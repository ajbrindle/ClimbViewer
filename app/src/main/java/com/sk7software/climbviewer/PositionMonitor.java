package com.sk7software.climbviewer;

import android.graphics.PointF;
import android.util.Log;

import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.geo.GeoConvert;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;

import java.util.ArrayList;
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

    private List<PointF> prevPoints;
    private Set<MonitorType> monitoring;
    private int routeId;
    private List<GPXRoute> climbs;
    private boolean onRoute;
    private boolean tryingToResume;
    private int routeStartIdx;
    private int matchingSectionIdx;
    private int onClimbId;

    private PositionMonitor() {
        super();
        prevPoints = new ArrayList<>();
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

    public void locationChanged(RoutePoint point) {
        if (prevPoints.isEmpty()) {
            prevPoints.add(new PointF((float) point.getEasting(), (float) point.getNorthing()));
            return;
        }

        PointF currentPoint = new PointF((float) point.getEasting(), (float) point.getNorthing());

        // Check if we are on the selected route, or any climb
        if (monitoring.contains(MonitorType.CLIMB)) {
            // Check if any of the listed climbs have started
            for (GPXRoute climb : climbs) {
                PointF start = new PointF((float) climb.getPoints().get(0).getEasting(), (float) climb.getPoints().get(0).getNorthing());
                if (climb.getZone() == GeoConvert.calcUTMZone(point.getLat(), point.getLon()) &&
                        hasPassedCloseToPoint(start, currentPoint)) {
                    PointF second = new PointF((float) climb.getPoints().get(1).getEasting(), (float) climb.getPoints().get(1).getNorthing());
                    if (checkDirection(second, currentPoint)) {
                        Log.d(TAG, "STARTED CLIMB " + climb.getName());
                        onClimbId = climb.getId();
                        break;
                    }
                }
            }
        }
        if (monitoring.contains(MonitorType.ROUTE)) {
            // Check if any point on selected route is between current point and one of the previous ones
            GPXRoute route = ClimbController.getInstance().getRoute();
            if (route == null) {
                route = Database.getInstance().getRoute(routeId);
            }
//            Log.d(TAG, "Find " + currentPoint.x + "," + currentPoint.y);
            for (int i = 0; i < route.getPoints().size() - 1; i++) {
                PointF routePt = new PointF((float) route.getPoints().get(i).getEasting(), (float) route.getPoints().get(i).getNorthing());
                if (route.getZone() == GeoConvert.calcUTMZone(point.getLat(), point.getLon()) &&
                        hasPassedCloseToPoint(routePt, currentPoint)) {
                    PointF second = new PointF((float) route.getPoints().get(i + 1).getEasting(), (float) route.getPoints().get(i + 1).getNorthing());
                    if (checkDirection(second, currentPoint)) {
                        Log.d(TAG, "ON ROUTE " + route.getName() + " (index " + i + ": " +
                                prevPoints.get(prevPoints.size()-1).x + "," + prevPoints.get(prevPoints.size()-1).y + " to " +
                                        currentPoint.x + "," + currentPoint.y + "; " +
                                        routePt.x + "," + routePt.y + ")");
                        onRoute = true;
                        matchingSectionIdx = i;

                        if (!tryingToResume) {
                            routeStartIdx = i;

                            // Store in preferences
                            Preferences.getInstance().addPreference(Preferences.PREFERENCES_ROUTE_ID, routeId);
                            Preferences.getInstance().addPreference(Preferences.PREFERENCES_ROUTE_START_IDX, routeStartIdx);
                        }
                        break;
                    }
                }
            }
        }

        if (prevPoints.size() >= 5) {
            prevPoints.remove(0);
        }
        prevPoints.add(currentPoint);
    }

    private boolean hasPassedCloseToPoint(PointF point, PointF currentPoint) {
        for (int i=prevPoints.size()-1; i>=0; i--) {
            if (LocationMonitor.pointWithinLineSegment(point, prevPoints.get(i), currentPoint)) {
//                Log.d(TAG, "Match: " + i + " " + prevPoints.get(i).x + "," + prevPoints.get(i).y);
                return true;
            }
        }
        return false;
    }

    private boolean checkDirection(PointF second, PointF currentPoint) {
        return LocationMonitor.isRightDirection(second, prevPoints.get(prevPoints.size()-1), currentPoint);
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

    public enum MonitorType {
        NONE,
        ROUTE,
        CLIMB
    }
}
