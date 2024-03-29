package com.sk7software.climbviewer;

import android.graphics.PointF;
import android.util.Log;

import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.geo.GeoConvert;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.view.AttemptData;
import com.sk7software.util.aspectlogger.DebugTrace;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PositionMonitor {
    private static PositionMonitor INSTANCE = null;
    private static final String TAG = PositionMonitor.class.getSimpleName();
    private static final int SECTION_INVALID = -99;
    private static final int BEARING_RANGE = 60;

    private List<PointF> prevPoints;
    private Set<MonitorType> monitoring;
    private int routeId;
    private List<GPXRoute> climbs;
    private boolean onRoute;
    private boolean tryingToResume;
    private int routeStartIdx;
    private int currentClimbIdx;
    private int matchingSectionIdx;
    private int onClimbId;

    private long lastRouteDistFromStart = 0;
    private List<Rejoin> rejoinSections;

    private PositionMonitor() {
        super();
        prevPoints = new ArrayList<>();
        matchingSectionIdx = -1;
        rejoinSections = new ArrayList<>();
    }

    public static PositionMonitor getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PositionMonitor();
            INSTANCE.monitoring = new HashSet<>();
        }
        return INSTANCE;
    }

    public void setRouteToMonitor(int routeId) {
        this.routeId = routeId;
    }

    public void setClimbsToMonitor(List<GPXRoute> climbs) {
        this.climbs = climbs;
    }

    @DebugTrace
    public void locationChanged(RoutePoint point) {
        if (prevPoints.isEmpty()) {
            prevPoints.add(new PointF((float) point.getEasting(), (float) point.getNorthing()));
            return;
        }

        PointF currentPoint = new PointF((float) point.getEasting(), (float) point.getNorthing());

        // Check if we are on the selected route, or any climb
        if (monitoring.contains(MonitorType.CLIMB)) {
            monitorClimbs(point, currentPoint);
        }
        if (monitoring.contains(MonitorType.ROUTE)) {
            monitorRoute(point);
        }
        if (monitoring.contains(MonitorType.CURRENT_CLIMB)) {
            monitorCurrentClimb(point, currentPoint);
        }
    }

    private void monitorClimbs(RoutePoint point, PointF currentPoint) {
        Log.d(TAG, "Checking for climbs - total " + climbs.size());
        // Check if any of the listed climbs have started
        for (GPXRoute climb : climbs) {
            Log.d(TAG, "Checking climb: " + climb.getName());
            PointF start = new PointF((float) climb.getPoints().get(0).getEasting(), (float) climb.getPoints().get(0).getNorthing());
            if (climb.getZone() == GeoConvert.calcUTMZone(point.getLat(), point.getLon())) {
                int firstIdx = hasPassedCloseToPoint(start, currentPoint);
                if (firstIdx >= 0) {
                    PointF second = new PointF((float) climb.getPoints().get(1).getEasting(), (float) climb.getPoints().get(1).getNorthing());
                    if (checkDirection(start, second, currentPoint, firstIdx)) {
                        Log.d(TAG, "STARTED CLIMB " + climb.getName());
                        onClimbId = climb.getId();
                        prevPoints.clear();
                        return;
                    }
                }
            }
        }
        addToPrevPoints(currentPoint);
    }

    private void monitorCurrentClimb(RoutePoint point, PointF currentPoint) {
        int currentClimbId = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_ON_CLIMB_ID, -1);
        if (currentClimbId < 0) {
            Preferences.getInstance().clearIntPreference(Preferences.PREFERENCES_ON_CLIMB_ID);
            monitoring.remove(MonitorType.CURRENT_CLIMB);
        }

        GPXRoute climb = Database.getInstance().getClimb(currentClimbId);
        Log.d(TAG, "Checking for climb: " + climb.getName());

        for (int i=1; i<climb.getPoints().size(); i++) {
            PointF start = new PointF((float) climb.getPoints().get(i-1).getEasting(), (float) climb.getPoints().get(i-1).getNorthing());
            if (climb.getZone() == GeoConvert.calcUTMZone(point.getLat(), point.getLon())) {
                int firstIdx = hasPassedCloseToPoint(start, currentPoint);
                if (firstIdx >= 0) {
                    PointF second = new PointF((float) climb.getPoints().get(i).getEasting(), (float) climb.getPoints().get(i).getNorthing());
                    if (checkDirection(start, second, currentPoint, firstIdx)) {
                        Log.d(TAG, "RESUMED CLIMB " + climb.getName());
                        onClimbId = currentClimbId;
                        currentClimbIdx = i-1;
                        prevPoints.clear();
                        return;
                    }
                }
            }
        }
        addToPrevPoints(currentPoint);
    }

    private void monitorRoute(RoutePoint point) {
        Log.d(TAG, "Checking for route");
        // Check if any point on selected route is between current point and one of the previous ones
        GPXRoute route = ClimbController.getInstance().getRoute();

        if (route == null) {
            route = Database.getInstance().getRoute(routeId);
        }

        if (checkOnRoute(route, point, 4)) {
            if (!tryingToResume) {
                // Record where the route was initially started (and don't update if it is resumed)
                routeStartIdx = matchingSectionIdx;

                // Store in preferences
                Preferences.getInstance().addPreference(Preferences.PREFERENCES_ROUTE_ID, routeId);
                Preferences.getInstance().addPreference(Preferences.PREFERENCES_ROUTE_START_IDX, routeStartIdx);
            }
        }
    }

    private void addToPrevPoints(PointF currentPoint) {
        if (prevPoints.size() >= 5) {
            prevPoints.remove(0);
        }
        prevPoints.add(currentPoint);
    }

    @DebugTrace
    public boolean checkOnRoute(GPXRoute route, RoutePoint point, int directionCount) {
        PointF pointToCheck = new PointF((float) point.getEasting(), (float) point.getNorthing());

        // Rejoin sections is populated when a candidate point for joining the route has been found
        // It is required because a point could be on multiple sections of the route and all of them
        // need to be checked (can't abort searching after the first one)
        if (rejoinSections != null) {
            rejoinSections
                    .removeIf(r -> r.getIndex() == SECTION_INVALID || r.hasExpired());
        }

        if (!rejoinSections.isEmpty()) {
            addToPrevPoints(pointToCheck);

            for (Rejoin rejoinSection : rejoinSections) {
                int startIndex = rejoinSection.getIndex();
                if (startIndex < 0) {
                    startIndex = 0;
                }
                int endIndex = startIndex + 10;
                if (endIndex > route.getPoints().size() - 1) {
                    endIndex = route.getPoints().size() - 1;
                }

                // Return if the direction has been confirmed, otherwise search again
                if (checkDirection(pointToCheck, route, startIndex, endIndex, rejoinSection, directionCount)) {
                    Log.d(TAG, "ON ROUTE " + route.getName() + " (" + matchingSectionIdx + ")");
                    rejoinSections.stream().forEach(r -> r.setIndex(SECTION_INVALID)); // Reset for clearing
                    onRoute = true;
                    return true;
                }
            }

            // Don't reset search if any potential matching sections are still available
            if (rejoinSections.stream().anyMatch(r -> r.getIndex() >= 0)) {
                return false;
            }
        }

        matchingSectionIdx = -1;
        rejoinSections.clear();

        // Look for all sections of the route that the current point is close to and add them to the
        // list of indexes that could be where the current point is on the route
        for (int i = 1; i < route.getPoints().size()-1; i++) {
            PointF pt1 = new PointF((float) route.getPoints().get(i-1).getEasting(), (float) route.getPoints().get(i-1).getNorthing());
            PointF pt2 = new PointF((float) route.getPoints().get(i).getEasting(), (float) route.getPoints().get(i).getNorthing());
            if (LocationMonitor.pointWithinLineSegment(pointToCheck, pt1, pt2)) {
                // Found a potential matching section but not on route until direction has been checked
                Log.d(TAG, "ON ROUTE SECTION " + route.getName() + " (index " + (i-1) + " to " + i +
                        ": " + pointToCheck.x + "," + pointToCheck.y + ")");

                // Initialise settings to check direction
                Rejoin r = new Rejoin();
                r.addSection(i-1);
                r.setDistFromStart(0);
                rejoinSections.add(r);
            }
        }

        return false;
    }

    private int hasPassedCloseToPoint(PointF point, PointF currentPoint) {
        for (int i=prevPoints.size()-1; i>=0; i--) {
            if (LocationMonitor.pointWithinLineSegment(point, prevPoints.get(i), currentPoint)) {
                Log.d(TAG, "Match: " + i + " " + prevPoints.get(i).x + "," + prevPoints.get(i).y + " to " +
                        currentPoint.x + "," + currentPoint.y + "; Past: " +
                        point.x + "," + point.y);
                return i;
            }
        }
        return -1;
    }

    private boolean checkDirection(PointF first, PointF second, PointF currentPoint, int firstIdx) {
        PointF lastPoint = prevPoints.get(firstIdx);

        // Calc bearing between first and second
        float trackDeltaX = second.x - first.x;
        float trackDeltaY = second.y - first.y;
        double trackBearing = Math.atan2(trackDeltaY, trackDeltaX) * 180 / Math.PI;

        float rideDeltaX = currentPoint.x - lastPoint.x;
        float rideDeltaY = currentPoint.y - lastPoint.y;
        double rideBearing = Math.atan2(rideDeltaY, rideDeltaX) * 180 / Math.PI;

        // Direction is OK if ride bearing is in same sector as track bearing
        double minBearing = trackBearing - BEARING_RANGE;
        double maxBearing = trackBearing + BEARING_RANGE;

        if (minBearing < 0) {
            minBearing += 360;
            maxBearing += 360;
            rideBearing += 360;
        }

        return rideBearing >= minBearing && rideBearing < maxBearing;
    }

    // This looks complicated but does work
    // It is aiming to check that the rider is progressing along the route in the right direction
    // At each point, the rider must either be further along the current matching segment of the route
    // or they have moved into one further along. When a target number of correct direction measures have
    // been found, the method returns true and confirms that the route is being followed. If there is
    // an incorrect direction measure, then the count is decremented and, when it hits a certain negative
    // score, the section is discarded because the rider appears to be going in the opposite direction.
    // This direction check is specific to routes, where they could be joined at any point.
    @DebugTrace
    private boolean checkDirection(PointF currentPoint, GPXRoute track, int startIndex, int searchToIndex, Rejoin rejoinSection, int directionCount) {
        // Search from the route point where the match was identified to a set number of points ahead of there
        for (int i = startIndex+1; i < searchToIndex; i++) {
            PointF lastPointPt = new PointF((float) track.getPoints().get(i-1).getEasting(), (float) track.getPoints().get(i - 1).getNorthing());
            PointF currentPointPt = new PointF((float) track.getPoints().get(i).getEasting(), (float) track.getPoints().get(i).getNorthing());

            // Determine if location is between this one and last one
            if (LocationMonitor.pointWithinLineSegment(currentPoint, lastPointPt, currentPointPt)) {
                Log.d(TAG, "Found within line segment: " + (i-1) + " to " + i);

                if (i-1 > rejoinSection.getIndex()) {
                    // Moved into a more distant segment, so distance must have increased
                    rejoinSection.setIndex(i-1);
                    rejoinSection.incrementDirection();
                    rejoinSection.setDistFromStart(0);
                    matchingSectionIdx = i-1;
                    Log.d(TAG, "Index OK on check " + rejoinSection.getDirectionCounter() + " (" + i + ")");
                } else {
                    // Find the point on the track
                    PointF nearestPt = LocationMonitor.getXXYY(currentPoint, lastPointPt, currentPointPt);
                    RoutePoint routePt = new RoutePoint();
                    routePt.setEasting(nearestPt.x);
                    routePt.setNorthing(nearestPt.y);

                    // Find distance from start
                    float calculatedDist = (float) AttemptData.calcDelta(routePt, track.getPoints().get(i-1));
                    calculatedDist += track.getPoints().get(i-1).getDistFromStart();

                    // Check if further from the start than the previous matching point
                    if (calculatedDist > rejoinSection.getDistFromStart()) {
                        rejoinSection.setIndex(i-1);
                        rejoinSection.incrementDirection();
                        rejoinSection.setDistFromStart(calculatedDist);
                        matchingSectionIdx = i-1;
                        Log.d(TAG, "Distance OK on check " + rejoinSection.getDirectionCounter() + " (" + calculatedDist + ")");
                    } else {
                        rejoinSection.decrementDirection();
                        break;
                    }
                }

                // Check if an end state has been reached
                if (rejoinSection.getDirectionCounter() >= directionCount) {
                    matchingSectionIdx = rejoinSection.getIndex();
                    return true;
                }
            }
        }

        // Allow one incorrect direction before resetting
        if (rejoinSection.getDirectionCounter() > -2) {
            return false;
        }

        // Direction counter is wrong so reset
        rejoinSection.setIndex(SECTION_INVALID);
        return false;
    }

    public void doMonitor(MonitorType type) {
        if (monitoring == null) {
            monitoring = new HashSet<>();
        }
        Log.d(TAG, "ADDED MONITOR: " + type);
        monitoring.add(type);
     }

     public void stopMonitor(MonitorType type) {
        monitoring.remove(type);
     }

     public void stopAllMonitors() {
        monitoring.clear();
     }
     public void resetRejoin() {
        if (rejoinSections != null) {
            rejoinSections.clear();
        }
     }

    public enum MonitorType {
        NONE,
        ROUTE,
        CLIMB,
        CURRENT_CLIMB
    }

    @Getter
    @Setter
    public class Rejoin {
        int index;
        float distFromStart;
        long timeUpdated;
        int directionCounter;

        private static final long EXPIRED_MS = 10000;

        public void addSection(int index) {
            this.index = index;
            this.directionCounter = 0;
            this.timeUpdated = now();
        }

        public boolean hasExpired() {
            return (now() - timeUpdated > EXPIRED_MS);
        }
        public void incrementDirection() {
            directionCounter++;
            timeUpdated = now();
        }

        public void decrementDirection() {
            directionCounter--;
            timeUpdated = now();
        }

        private long now() {
            return (new Date()).getTime();
        }
    }
}
