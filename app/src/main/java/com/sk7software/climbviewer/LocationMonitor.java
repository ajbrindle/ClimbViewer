package com.sk7software.climbviewer;

import static android.content.Context.LOCATION_SERVICE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.PointF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.util.Log;

import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.geo.Projection;
import com.sk7software.climbviewer.model.RoutePoint;

public class LocationMonitor {
    private LocationManager lm;
    private LocationListener locationListener;
    private boolean listenerRunning;
    private ActivityUpdateInterface parent;

    private static final float MAX_DIST = 15;
    private static final float MAX_DIST_SQ = MAX_DIST * MAX_DIST;

    // Time (in seconds) and distance (in metres) between updates to the location manager
    private static final int LM_UPDATE_INTERVAL = 1;
    private static final int LM_UPDATE_DISTANCE = 1;

    private static final String TAG = LocationMonitor.class.getSimpleName();

    public LocationMonitor() {
        super();
    }

    public LocationMonitor(ActivityUpdateInterface activity) {
        this.parent = activity;
        lm = (LocationManager) ((Activity)parent).getSystemService(LOCATION_SERVICE);
        locationListener = new ClimbLocationListener(parent);
        resumeListener();
    }

    @SuppressLint("MissingPermission")
    public void resumeListener() {
        if (!listenerRunning) {
            lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LM_UPDATE_INTERVAL * 1000,
                    LM_UPDATE_DISTANCE,
                    locationListener);
            listenerRunning = true;
        }
    }

    public void stopListener() {
        lm.removeUpdates(locationListener);
        listenerRunning = false;
    }

    /**
     * Determines if the start point of the climb is within the line segment of the last two live
     * location updates. In order to be valid, the perpendicular distance must be within 10 metres
     * @param start - start point of route (E, N)
     * @param a - last-but-one location update (E, N)
     * @param b - last location update (E, N)
     * @return true if point is within line segment (and close enough)
     */
    public static boolean pointWithinLineSegment(PointF start, PointF a, PointF b) {
        // Algorithm from:
        // https://stackoverflow.com/questions/849211/shortest-distance-between-a-point-and-a-line-segment
        PointF xxyy = getXXYY(start, a, b);
        if (xxyy == null) {
            return false;
        }

        double dx = start.x - xxyy.x;
        double dy = start.y - xxyy.y;
        double distSq = dx * dx + dy * dy;
        Log.d(TAG, "Dist sq: " + distSq);
        return distSq < MAX_DIST_SQ;
    }

    public static PointF getXXYY(PointF start, PointF a, PointF b) {
        double A = start.x - a.x;
        double B = start.y - a.y;
        double C = b.x - a.x;
        double D = b.y - a.y;

        double dot = A * C + B * D;
        double len_sq = C * C + D * D;
        double param = -1;
        if (len_sq != 0) //in case of 0 length line
            param = dot / len_sq;

        double xx, yy;
        // param < 0 or param > 1 means point is not in between ends of line segment
        if (param < 0) {
            return null;
        }
        else if (param > 1) {
            return null;
        }
        else {
            xx = a.x + param * C;
            yy = a.y + param * D;
            return new PointF((float)xx, (float)yy);
        }
    }

    /**
     * Determines that the direction of travel is the right one for the route by checking that
     * the current position is nearer to the second route point than the last position.
     * This assumes that the first check (position is on the first line segment) has passed
     * @param second - second point on route
     * @param a - last-but-one location update (E, N)
     * @param b - last location update (E, N)
     * @return true if b is nearer to second point than a is
     */
    public static boolean isRightDirection(PointF second, PointF a, PointF b) {
        double distA2 = ((a.x - second.x) * (a.x - second.x)) +
                ((a.y - second.y) * (a.y - second.y));
        double distB2 = ((b.x - second.x) * (b.x - second.x)) +
                ((b.y - second.y) * (b.y - second.y));
        return distB2 < distA2;
    }

    public boolean isListenerRunning() {
        return listenerRunning;
    }

    public static class ClimbLocationListener implements LocationListener {
        private ActivityUpdateInterface notifyActivity;

        public ClimbLocationListener(ActivityUpdateInterface parent) {
            this.notifyActivity = parent;
        }

        @Override
        public synchronized void onLocationChanged(Location loc) {
            if (loc != null) {
                // Check lat and lon are > 0
                if (Math.abs(loc.getLatitude()) > 0.001 && Math.abs(loc.getLongitude()) > 0.001) {

                    try {
                        boolean mapChanged = false;
                        Log.d(TAG, "Location changed: " + loc.getLatitude() +
                                "," + loc.getLongitude());

                        RoutePoint point = new RoutePoint();
                        point.setLat(loc.getLatitude());
                        point.setLon(loc.getLongitude());
                        point.setENFromLL(Database.getProjection(Projection.SYS_OSGB36), 0);
                        point.setElevation(loc.getAltitude());

                        ClimbController.getInstance().updateClimbData(point, notifyActivity);

                        // Send notification to parent activity
                        notifyActivity.locationChanged(point);
                    } catch (Exception e) {
                        Log.d(TAG, "Exception: " + e.getMessage());
                    }
                }
            }
        }
    }
}
