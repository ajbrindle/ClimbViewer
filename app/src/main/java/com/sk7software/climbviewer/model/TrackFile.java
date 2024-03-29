package com.sk7software.climbviewer.model;

import android.graphics.PointF;
import android.util.Log;

import com.sk7software.climbviewer.LocationMonitor;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.geo.GeoConvert;
import com.sk7software.climbviewer.geo.Projection;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

@Root(name="gpx", strict=false)
@Getter
@Setter
public class TrackFile {
    // EMULATOR
    //private static final String FILE_DIR = "/data/data/com.sk7software.climbviewer/";
    // P20 Phone
    public static final String FILE_DIR = "/sdcard/Android/data/com.sk7software.climbviewer/";

    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String TIME_FORMAT = "HH:mm:ss";
    private static final String ROUTE_EXT = ".rte";
    private static final String TAG = TrackFile.class.getSimpleName();

    @Element
    private TrackMetadata metadata;

    @Element(name="trk")
    private Track route;

    public static TrackFile createFromStream(InputStream is) {
        Serializer serializer = new Persister();
        try {
            TrackFile track = serializer.read(TrackFile.class, is);
            track.setGridPoints();
            return track;
        } catch (Exception e) {
            Log.d(TAG, "Unable to read track: " + e.getMessage());
        }
        return null;
    }

    public static void addLocalFiles() {
        // Get all files in MAP_DIR with .mhr extension
        File directory = new File(FILE_DIR);
        Serializer serializer = new Persister();

        List<File> files = Arrays.asList(directory.listFiles()).stream()
                .filter(file -> file.getName().endsWith(ROUTE_EXT)).collect(Collectors.toList());

        for (File f : files) {
            // Load the file
            Log.d(TAG, "Found tracked route: " + f.getAbsolutePath());

            try {
                TrackFile track = serializer.read(TrackFile.class, f);
                track.setGridPoints();
            } catch (Exception e) {
                Log.d(TAG, "Unable to read track: " + e.getMessage());
            }
        }
    }

    public static void processTrackFile(TrackFile track, int multiplier) {
        if (track != null) {
            // Convert points to grid
            track.setGridPoints();

            // Find matched climbs
            List<GPXRoute> matchedClimbs = track.matchToClimbs();

            // Add attempt
            for (GPXRoute climb : matchedClimbs) {
                ClimbAttempt attempt = track.extractAttempt(climb, multiplier);
                Log.d(TAG, "Extracted attempt: " + attempt.getDatetime());
                Log.d(TAG, "Duration: " + attempt.getDuration());
                Log.d(TAG, "Number of points: " + attempt.getPoints().size());
                Log.d(TAG, "Attempt for climb: " + climb.getName() + " [" + climb.getId() + "]");
                Database.getInstance().addAttempt(attempt, climb.getId());
            }
        }
    }

    public void setGridPoints() {
        int zone = 0;

        if (!getRoute().getTrackSegment().getPoints().isEmpty()) {
            zone = GeoConvert.calcUTMZone(getRoute().getTrackSegment().getPoints().get(0).getLat(),
                    getRoute().getTrackSegment().getPoints().get(0).getLon());
        }
        for (RoutePoint pt : getRoute().getTrackSegment().getPoints()) {
            pt.setENFromLL(Database.getProjection(Projection.SYS_UTM_WGS84), zone);
        }
    }

    public List<GPXRoute> matchToClimbs() {
        return matchToClimbs(1, 0);
    }

    public List<GPXRoute> matchToClimbsAfterIndex(int index) {
        return matchToClimbs(1, index);
    }

    public List<GPXRoute> matchToClimbs(int multiplier, int minIndex) {
        List<GPXRoute> allClimbs = new ArrayList<>(Arrays.asList(Database.getInstance().getClimbs()));
        List<GPXRoute> startedClimbs = new ArrayList<>();

        PointF lastPoint = null;
        int routePointIndex = 0;

        // Find all climbs whose start point is on the route
        for (RoutePoint pt : getRoute().getTrackSegment().getPoints()) {
            if (lastPoint == null) {
                lastPoint = new PointF((float)pt.getEasting(), (float)pt.getNorthing());
                continue;
            }

            PointF currentPoint = new PointF((float)pt.getEasting(), (float)pt.getNorthing());

            for (GPXRoute climb : allClimbs) {
                if (routePointIndex >= minIndex && startFound(climb, lastPoint, currentPoint, routePointIndex, multiplier)) {
                    Log.d(TAG, "FOUND CLIMB START " + climb.getName());
                    GPXRoute startedClimb = Database.getInstance().getClimb(climb.getId());
                    startedClimb.setStartIdx(routePointIndex);
                    startedClimbs.add(startedClimb);
                }
            }

            // Remove any climbs already found from allClimbs
            allClimbs.removeIf(startedClimbs::contains);
            lastPoint = currentPoint;
            routePointIndex++;
        }

        List<GPXRoute> completedClimbs = new ArrayList<>();
        lastPoint = null;
        routePointIndex = 0;

        Log.d(TAG, "Matched climbs: " + startedClimbs.size());

        // From the started climbs, find the ones that finish
        for (RoutePoint pt : getRoute().getTrackSegment().getPoints()) {
            if (lastPoint == null) {
                lastPoint = new PointF((float)pt.getEasting(), (float)pt.getNorthing());
                continue;
            }

            PointF currentPoint = new PointF((float)pt.getEasting(), (float)pt.getNorthing());

            for (GPXRoute climb : startedClimbs) {
                if (routePointIndex < climb.getStartIdx()) {
                    // Start point of climb has not yet been reached
                    continue;
                }

                int lastIdx = climb.getPoints().size()-1;
                PointF end = new PointF((float)climb.getPoints().get(lastIdx).getEasting(), (float)climb.getPoints().get(lastIdx).getNorthing());
                if (LocationMonitor.pointWithinLineSegmentWithTolerance(end, lastPoint, currentPoint, multiplier)) {
                    Log.d(TAG, "FOUND CLIMB END " + climb.getName() + " (" + routePointIndex + ")");
                    completedClimbs.add(climb);
                }
            }

            startedClimbs.removeIf(completedClimbs::contains);
            lastPoint = currentPoint;
            routePointIndex++;
        }

        return completedClimbs;
    }

