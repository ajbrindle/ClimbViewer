package com.sk7software.climbviewer;

import android.graphics.PointF;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.geo.GeoConvert;
import com.sk7software.climbviewer.geo.Projection;
import com.sk7software.climbviewer.model.AttemptPoint;
import com.sk7software.climbviewer.model.AttemptStats;
import com.sk7software.climbviewer.model.ClimbAttempt;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.view.DisplayFormatter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClimbController {
    private static ClimbController INSTANCE = null;
    private static final String TAG = ClimbController.class.getSimpleName();
    public static final int DISPLAY_INTERVAL = 20000;

    private GPXRoute climb;
    private Map<PointType, Integer> minIndex;
    private int lastClimbId;

    // Current attempt
    private ClimbAttempt attempt;
    private boolean attemptInProgress;
    private PointF lastPoint = null;
    private float attemptDist;
    private boolean inLastSegment;
    private float bearing;
    private Map<PointType, RoutePoint> snappedPosition;
    private int offClimbCount;

    // Gradients
    private float currentGradient;
    private float segmentToGo;
    private float nextGradient;

    // PB Attempt
    private ClimbAttempt pbAttempt;
    private boolean plotPB;
    private RoutePoint pbPoint;
    private float pbDist;
    private float timeDiffToPB;
    private float distToPB;

    public enum PointType {
        ATTEMPT,
        PB;
    }

    private ClimbController() {
        super();
    }

    public static ClimbController getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ClimbController();
        }
        return INSTANCE;
    }

    public void reset() {
        attempt = null;
        attemptInProgress = false;
        lastPoint = null;
        climb = null;
        plotPB = false;
        offClimbCount = 0;
    }

    public void startAttempt() {
        attempt = new ClimbAttempt();
        attempt.setDatetime(LocalDateTime.now());
        minIndex = new HashMap<>();
        minIndex.put(PointType.ATTEMPT, 0);
        minIndex.put(PointType.PB, 0);
        attemptInProgress = true;
        inLastSegment = false;
        snappedPosition = new HashMap<>();
        offClimbCount = 0;
    }

    public void loadPB() {
        if (climb != null) {
            pbAttempt = Database.getInstance().getClimbPB(climb.getId());
            if (pbAttempt != null && pbAttempt.getPoints() != null && pbAttempt.getPoints().size() > 0) {
                LocalDateTime pbStart = pbAttempt.getPoints().get(0).getTimestamp();
                pbAttempt.getPoints().stream()
                        .forEach(p -> p.setSecondsFromStart(ChronoUnit.SECONDS.between(pbStart, p.getTimestamp())));
                plotPB = true;
            }
        }
    }

    public void updateClimbData(RoutePoint point, ActivityUpdateInterface callbackActivity) {
        if (lastPoint == null) {
            lastPoint = new PointF((float)point.getEasting(), (float)point.getNorthing());
            return;
        }

        PointF currentPoint = new PointF((float)point.getEasting(), (float)point.getNorthing());

        if (attemptInProgress) {
            LocalDateTime now = LocalDateTime.now();
            attempt.addPoint(point, now);

            int endIndex = climb.getPoints().size() - 1;

            // Work out distances and times at this location
            attemptDist = calcDist(point, PointType.ATTEMPT);
            calcBearing();

            if (plotPB) {
                pbPoint = calcPbLocation(now);
                pbDist = calcDist(pbPoint, PointType.PB);
                timeDiffToPB = calcTimeDiff(point, now);
                distToPB = attemptDist - pbDist;
            }

            // Check if the climb has finished
            PointF end = new PointF((float) climb.getPoints().get(endIndex).getEasting(), (float) climb.getPoints().get(endIndex).getNorthing());

            if (LocationMonitor.pointWithinLineSegment(end, lastPoint, currentPoint)) {
                // Entered last segment
                Log.d(TAG, "In last segment");
                inLastSegment = true;
            } else if (inLastSegment) {
                // Left last segment so climb has finished
                Log.d(TAG, "Climb finished");
                attemptInProgress = false;

                // Calculate duration
                long startTime = attempt.getPoints().get(0).getTimestamp().atZone(ZoneId.systemDefault()).toEpochSecond();
                long endTime = attempt.getPoints().get(attempt.getPoints().size()-1).getTimestamp().atZone(ZoneId.systemDefault()).toEpochSecond();
                Log.d(TAG, "Duration: " + startTime + " to " + endTime);
                attempt.setDuration((int)(endTime - startTime));

                // Add to database then reset
                Database.getInstance().addAttempt(attempt, climb.getId());
                reset();
            } else if (!stillOnClimb(currentPoint)) {
                if (++offClimbCount > 5) {
                    // Deviated off climb, so reset
                    lastClimbId = -99; // Prevents summary panel on screen
                    reset();
                }
            } else {
                offClimbCount = 0;
            }
        }

        lastPoint = currentPoint;
    }

    private synchronized float calcDist(RoutePoint loc, PointType type) {
        PointF locPt = new PointF((float)loc.getEasting(), (float)loc.getNorthing());
        Projection proj = Database.getProjection(climb.getProjectionId());
        int zone = climb.getZone();

        int startIndex = minIndex.get(type);
        int lastIndex = startIndex;

        // Get previous distance from start
        float dist = lastIndex < climb.getPoints().size()-1 ?
                climb.getPoints().get(lastIndex+1).getDistFromStart() :
                climb.getPoints().get(climb.getPoints().size()-1).getDistFromStart();

        // Look for point on climb that matches with current location
        for (int i=startIndex+1; i<climb.getPoints().size(); i++) {
            RoutePoint pt = climb.getPoints().get(i);

            PointF lastPointPt = new PointF((float)climb.getPoints().get(i-1).getEasting(), (float)climb.getPoints().get(i-1).getNorthing()); //float)lastPoint.getEasting(), (float)lastPoint.getNorthing());
            PointF currentPointPt = new PointF((float)climb.getPoints().get(i).getEasting(), (float)climb.getPoints().get(i).getNorthing()); //(float)pt.getEasting(), (float)pt.getNorthing());

            // Determine if location is between this one and last one
            if (LocationMonitor.pointWithinLineSegment(locPt, lastPointPt, currentPointPt)) {
                // Find the point on the route
                PointF nearestPt = LocationMonitor.getXXYY(locPt, lastPointPt, currentPointPt);
                RoutePoint routePt = new RoutePoint();
                routePt.setEasting(nearestPt.x);
                routePt.setNorthing(nearestPt.y);
                LatLng ll = GeoConvert.convertGridToLL(proj, routePt, zone);
                routePt.setLat(ll.latitude);
                routePt.setLon(ll.longitude);
                snappedPosition.put(type, routePt);

                // Calculate how far along this line segment the current point is
                dist = (float) calcDelta(routePt, climb.getPoints().get(i-1));
                dist+= climb.getPoints().get(i-1).getDistFromStart();
                startIndex = i-1;

                // Calculate gradients
                if (type == PointType.ATTEMPT) {
                    currentGradient = calcGradient(climb.getPoints().get(i-1), climb.getPoints().get(i));//lastPoint, pt);

                    if (i < climb.getPoints().size() - 1) {
                        nextGradient = calcGradient(pt, climb.getPoints().get(i + 1));
                    } else {
                        nextGradient = 0;
                    }
                    segmentToGo = pt.getDistFromStart() - dist;
                }

                break;
            }
        }
        Log.d(TAG, type.name() + " distance so far: " + dist + " [" + startIndex + "]");
        minIndex.put(type, startIndex);
        return dist;
    }

    private void calcBearing() {
        int index = minIndex.get(PointType.ATTEMPT);
        RoutePoint p1 = climb.getPoints().get(index);
        RoutePoint p2 = climb.getPoints().get(index+1);
        bearing = (float)Math.atan2(p2.getEasting() - p1.getEasting(), p2.getNorthing() - p1.getNorthing());
        bearing *= 180/Math.PI;
    }

    // Determines point on the PB track based on the time since started
    // If an exact time match is found, it returns that point
    // Else it interpolates within the times either side
    private RoutePoint calcPbLocation(LocalDateTime now) {
        long secondsSinceStart = ChronoUnit.SECONDS.between(attempt.getDatetime(), now);

        // Find point on pb that is no more than this many seconds from the start
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

    private RoutePoint interpolatePosition(AttemptPoint p1, AttemptPoint p2, long secondsSinceStart) {
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

    private double calcDelta(RoutePoint point, RoutePoint referencePoint) {
        return Math.sqrt(Math.pow(referencePoint.getEasting() - point.getEasting(), 2.0) +
                        Math.pow(referencePoint.getNorthing() - point.getNorthing(), 2.0));
    }

    private float calcGradient(RoutePoint p1, RoutePoint p2) {
        double length = Math.sqrt(Math.pow(p2.getEasting() - p1.getEasting(), 2.0) +
                Math.pow(p2.getNorthing() - p1.getNorthing(), 2.0));
        double elevDiff = p2.getElevation() - p1.getElevation();
        return (float)(100.0 * elevDiff / length);
    }

    // Calculates the time difference between the current location and the PB
    // Could be positive if in front of PB
    private float calcTimeDiff(RoutePoint attemptPoint, LocalDateTime now) {
        // Determine where this point is on the PB
        RoutePoint lastPoint = null;
        PointF attPt = new PointF((float)attemptPoint.getEasting(), (float)attemptPoint.getNorthing());
        float secondsToPoint = 0;
        int lastIndex = 0;

        for (AttemptPoint pbPoint : pbAttempt.getPoints()) {
            if (lastPoint == null) {
                lastPoint = pbPoint.getPoint();
                continue;
            }

            RoutePoint pt = pbPoint.getPoint();

            PointF lastPbPointPt = new PointF((float)lastPoint.getEasting(), (float)lastPoint.getNorthing());
            PointF pbPointPt = new PointF((float)pbPoint.getPoint().getEasting(), (float)pbPoint.getPoint().getNorthing());

            // Determine if location is between this one and last one on the PB track
            if (LocationMonitor.pointWithinLineSegment(attPt, lastPbPointPt, pbPointPt)) {
                // Get the closest point on the PB line segment
                PointF nearestPt = LocationMonitor.getXXYY(attPt, lastPbPointPt, pbPointPt);

                // Set the point then work out how far along the PB line segment it is
                RoutePoint routePt = new RoutePoint();
                routePt.setEasting(nearestPt.x);
                routePt.setNorthing(nearestPt.y);
                float distAlong = (float)calcDelta(routePt, pbAttempt.getPoints().get(lastIndex).getPoint());
                float distBetween = (float)Math.sqrt(Math.pow(pbPoint.getPoint().getEasting() - pbAttempt.getPoints().get(lastIndex).getPoint().getEasting(), 2.0) +
                        Math.pow(pbPoint.getPoint().getNorthing() - pbAttempt.getPoints().get(lastIndex).getPoint().getNorthing(), 2.0));

                // Interpolate to get PB time at the same point
                float weighting = distAlong / distBetween;
                secondsToPoint = weighting * (pbPoint.getSecondsFromStart() - pbAttempt.getPoints().get(lastIndex).getSecondsFromStart());
                secondsToPoint += pbAttempt.getPoints().get(lastIndex).getSecondsFromStart();
                return secondsToPoint - (ChronoUnit.SECONDS.between(attempt.getDatetime(), now));
            }

            lastIndex++;
            lastPoint = pt;
        }
        return Float.MIN_VALUE;
    }

    public void loadClimb(GPXRoute climb) {
        this.climb = climb;
        this.lastClimbId = climb.getId();
        setPointsDist(this.climb);
    }

    private void setPointsDist(GPXRoute climb) {
        float dist = 0;
        climb.getPoints().get(0).setDistFromStart(0);

        for (int i=1; i<climb.getPoints().size(); i++) {
            dist += Math.sqrt(Math.pow(climb.getPoints().get(i).getEasting() - climb.getPoints().get(i-1).getEasting(), 2.0) +
                    Math.pow(climb.getPoints().get(i).getNorthing() - climb.getPoints().get(i-1).getNorthing(), 2.0));
            climb.getPoints().get(i).setDistFromStart(dist);
        }
    }

    private boolean stillOnClimb(PointF point) {
        PointF lastP = null;
        for (RoutePoint pt : climb.getPoints()) {
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
        GPXRoute lastClimb = Database.getInstance().getClimb(lastClimbId);
        setPointsDist(lastClimb);
        int numClimbPoints = lastClimb.getPoints().size();
        stats.setDistanceM(lastClimb.getPoints().get(numClimbPoints-1).getDistFromStart());
        return stats;
    }
}
