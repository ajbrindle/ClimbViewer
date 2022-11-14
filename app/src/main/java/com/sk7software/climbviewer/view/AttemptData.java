package com.sk7software.climbviewer.view;

import android.graphics.PointF;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.ClimbController;
import com.sk7software.climbviewer.LocationMonitor;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.geo.GeoConvert;
import com.sk7software.climbviewer.geo.Projection;
import com.sk7software.climbviewer.model.ClimbAttempt;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;

import java.util.HashMap;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AttemptData {
    private ClimbAttempt attempt;
    private RoutePoint position;
    private RoutePoint snappedPosition;
    private float dist;
    private float bearing;
    private float currentGradient;
    private float nextGradient;
    private float segmentToGo;
    private float elevDone;
    private Integer minIndex;
    private ClimbController.PointType type;

    private static final String TAG = AttemptData.class.getSimpleName();

    public float calcDist(RoutePoint loc, GPXRoute track) {
        boolean found = false;
        PointF locPt = new PointF((float)loc.getEasting(), (float)loc.getNorthing());
        Projection proj = Database.getProjection(track.getProjectionId());
        int zone = track.getZone();

        int startIndex = minIndex;
        int lastIndex = startIndex;

        // Get previous distance from start
        float calculatedDist = lastIndex < track.getPoints().size()-1 ?
                track.getPoints().get(lastIndex+1).getDistFromStart() :
                track.getPoints().get(track.getPoints().size()-1).getDistFromStart();

        // Look for point on climb that matches with current location
        for (int i=startIndex+1; i<track.getPoints().size(); i++) {
            RoutePoint pt = track.getPoints().get(i);

            PointF lastPointPt = new PointF((float)track.getPoints().get(i-1).getEasting(), (float)track.getPoints().get(i-1).getNorthing());
            PointF currentPointPt = new PointF((float)track.getPoints().get(i).getEasting(), (float)track.getPoints().get(i).getNorthing());

            // Determine if location is between this one and last one
            if (LocationMonitor.pointWithinLineSegment(locPt, lastPointPt, currentPointPt)) {
                Log.d(TAG, type + " within line segment: " + (i-1) + " to " + i);
                found = true;

                // Find the point on the route
                PointF nearestPt = LocationMonitor.getXXYY(locPt, lastPointPt, currentPointPt);
                RoutePoint routePt = new RoutePoint();
                routePt.setEasting(nearestPt.x);
                routePt.setNorthing(nearestPt.y);
                LatLng ll = GeoConvert.convertGridToLL(proj, routePt, zone);
                routePt.setLat(ll.latitude);
                routePt.setLon(ll.longitude);
                snappedPosition = routePt;

                // Calculate how far along this line segment the current point is
                calculatedDist = (float) calcDelta(routePt, track.getPoints().get(i-1));
                calculatedDist+= track.getPoints().get(i-1).getDistFromStart();

                // Calculate elevation climbed - it is the climb to the previous point plus
                // the fraction of the elevation gain on this line segment
                elevDone = track.getPoints().get(i-1).getElevFromStart();

                if (track.getPoints().get(i).getElevation() > track.getPoints().get(i-1).getElevation()) {
                    float elevDiff = (float)(track.getPoints().get(i).getElevation() - track.getPoints().get(i-1).getElevation());
                    elevDone += elevDiff * calcFraction(routePt, track.getPoints().get(i-1), track.getPoints().get(i));
                }
                Log.d(TAG, "ELEVATION DONE: " + elevDone);

                startIndex = i-1;

                // Calculate gradients
                currentGradient = calcGradient(track.getPoints().get(i-1), track.getPoints().get(i));

                if (i < track.getPoints().size() - 1) {
                    nextGradient = calcGradient(pt, track.getPoints().get(i + 1));
                } else {
                    nextGradient = 0;
                }
                segmentToGo = pt.getDistFromStart() - calculatedDist;

                break;
            }
        }
        if (found) {
            Log.d(TAG, type.name() + " distance so far: " + calculatedDist + " [" + startIndex + "]");
            minIndex = startIndex;
            dist = calculatedDist;
            return calculatedDist;
        }
        return -1;

    }

    private static float calcGradient(RoutePoint p1, RoutePoint p2) {
        double length = Math.sqrt(Math.pow(p2.getEasting() - p1.getEasting(), 2.0) +
                Math.pow(p2.getNorthing() - p1.getNorthing(), 2.0));
        double elevDiff = p2.getElevation() - p1.getElevation();
        return (float)(100.0 * elevDiff / length);
    }

    public static double calcDelta(RoutePoint point, RoutePoint referencePoint) {
        return Math.sqrt(Math.pow(referencePoint.getEasting() - point.getEasting(), 2.0) +
                Math.pow(referencePoint.getNorthing() - point.getNorthing(), 2.0));
    }

    public static double calcFraction(RoutePoint point, RoutePoint start, RoutePoint end) {
        double total = Math.sqrt(Math.pow(end.getEasting() - start.getEasting(), 2.0) +
                Math.pow(end.getNorthing() - start.getNorthing(), 2.0));
        double done = calcDelta(point, start);
        return done/total;
    }

}