    public ClimbAttempt extractAttempt(GPXRoute climb, int multiplier) {
        ClimbAttempt attempt = new ClimbAttempt();
        attempt.setDatetime(convertToDate(metadata.getTime()));

        LocalDateTime startTime = null;
        boolean inProgress = false;

        PointF lastPoint = null;
        int segmentPointIndex = 0;

        // Find start point of the climb on the route
        for (RoutePoint pt : getRoute().getTrackSegment().getPoints()) {
            if (lastPoint == null) {
                lastPoint = new PointF((float)pt.getEasting(), (float)pt.getNorthing());
                continue;
            }

            PointF currentPoint = new PointF((float)pt.getEasting(), (float)pt.getNorthing());
            if (inProgress) {
                LocalDateTime pointTime = convertToDate(pt.getTime());
                attempt.addPoint(pt, pointTime);
                PointF end = new PointF((float)climb.getPoints().get(climb.getPoints().size()-1).getEasting(),
                        (float)climb.getPoints().get(climb.getPoints().size()-1).getNorthing());
                if (LocationMonitor.pointWithinLineSegmentWithTolerance(end, lastPoint, currentPoint, multiplier)) {
                    LocalDateTime endTime = convertToDate(pt.getTime());
                    long diff = endTime.atZone(ZoneId.systemDefault()).toEpochSecond() -
                                startTime.atZone(ZoneId.systemDefault()).toEpochSecond();
                    attempt.setDuration((int)diff);
                    Log.d(TAG, "END OF ATTEMPT: " + diff + "; " + climb.getPoints().get(climb.getPoints().size()-1).getLat() + "," + climb.getPoints().get(climb.getPoints().size()-1).getLon());
                    Log.d(TAG, "Points: " + lastPoint + "; " + currentPoint);
                    break;
                }
            } else {
                if (startFound(climb, lastPoint, currentPoint, segmentPointIndex, multiplier)) {
                    Log.d(TAG, "STARTED ATTEMPT " + climb.getName() + "; " + pt.getLat() + "," + pt.getLon());
                    startTime = convertToDate(pt.getTime());
                    attempt.addPoint(pt, startTime);
                    inProgress = true;
                }
            }
            lastPoint = currentPoint;
            segmentPointIndex++;
        }
        return attempt;
    }

    private boolean startFound(GPXRoute climb, PointF lastPoint, PointF currentPoint, int index, int multiplier) {
        PointF start = new PointF((float) climb.getPoints().get(0).getEasting(), (float) climb.getPoints().get(0).getNorthing());
        if (LocationMonitor.pointWithinLineSegmentWithTolerance(start, lastPoint, currentPoint, multiplier)) {
            Log.d(TAG, "Found point in segment: " + index + " for climb " + climb.getName());
            PointF second = new PointF((float) climb.getPoints().get(1).getEasting(), (float) climb.getPoints().get(1).getNorthing());
            DirectionChecker checker = new DirectionChecker(start, second);
            checker.setStartIndex(index);
            checker.calcSegmentDist(start, lastPoint, currentPoint);
            if (checker.check(second, getRoute().getTrackSegment().getPoints(), multiplier)) {
                return true;
            }
        }
        return false;
    }

    public static LocalDateTime convertToDate(String timeStr) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");
        return LocalDateTime.parse(timeStr, formatter);
    }


    // Creates a dummy file so that climbs on a specified route can be found
    // using the same method as climbs are found in a route file
    public static List<GPXRoute> findClimbsOnTrackFromPoints(GPXRoute route) {
        TrackFile dummyFile = new TrackFile();
        Track track = new Track();
        TrackSegment segment = new TrackSegment();
        segment.setPoints(route.getPoints());
        track.setTrackSegment(segment);
        dummyFile.setRoute(track);

        List<GPXRoute> climbsOnRoute = dummyFile.matchToClimbs();

        for (GPXRoute r : climbsOnRoute) {
            Log.d(TAG, "Climbs on route: " + r.getName());
        }

        return climbsOnRoute;
    }
}
