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
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PositionMonitor {
    private static PositionMonitor INSTANCE = null;
    private static final String TAG = PositionMonitor.class.getSimpleName();
    private static final int SECTION_INVALID = -99;

    private List<PointF> prevPoints;
    private Set<MonitorType> monitoring;
    private int routeId;
    private List<GPXRoute> climbs;
    private boolean onRoute;
    private boolean tryingToResume;
    private int routeStartIdx;
    private int matchingSectionIdx;
    private int onClimbId;

    private long lastRouteDistFromStart = 0;
    private List<Rejoin> rejoinSections;

    private PositionMonitor() {
        super();
        prevPoints = new LinkedList<>();
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
                            break;
                        }
                    }
                }
            }
        }
        if (monitoring.contains(MonitorType.ROUTE)) {
            Log.d(TAG, "Checking for route");
            // Check if any point on selected route is between current point and one of the previous ones
            GPXRoute route = ClimbController.getInstance().getRoute();

            if (route == null) {
                route = Database.getInstance().getRoute(routeId);
            }

            if (checkOnRoute(route, point, 4)) {
                if (!tryingToResume) {
                    routeStartIdx = matchingSectionIdx;

                    // Store in preferences
                    Preferences.getInstance().addPreference(Preferences.PREFERENCES_ROUTE_ID, routeId);
                    Preferences.getInstance().addPreference(Preferences.PREFERENCES_ROUTE_START_IDX, routeStartIdx);
                }
            }
        }
        addToPrevPoints(currentPoint);
    }

    private void addToPrevPoints(PointF currentPoint) {
        if (prevPoints.size() >= 5) {
            prevPoints.remove(0);
        }
        prevPoints.add(currentPoint);
    }

    @DebugTrace
    public boolean checkOnRoute(GPXRoute route, RoutePoint point, int directionCount) {
        PointF currentPoint = new PointF((float) point.getEasting(), (float) point.getNorthing());

        if (rejoinSections != null) {
            rejoinSections
                    .removeIf(r -> r.getIndex() == SECTION_INVALID || r.hasExpired());
            Log.d(TAG, "REJOIN SECTIONS: " + rejoinSections.stream().map(r -> r.getIndex()).collect(Collectors.toList()));
        }

        if (!rejoinSections.isEmpty()) {
            addToPrevPoints(currentPoint);

            for (Rejoin rejoinSection : rejoinSections) {
                int startIndex = rejoinSection.getIndex();
                int endIndex = startIndex + 10;
                if (startIndex < 0) {
                    startIndex = 0;
                }
                if (endIndex > route.getPoints().size() - 1) {
                    endIndex = route.getPoints().size() - 1;
                }

                // Return if the direction has been confirmed, otherwise search again
                if (checkDirection(currentPoint, route, startIndex, endIndex, rejoinSection, directionCount)) {
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

        Log.d(TAG, "Checking points");
        // Look for all sections of the route that the current point is close to
        for (int i = 1; i < route.getPoints().size()-1; i++) {
            PointF pt1 = new PointF((float) route.getPoints().get(i-1).getEasting(), (float) route.getPoints().get(i-1).getNorthing());
            PointF pt2 = new PointF((float) route.getPoints().get(i).getEasting(), (float) route.getPoints().get(i).getNorthing());
            if (LocationMonitor.pointWithinLineSegment(currentPoint, pt1, pt2)) {
                // Found a potential matching section but not on route until direction has been checked
                Log.d(TAG, "ON ROUTE SECTION " + route.getName() + " (index " + (i-1) + " to " + i +
                        ": " + currentPoint.x + "," + currentPoint.y + ")");
                matchingSectionIdx = i-1;

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

        // Direction is OK if ride bearing is in same quadrant as track bearing (45 deg either way)
        double minBearing = trackBearing - 45;
        double maxBearing = trackBearing + 45;

        if (minBearing < 0) {
            minBearing += 360;
            maxBearing += 360;
            rideBearing += 360;
        }

        return rideBearing >= minBearing && rideBearing < maxBearing;
    }

    @DebugTrace
    private boolean checkDirection(PointF currentPoint, GPXRoute track, int startIndex, int searchToIndex, Rejoin rejoinSection, int directionCount) {
        for (int i = startIndex+1; i < searchToIndex; i++) {
            RoutePoint pt = track.getPoints().get(i);

            PointF lastPointPt = new PointF((float) track.getPoints().get(i - 1).getEasting(), (float) track.getPoints().get(i - 1).getNorthing());
            PointF currentPointPt = new PointF((float) track.getPoints().get(i).getEasting(), (float) track.getPoints().get(i).getNorthing());

            // Determine if location is between this one and last one
            if (LocationMonitor.pointWithinLineSegment(currentPoint, lastPointPt, currentPointPt)) {
                Log.d(TAG, "Found within line segment: " + (i - 1) + " to " + i);

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
                    float calculatedDist = (float) AttemptData.calcDelta(routePt, track.getPoints().get(i-1));
                    calculatedDist += track.getPoints().get(i-1).getDistFromStart();

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
        CLIMB
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
