package com.sk7software.climbviewer;

import android.graphics.Color;
import android.graphics.PointF;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.model.AttemptPoint;
import com.sk7software.climbviewer.model.AttemptStats;
import com.sk7software.climbviewer.model.ClimbAttempt;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.view.AttemptData;
import com.sk7software.climbviewer.view.PlotPoint;
import com.sk7software.util.aspectlogger.DebugTrace;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClimbController {
    private static ClimbController INSTANCE = null;
    private static final String TAG = ClimbController.class.getSimpleName();

    // Climb
    private GPXRoute climb;
    private int lastClimbId;

    // Location
    private PointF lastPoint = null;
    private LatLng lastPointLL = null;

    // Route
    private GPXRoute route;
    private int lastRouteId;
    private boolean routeInProgress;
    private int routeIdx;

    // Attempt data for items that are currently in progress
    Map<PointType, AttemptData> attempts;
    private boolean attemptInProgress;
    private boolean inLastSegment;
    private int offClimbCount;
    private int offRouteCount;

    // PB Attempt
    private float timeDiffToPB;
    private float distToPB;

    public enum PointType {
        ATTEMPT(Color.CYAN),
        PB(Color.GREEN),
        ROUTE(Color.CYAN);

        int color;
        private PointType(int c) {
            this.color = c;
        }
        public int getColor() {
            return color;
        }
    }

    private ClimbController() {
        super();
        this.attempts = new HashMap<>();
    }

    public static ClimbController getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ClimbController();
        }
        return INSTANCE;
    }

    private void reset(PointType type) {
        if (type == PointType.ATTEMPT) {
            attempts.remove(PointType.ATTEMPT);
            attempts.remove(PointType.PB);
            attemptInProgress = false;
            climb = null;
            offClimbCount = 0;
            PositionMonitor.getInstance().setOnClimbId(-1);
        } else if (type == PointType.ROUTE) {
            attempts.remove(PointType.ROUTE);
            routeInProgress = false;
        }
    }

    public void startAttempt() {
        AttemptData attempt = AttemptData.builder()
                                .attempt(new ClimbAttempt())
                                .minIndex(0)
                                .type(PointType.ATTEMPT)
                                .build();
        attempt.getAttempt().setDatetime(LocalDateTime.now());
        attempts.put(PointType.ATTEMPT, attempt);
        attemptInProgress = true;
        inLastSegment = false;
        offClimbCount = 0;
    }

    public void startRoute() {
        AttemptData routeAttempt = AttemptData.builder()
                .attempt(new ClimbAttempt())
                .minIndex(0)
                .type(PointType.ROUTE)
                .build();
        attempts.put(PointType.ROUTE, routeAttempt);
        routeInProgress = true;
    }

    public void loadPB() {
        if (climb != null) {
            ClimbAttempt pbAttempt = Database.getInstance().getClimbTime(climb.getId(), true);
            if (pbAttempt != null && pbAttempt.getPoints() != null && !pbAttempt.getPoints().isEmpty()) {
                LocalDateTime pbStart = pbAttempt.getPoints().get(0).getTimestamp();
                pbAttempt.getPoints().stream()
                        .forEach(p -> p.setSecondsFromStart(ChronoUnit.SECONDS.between(pbStart, p.getTimestamp())));
                AttemptData pb = AttemptData.builder()
                        .attempt(pbAttempt)
                        .minIndex(0)
                        .type(PointType.PB)
                        .build();
                attempts.put(PointType.PB, pb);
            }
        }
    }

    @DebugTrace
    public void updateClimbData(RoutePoint point, ActivityUpdateInterface callbackActivity) {
        Log.d(TAG, "Updating climb data " + point.getEasting() + "," + point.getNorthing() + " " + callbackActivity.getClass().getSimpleName());
        lastPointLL = new LatLng(point.getLat(), point.getLon());

        if (lastPoint == null) {
            lastPoint = new PointF((float)point.getEasting(), (float)point.getNorthing());
            return;
        }

        PointF currentPoint = new PointF((float)point.getEasting(), (float)point.getNorthing());

        if (attemptInProgress) {
            Log.d(TAG, "Calculating attempt");
            LocalDateTime now = LocalDateTime.now();
            updateAttempt(point, now);

            // Update PB info if this is being tracked
            if (attempts.containsKey(PointType.PB)) {
                updatePB(point, now);
            }

            // Check if the climb has finished
            checkIfFinished(currentPoint, point);
        }

        if (routeInProgress) {
            // Work out distances and times at this location
            Log.d(TAG, "Calculating route distance");
            AttemptData routeData = attempts.get(PointType.ROUTE);
            routeData.calcDist(point, route);
            routeData.setBearing(calcBearing(route, routeData));

            if (!stillOnTrack(route, currentPoint, routeData.getMinIndex(), point.getAccuracy())) {
                if (++offRouteCount > 3) {
                    // Deviated off route, so go back into monitoring mode
                    routeInProgress = false;
                    Log.d(TAG, "Dropped off route");
                }
            } else {
                routeIdx = routeData.getMinIndex();
                offRouteCount = 0;
            }
        }

        lastPoint = currentPoint;
    }

    public void rejoinRoute(int idx) {
        routeInProgress = true;
        AttemptData routeData = attempts.get(PointType.ROUTE);
        routeData.setMinIndex(idx);
    }

    private void updateAttempt(RoutePoint point, LocalDateTime now) {
        AttemptData attemptData = attempts.get(PointType.ATTEMPT);
        ClimbAttempt attempt = attemptData.getAttempt();
        attempt.addPoint(point, now);

        // Work out distances and times at this location
        attemptData.calcDist(point, climb);
        attemptData.setBearing(calcBearing(climb, attemptData));
    }

    private void updatePB(RoutePoint point, LocalDateTime now) {
        AttemptData pb = attempts.get(PointType.PB);
        RoutePoint pbPoint = calcPbLocation(now);
        pb.setPosition(pbPoint);
        pb.calcDist(pbPoint, climb);

        timeDiffToPB = calcTimeDiff(point, now);
        distToPB = attempts.get(PointType.ATTEMPT).getDist() - pb.getDist();
    }

    @DebugTrace
    private void checkIfFinished(PointF currentPoint, RoutePoint point) {
        AttemptData attemptData = attempts.get(PointType.ATTEMPT);
        ClimbAttempt attempt = attemptData.getAttempt();

        int endIndex = climb.getPoints().size() - 1;
        PointF end = new PointF((float) climb.getPoints().get(endIndex).getEasting(), (float) climb.getPoints().get(endIndex).getNorthing());

        if (LocationMonitor.pointWithinLineSegment(end, lastPoint, currentPoint)) {
            // Entered last segment
            Log.d(TAG, "In last segment");
            inLastSegment = true;
        } else if (inLastSegment) {
            // Left last segment so climb has finished
            Log.d(TAG, "Climb finished");
            attemptInProgress = false;
            PositionMonitor.getInstance().setOnClimbId(-1);

            // Calculate duration
            long startTime = attempt.getPoints().get(0).getTimestamp().atZone(ZoneId.systemDefault()).toEpochSecond();
            long endTime = attempt.getPoints().get(attempt.getPoints().size()-1).getTimestamp().atZone(ZoneId.systemDefault()).toEpochSecond();
            Log.d(TAG, "Duration: " + startTime + " to " + endTime);
            attempt.setDuration((int)(endTime - startTime));

            // Add to database then reset
            Database.getInstance().addAttempt(attempt, climb.getId());
            reset(PointType.ATTEMPT);
        } else if (!stillOnTrack(climb, currentPoint, 0, point.getAccuracy())) {
            if (++offClimbCount > 10) {
                // Deviated off climb, so reset
                lastClimbId = -99; // Prevents summary panel on screen
                reset(PointType.ATTEMPT);
            }
        } else {
            offClimbCount = 0;
        }

    }
    private float calcBearing(GPXRoute track, AttemptData attemptData) {
        int index = attemptData.getMinIndex(); //minIndex.get(type);
        RoutePoint p1 = track.getPoints().get(index);
        RoutePoint p2 = track.getPoints().get(index+1);
        float bearing = (float)Math.atan2(p2.getEasting() - p1.getEasting(), p2.getNorthing() - p1.getNorthing());
        bearing *= 180/Math.PI;
        return bearing;
    }

    // Determines point on the PB track based on the time since started
    // If an exact time match is found, it returns that point
    // Else it interpolates within the times either side
    private RoutePoint calcPbLocation(LocalDateTime now) {
        ClimbAttempt attempt = attempts.get(PointType.ATTEMPT).getAttempt();
        long secondsSinceStart = ChronoUnit.SECONDS.between(attempt.getDatetime(), now);

        // Find point on pb that is no more than this many seconds from the start
        ClimbAttempt pbAttempt = attempts.get(PointType.PB).getAttempt();
        AttemptPoint pt = pbAttempt.getPoints().stream()
                .filter(p -> p.getSecondsFromStart() <= secondsSinceStart)
                .sorted((a,b) -> Long.compare(b.getSecondsFromStart(), a.getSecondsFromStart()))
                .findFirst()
                .orElse(null);
        if (pt != null) {
            // Found earliest point
            if (pt.getSecondsFromStart() == secondsSinceStart) {
                // Exact match
                return pt.getPoint();
            } else {
                // Get next point
                AttemptPoint nextPt = pbAttempt.getPoints().stream()
                        .filter(p -> p.getSecondsFromStart() > secondsSinceStart)
                        .findFirst()
                        .orElse(null);

                if (nextPt != null) {
                    // Interpolate between the points
                    return interpolatePosition(nextPt, pt, secondsSinceStart);
                } else {
                    return pt.getPoint();
                }
            }
        }
        return null;
    }

    private static RoutePoint interpolatePosition(AttemptPoint p1, AttemptPoint p2, long secondsSinceStart) {
        RoutePoint routePoint = new RoutePoint();

        long timeDiff = p2.getSecondsFromStart() - p1.getSecondsFromStart();
        double weighting = (double)(secondsSinceStart - p1.getSecondsFromStart()) / (double)timeDiff;

        routePoint.setEasting(p1.getPoint().getEasting() + (weighting * (p2.getPoint().getEasting() - p1.getPoint().getEasting())));
        routePoint.setNorthing(p1.getPoint().getNorthing() + (weighting * (p2.getPoint().getNorthing() - p1.getPoint().getNorthing())));

        // Interpolation will be near enough for lat/long given the small increments involved
        routePoint.setLat(p1.getPoint().getLat() + (weighting * (p2.getPoint().getLat() - p1.getPoint().getLat())));
        routePoint.setLon(p1.getPoint().getLon() + (weighting * (p2.getPoint().getLon() - p1.getPoint().getLon())));
        return routePoint;
    }



    // Calculates the time difference between the current location and the PB
    // Could be positive if in front of PB
    private float calcTimeDiff(RoutePoint attemptPoint, LocalDateTime now) {
        ClimbAttempt attempt = attempts.get(PointType.ATTEMPT).getAttempt();
        ClimbAttempt pbAttempt = attempts.get(PointType.PB).getAttempt();

        // Determine where this point is on the PB
        RoutePoint lastRoutePoint = null;
        PointF attPt = new PointF((float)attemptPoint.getEasting(), (float)attemptPoint.getNorthing());
        float secondsToPoint = 0;
        int lastIndex = 0;

        for (AttemptPoint pbPoint : pbAttempt.getPoints()) {
            if (lastRoutePoint == null) {
                lastRoutePoint = pbPoint.getPoint();
                continue;
            }

            RoutePoint pt = pbPoint.getPoint();

            PointF lastPbPointPt = new PointF((float)lastRoutePoint.getEasting(), (float)lastRoutePoint.getNorthing());
            PointF pbPointPt = new PointF((float)pbPoint.getPoint().getEasting(), (float)pbPoint.getPoint().getNorthing());

            // Determine if location is between this one and last one on the PB track
            if (LocationMonitor.pointWithinLineSegment(attPt, lastPbPointPt, pbPointPt)) {
                // Get the closest point on the PB line segment
                PointF nearestPt = LocationMonitor.getXXYY(attPt, lastPbPointPt, pbPointPt);

                // Set the point then work out how far along the PB line segment it is
                RoutePoint routePt = new RoutePoint();
                routePt.setEasting(nearestPt.x);
                routePt.setNorthing(nearestPt.y);
                float distAlong = (float)AttemptData.calcDelta(routePt, pbAttempt.getPoints().get(lastIndex).getPoint());
                float distBetween = (float)Math.sqrt(Math.pow(pbPoint.getPoint().getEasting() - pbAttempt.getPoints().get(lastIndex).getPoint().getEasting(), 2.0) +
                        Math.pow(pbPoint.getPoint().getNorthing() - pbAttempt.getPoints().get(lastIndex).getPoint().getNorthing(), 2.0));

                // Interpolate to get PB time at the same point
                float weighting = distAlong / distBetween;
                secondsToPoint = weighting * (pbPoint.getSecondsFromStart() - pbAttempt.getPoints().get(lastIndex).getSecondsFromStart());
                secondsToPoint += pbAttempt.getPoints().get(lastIndex).getSecondsFromStart();
                return secondsToPoint - (ChronoUnit.SECONDS.between(attempt.getDatetime(), now));
            }

            lastIndex++;
            lastRoutePoint = pt;
        }
        return Float.MIN_VALUE;
    }

    public void loadClimb(GPXRoute climb) {
        this.climb = climb;
        this.lastClimbId = climb.getId();
        setPointsDist(this.climb);
        this.climb.calcSmoothedPoints();
    }

    public void loadRoute(GPXRoute route) {
        this.route = route;
        this.lastRouteId = route.getId();
        setPointsDist(this.route);
    }

    public void clearRoute() {
        this.route = null;
        this.lastRouteId = -1;
    }

    private void setPointsDist(GPXRoute gpxRoute) {
        float dist = 0;
        float elev = 0;

        gpxRoute.getPoints().get(0).setDistFromStart(0);
        gpxRoute.getPoints().get(0).setElevFromStart(0);

        for (int i=1; i<gpxRoute.getPoints().size(); i++) {
            dist += Math.sqrt(Math.pow(gpxRoute.getPoints().get(i).getEasting() - gpxRoute.getPoints().get(i - 1).getEasting(), 2.0) +
                    Math.pow(gpxRoute.getPoints().get(i).getNorthing() - gpxRoute.getPoints().get(i - 1).getNorthing(), 2.0));
            gpxRoute.getPoints().get(i).setDistFromStart(dist);
        }

        // Calculate elevations
        for (int i=1; i<gpxRoute.getPoints().size(); i++) {
            if (gpxRoute.getPoints().get(i).getElevation() > gpxRoute.getPoints().get(i-1).getElevation()) {
                elev += gpxRoute.getPoints().get(i).getElevation() - gpxRoute.getPoints().get(i-1).getElevation();
            }
            gpxRoute.getPoints().get(i).setElevFromStart(elev);
        }
    }

    public List<PlotPoint> getClimbPoints(GPXRoute profile, int startIdx, double targetDist, boolean smooth) {
        float distFromLast = 0;
        float totalDist = 0;
        boolean first = true;
        float lastX = 0;
        double lastGradient = 0;
        int lastIndex = 0;
        List<PlotPoint> points = new ArrayList<>();

        int smoothDist = profile.getSmoothDist();
        if (smoothDist == 0) {
            smoothDist = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_SMOOTH_DIST, 20);
        }

        // Determine cumulative delta to all points
        for (int i = startIdx; i < profile.getPoints().size(); i++) {
            RoutePoint pt = profile.getPoints().get(i);
            PlotPoint p = new PlotPoint();
            p.setLocation(new LatLng(pt.getLat(), pt.getLon()));

            if (first) {
                first = false;
                p.setX(profile.getPoints().get(i).getDistFromStart());
                p.setElevation((float) pt.getElevation());
                lastIndex = startIdx;
            } else {
                distFromLast += (float) calcDelta(pt, profile.getPoints().get(lastIndex).getEasting(), profile.getPoints().get(lastIndex).getNorthing());
                lastIndex++;

                if (smooth && distFromLast < smoothDist) continue;

                p.setX(distFromLast + lastX);
                p.setElevation((float) pt.getElevation());
                distFromLast = 0;
            }

            points.add(p);
            lastX = p.getX();
            totalDist = p.getX();

            if (totalDist - points.get(0).getX() >= targetDist) {
                break;
            }
        }

        return points;
    }

    private double calcDelta(RoutePoint pt, double e, double n) {
        return Math.sqrt(Math.pow(e - pt.getEasting(), 2.0) + Math.pow(n - pt.getNorthing(), 2.0));
    }

    @DebugTrace
    private boolean stillOnTrack(GPXRoute track, PointF point, int minIndex, float accuracy) {
        PointF lastP = null;
        int maxIndex = track.getPoints().size();

        if (minIndex > 0) {
            maxIndex = minIndex + 10;
            if (maxIndex > track.getPoints().size()) {
                maxIndex = track.getPoints().size();
            }
        }

        for (int i=minIndex; i<maxIndex; i++) {
            RoutePoint pt = track.getPoints().get(i);
            PointF p = new PointF((float) pt.getEasting(), (float) pt.getNorthing());
            if (lastP == null) {
                lastP = p;
            } else {
                if (LocationMonitor.pointWithinLineSegment(point, lastP, p)) {
                    return true;
                }
                lastP = p;
            }
        }
        return false;
    }

    public AttemptStats getLastAttemptStats(int lastClimbId) {
        AttemptStats stats = Database.getInstance().getLastAttempt(lastClimbId);
        if (stats != null) {
            GPXRoute lastClimb = Database.getInstance().getClimb(lastClimbId);
            setPointsDist(lastClimb);
            int numClimbPoints = lastClimb.getPoints().size();
            stats.setDistanceM(lastClimb.getPoints().get(numClimbPoints - 1).getDistFromStart());
        }
        return stats;
    }
}
