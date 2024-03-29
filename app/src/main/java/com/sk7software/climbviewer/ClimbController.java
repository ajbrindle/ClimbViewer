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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
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
    private List<PointF> prevPoints;
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
    private boolean forceClose;
    private boolean nearEnd;

    // PB Attempt
    private float timeDiffToPB;
    private float distToPB;
    private boolean pbFinished;

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

    public void reset(PointType type) {
        if (type == PointType.ATTEMPT) {
            attempts.remove(PointType.ATTEMPT);
            attempts.remove(PointType.PB);
            pbFinished = false;
            attemptInProgress = false;
            climb = null;
            offClimbCount = 0;
            PositionMonitor.getInstance().setOnClimbId(-1);
            Preferences.getInstance().clearIntPreference(Preferences.PREFERENCES_ON_CLIMB_ID);
        } else if (type == PointType.ROUTE) {
            attempts.remove(PointType.ROUTE);
            routeInProgress = false;
        }
    }

    public void startAttempt(int minIndex) {
        AttemptData attempt = AttemptData.builder()
                                .attempt(new ClimbAttempt())
                                .minIndex(minIndex)
                                .type(PointType.ATTEMPT)
                                .build();

        if (minIndex == 0) {
            attempt.getAttempt().setDatetime(LocalDateTime.now());
            Preferences.getInstance().addPreference(Preferences.PREFERENCES_CLIMB_START_TIME, System.currentTimeMillis());
        } else {
            attempt.getAttempt().setDatetime(LocalDateTime.ofInstant(Instant.ofEpochMilli(Preferences.getInstance().getLongPreference(Preferences.PREFERENCES_CLIMB_START_TIME, 0)), ZoneId.systemDefault()));
        }
        attempts.put(PointType.ATTEMPT, attempt);
        attemptInProgress = true;
        inLastSegment = false;
        forceClose = false;
        nearEnd = false;
        offClimbCount = 0;
        prevPoints.clear();
        lastPoint = null;
        Preferences.getInstance().addPreference(Preferences.PREFERENCES_ON_CLIMB_ID, climb.getId());
    }

    public void startRoute() {
        AttemptData routeAttempt = AttemptData.builder()
                .attempt(new ClimbAttempt())
                .minIndex(0)
                .type(PointType.ROUTE)
                .build();
        attempts.put(PointType.ROUTE, routeAttempt);
        prevPoints.clear();
        lastPoint = null;
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
                pbFinished = false;
            }
        }
    }

    @DebugTrace
    public void updateClimbData(RoutePoint point, ActivityUpdateInterface callbackActivity) {
        Log.d(TAG, "Updating climb data " + point.getEasting() + "," + point.getNorthing() + " " + callbackActivity.getClass().getSimpleName());
        lastPointLL = new LatLng(point.getLat(), point.getLon());

        if (lastPoint == null) {
            setPrevPoint(new PointF((float)point.getEasting(), (float)point.getNorthing()));
            return;
        }

        PointF currentPoint = new PointF((float)point.getEasting(), (float)point.getNorthing());

        if (attemptInProgress) {
            updateClimbAttempt(point, currentPoint);
        }
        if (routeInProgress) {
            updateRouteAttempt(point);
        }
        setPrevPoint(currentPoint);
    }

    private void updateClimbAttempt(RoutePoint point, PointF currentPoint) {
        Log.d(TAG, "Calculating attempt");
        LocalDateTime now = LocalDateTime.now();
        float distDone = updateAttempt(point, now);

        // Update PB info if this is being tracked
        if (attempts.containsKey(PointType.PB)) {
            updatePB(point, now);
        }

        if (distDone >= 0) {
            // Reset off-climb counter
            offClimbCount = 0;
            forceClose = false;
            calcNearEnd(distDone);
        } else {
            int minOffCount = Preferences.getInstance().getBooleanPreference(Preferences.PREFERENCES_CLIMB_ULTRA_TOLERANCE) ? 1000 : 15;
            // Update "off climb" counter
            if (++offClimbCount > minOffCount) {
                // Deviated off climb, so reset
                lastClimbId = -99; // Prevents summary panel on screen
                reset(PointType.ATTEMPT);
            } else if (offClimbCount > 3) {
                // Allow force close panel to show
                forceClose = true;
            }
        }
        checkIfFinished(currentPoint, distDone);
    }

    private void updateRouteAttempt(RoutePoint point) {
        // Work out distances and times at this location
        Log.d(TAG, "Calculating route distance");
        AttemptData routeData = attempts.get(PointType.ROUTE);
        float distDone = routeData.calcDist(point, route);
        routeData.setBearing(calcBearing(route, routeData));

        if (distDone >= 0) {
            routeIdx = routeData.getMinIndex();
            offRouteCount = 0;
        } else {
            if (++offRouteCount > 3) {
                // Deviated off route, so go back into monitoring mode
                routeInProgress = false;
                Log.d(TAG, "Dropped off route");
            }
        }
    }

    public void rejoinRoute(int idx) {
        routeInProgress = true;
        AttemptData routeData = attempts.get(PointType.ROUTE);
        routeData.setMinIndex(idx);
    }

    private float updateAttempt(RoutePoint point, LocalDateTime now) {
        AttemptData attemptData = attempts.get(PointType.ATTEMPT);
        ClimbAttempt attempt = attemptData.getAttempt();
        attempt.addPoint(point, now);

        // Work out distances and times at this location
        float distDone = attemptData.calcDist(point, climb);
        attemptData.setBearing(calcBearing(climb, attemptData));
        return distDone;
    }

    private void updatePB(RoutePoint point, LocalDateTime now) {
        AttemptData pb = attempts.get(PointType.PB);
        RoutePoint pbPoint = calcPbLocation(now);
        pb.setPosition(pbPoint);
        pb.calcDist(pbPoint, climb);

        timeDiffToPB = calcTimeDiff(point, now);
        distToPB = attempts.get(PointType.ATTEMPT).getDist() - pb.getDist();
    }

    private void calcNearEnd(float distDone) {
        int endIndex = climb.getPoints().size() - 1;

        // Near end if within 100m of the end
        if (climb.getPoints().get(endIndex).getDistFromStart() - distDone < 100 && distDone > 0) {
            nearEnd = true;
        }
    }

    @DebugTrace
    private void checkIfFinished(PointF currentPoint, float distDone) {
        Log.d(TAG, "Check finished: " + distDone);
        if (!nearEnd) {
            return;
        }

        AttemptData attemptData = attempts.get(PointType.ATTEMPT);
        ClimbAttempt attempt = attemptData.getAttempt();

        int endIndex = climb.getPoints().size() - 1;

        PointF end = new PointF((float) climb.getPoints().get(endIndex).getEasting(), (float) climb.getPoints().get(endIndex).getNorthing());
        Log.d(TAG, "Check finished: " + currentPoint.x + "," + currentPoint.y + "; " + end.x + "," + end.y);

        if (hasPassedPoint(end, currentPoint)) {
            // Left last segment so climb has finished
            Log.d(TAG, "Climb finished");
            finishClimb(attempt);
        }
    }

    public void finishClimb(ClimbAttempt attempt) {
        attemptInProgress = false;
        PositionMonitor.getInstance().setOnClimbId(-1);

        // Calculate duration
        long startTime = attempt.getDatetime().atZone(ZoneId.systemDefault()).toEpochSecond();
        long endTime = attempt.getPoints().get(attempt.getPoints().size()-1).getTimestamp().atZone(ZoneId.systemDefault()).toEpochSecond();
        Log.d(TAG, "Duration: " + startTime + " to " + endTime);
        attempt.setDuration((int)(endTime - startTime));

        // Add to database then reset
        Database.getInstance().addAttempt(attempt, climb.getId());
        reset(PointType.ATTEMPT);
    }

    private float calcBearing(GPXRoute track, AttemptData attemptData) {
        int index = attemptData.getMinIndex();
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

        if (secondsSinceStart > pbAttempt.getPoints().get(pbAttempt.getPoints().size()-1).getSecondsFromStart()) {
            // PB has finished
            pbFinished = true;
        }

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
        this.climb.setPointsDist();
        this.climb.calcSmoothedPoints();
    }

    public void loadRoute(GPXRoute route) {
        this.route = route;
        this.lastRouteId = route.getId();
        this.route.setPointsDist();
    }

    public void clearRoute() {
        this.route = null;
        this.lastRouteId = -1;
    }

    public List<PlotPoint> getClimbPoints(GPXRoute profile, int startIdx, double targetDist, boolean smooth) {
        float distFromLast = 0;
        float totalDist = 0;
        boolean first = true;
        float lastX = 0;
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

    public AttemptStats getLastAttemptStats(int lastClimbId) {
        AttemptStats stats = new AttemptStats();
        List<AttemptStats> attemptStats = Database.getInstance().getClimbAttemptDurations(lastClimbId);
        stats.calcStats(attemptStats);

        GPXRoute lastClimb = Database.getInstance().getClimb(lastClimbId);
        lastClimb.setPointsDist();
        int numClimbPoints = lastClimb.getPoints().size();
        stats.setDistanceM(lastClimb.getPoints().get(numClimbPoints - 1).getDistFromStart());
        return stats;
    }

    private void setPrevPoint(PointF pt) {
        lastPoint = pt;
        if (prevPoints == null) {
            prevPoints = new LinkedList<>();
        }
        if (prevPoints.size() > 5) {
            prevPoints.remove(0);
        }
        prevPoints.add(pt);
    }

    private boolean hasPassedPoint(PointF pointOnSection, PointF currentPoint) {
        for (PointF pt : prevPoints) {
            if (LocationMonitor.pointWithinLineSegment(pointOnSection, pt, currentPoint)) {
                return true;
            }
        }
        return false;
    }
}
