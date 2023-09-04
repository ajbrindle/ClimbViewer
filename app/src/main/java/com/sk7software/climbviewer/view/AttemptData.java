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
import com.sk7software.util.aspectlogger.DebugTrace;

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

    @DebugTrace
    public float calcDist(RoutePoint loc, GPXRoute track, int index) {
        boolean found = false;
        PointF locPt = new PointF((float)loc.getEasting(), (float)loc.getNorthing());
        Projection proj = Database.getProjection(track.getProjectionId());
        int zone = track.getZone();

        int startIndex = index;
        int lastIndex = startIndex;

        // Get previous distance from start
        float calculatedDist = lastIndex < track.getPoints().size()-1 ?
                track.getPoints().get(lastIndex+1).getDistFromStart() :
                track.getPoints().get(track.getPoints().size()-1).getDistFromStart();

        // Look for point on track that matches with current location
        // Only look a few points beyond the last one to prevent picking up return points
        // on tracks that retrace themselves at some point
        int searchToIndex = startIndex + 10;
        if (searchToIndex >= track.getPoints().size()) {
            searchToIndex = track.getPoints().size();
        }

        Log.d(TAG, "Searching from " + startIndex + " to " + searchToIndex + " for " + locPt.x + "," + locPt.y);

        for (int i=startIndex+1; i<searchToIndex; i++) {
            RoutePoint pt = track.getPoints().get(i);

            PointF lastPointPt = new PointF((float)track.getPoints().get(i-1).getEasting(), (float)track.getPoints().get(i-1).getNorthing());
            PointF currentPointPt = new PointF((float)track.getPoints().get(i).getEasting(), (float)track.getPoints().get(i).getNorthing());

            Log.d(TAG, "Test " + i + ": (" + locPt.x + "," + locPt.y + ") " + lastPointPt.x + "," + lastPointPt.y + "; " + currentPointPt.x + "," + currentPointPt.y);

            // Determine if location is between this one and last one (with extra tolerance as probably
            // want to stay on track once on it)
            if (LocationMonitor.pointWithinLineSegmentWithTolerance(locPt, lastPointPt, currentPointPt, 2)) {
                Log.d(TAG, type + " within line segment: " + (i-1) + " to " + i);
                found = true;

                // Find the point on the track
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
                calculatedDist += track.getPoints().get(i-1).getDistFromStart();

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

                // Find index where gradient changes by more than 0.2%
                int nextIndex = i;
                for (int j=i; j<track.getPoints().size()-1; j++) {
                    if (Math.abs(calcGradient(track.getPoints().get(j), track.getPoints().get(j+1)) - currentGradient) > 0.2) {
                        nextIndex = j;
                        pt = track.getPoints().get(j);
                        break;
                    }
                }

                if (nextIndex < track.getPoints().size() - 1) {
                    nextGradient = calcGradient(pt, track.getPoints().get(nextIndex + 1));
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
        Log.d(TAG, "No close line segment");
        return -1;
    }

    public float calcDist(RoutePoint loc, GPXRoute track) {
        return calcDist(loc, track, minIndex);
    }

    private static float calcGradient(RoutePoint p1, RoutePoint p2) {
        double length = Math.sqrt(Math.pow(p2.getEasting() - p1.getEasting(), 2.0) +
                Math.pow(p2.getNorthing() - p1.getNorthing(), 2.0));
        double elevDiff = p2.getSmoothedElevation() - p1.getSmoothedElevation();
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
