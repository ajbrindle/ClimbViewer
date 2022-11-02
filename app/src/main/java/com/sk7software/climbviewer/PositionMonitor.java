package com.sk7software.climbviewer;

import android.content.Intent;
import android.graphics.PointF;
import android.util.Log;

import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.geo.GeoConvert;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;

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

    private PointF lastPoint;
    private Set<MonitorType> monitoring;
    private int routeId;
    private List<GPXRoute> climbs;
    private boolean onRoute;
    private boolean tryingToResume;
    private int routeStartIdx;
    private int onClimbId;

    private PositionMonitor() {
        super();
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
        if (lastPoint == null) {
            lastPoint = new PointF((float) point.getEasting(), (float) point.getNorthing());
            return;
        }

        PointF currentPoint = new PointF((float) point.getEasting(), (float) point.getNorthing());

        // Check if we are on the selected route, or any climb
        if (monitoring.contains(MonitorType.CLIMB)) {
            // Check if any of the listed climbs have started
            for (GPXRoute climb : climbs) {
                PointF start = new PointF((float) climb.getPoints().get(0).getEasting(), (float) climb.getPoints().get(0).getNorthing());
                if (climb.getZone() == GeoConvert.calcUTMZone(point.getLat(), point.getLon()) &&
                        LocationMonitor.pointWithinLineSegment(start, lastPoint, currentPoint)) {
                    PointF second = new PointF((float) climb.getPoints().get(1).getEasting(), (float) climb.getPoints().get(1).getNorthing());
                    if (LocationMonitor.isRightDirection(second, lastPoint, currentPoint)) {
                        Log.d(TAG, "STARTED CLIMB " + climb.getName());
                        onClimbId = climb.getId();
                        break;
                    }
                }
            }
        }
        if (monitoring.contains(MonitorType.ROUTE)) {
            // Check if the point is on the selected route
            GPXRoute route = Database.getInstance().getRoute(routeId);
            for (int i = 0; i < route.getPoints().size() - 1; i++) {
                PointF start = new PointF((float) route.getPoints().get(i).getEasting(), (float) route.getPoints().get(i).getNorthing());
                if (route.getZone() == GeoConvert.calcUTMZone(point.getLat(), point.getLon()) &&
                        LocationMonitor.pointWithinLineSegment(start, lastPoint, currentPoint)) {
                    PointF second = new PointF((float) route.getPoints().get(i + 1).getEasting(), (float) route.getPoints().get(i + 1).getNorthing());
                    if (LocationMonitor.isRightDirection(second, lastPoint, currentPoint)) {
                        Log.d(TAG, "ON ROUTE " + route.getName());
                        onRoute = true;
                        if (!tryingToResume) {
                            routeStartIdx = i;
                        }
                        break;
                    }
                }
            }
        }
        lastPoint = currentPoint;
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

    public enum MonitorType {
        NONE,
        ROUTE,
        CLIMB;
    }
}
