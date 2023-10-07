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
import com.sk7software.climbviewer.geo.GeoConvert;
import com.sk7software.climbviewer.geo.Projection;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.util.aspectlogger.DebugTrace;

import java.math.BigDecimal;
import java.text.DecimalFormat;

public class LocationMonitor {
    private static LocationMonitor INSTANCE = null;
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

    private LocationMonitor() {
        super();
    }

    private LocationMonitor(ActivityUpdateInterface activity) {
        this.parent = activity;
        lm = (LocationManager) ((Activity)parent).getSystemService(LOCATION_SERVICE);
        locationListener = new ClimbLocationListener(parent);
        resumeListener();
    }

    public static LocationMonitor getInstance(ActivityUpdateInterface activity) {
        if (INSTANCE == null) {
            INSTANCE = new LocationMonitor(activity);
        } else {
            ((ClimbLocationListener) INSTANCE.locationListener).setNotifyActivity(activity);
            INSTANCE.resumeListener();
        }
        return INSTANCE;
    }

    @SuppressLint("MissingPermission")
    @DebugTrace
    public void resumeListener() {
        if (!listenerRunning) {
            Log.d(TAG, "Starting location monitor");
            lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    (long)LM_UPDATE_INTERVAL * 1000,
                    LM_UPDATE_DISTANCE,
                    locationListener);
            listenerRunning = true;
        } else {
            Log.d(TAG, "Monitor already running");
        }
    }

    public static void stopListener() {
        Log.d(TAG, "Stopping location monitor");
        if (INSTANCE != null) {
            if (INSTANCE.lm != null) {
                INSTANCE.lm.removeUpdates(INSTANCE.locationListener);
            }
            INSTANCE.listenerRunning = false;
        }
    }

    /**
     * Determines if the reference point (loc) is within the line segment bounded by a and b
     * In order to be valid, the perpendicular distance must be within the defined number of metres
     * @param loc - reference point (E, N)
     * @param a - last-but-one location update (E, N)
     * @param b - last location update (E, N)
     * @return true if point is within line segment (and close enough)
     */
    public static boolean pointWithinLineSegmentWithTolerance(PointF loc, PointF a, PointF b, int multiplier) {
        // If point is within the MAX_DIST of the start, then can override it being outside the perpendicular check
        double distAsq = Math.pow(a.x - loc.x, 2) + Math.pow(a.y - loc.y, 2);
        boolean override = distAsq < (MAX_DIST_SQ * multiplier * multiplier);

        // Algorithm from:
        // https://stackoverflow.com/questions/849211/shortest-distance-between-a-point-and-a-line-segment
        PointF xxyy = getXXYYWithOverride(loc, a, b, override);
        if (xxyy == null) {
            return false;
        }

        double dx = loc.x - xxyy.x;
        double dy = loc.y - xxyy.y;
        double distSq = dx * dx + dy * dy;
        return distSq < (MAX_DIST_SQ * multiplier * multiplier);
    }

    public static boolean pointWithinLineSegment(PointF loc, PointF a, PointF b) {
        return pointWithinLineSegmentWithTolerance(loc, a, b, 1);
    }

    public static PointF getXXYY (PointF start, PointF a, PointF b) {
        double distAsq = Math.pow(a.x - start.x, 2) + Math.pow(a.y - start.y, 2);
        boolean override = distAsq < MAX_DIST_SQ;

        return getXXYYWithOverride(start, a, b, override);
    }
    private static PointF getXXYYWithOverride(PointF start, PointF a, PointF b, boolean override) {
        double A = start.x - a.x;
        double B = start.y - a.y;
        double C = b.x - a.x;
        double D = b.y - a.y;

        double dot = A * C + B * D;
        double len_sq = C * C + D * D;
        double param = -1;
        if (len_sq != 0) { //in case of 0 length line
            param = dot / len_sq;
        }

        double xx;
        double yy;
        // param < 0 or param > 1 means point is not in between ends of line segment
        if (param <= 0 && !override) {
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
        private boolean firstUpdate;

        public ClimbLocationListener(ActivityUpdateInterface parent) {
            this.notifyActivity = parent;
            firstUpdate = true;
        }

        public void setNotifyActivity(ActivityUpdateInterface parent) {
            this.notifyActivity = parent;
        }

        @Override
        @DebugTrace
        public synchronized void onLocationChanged(Location loc) {
            if (firstUpdate) {
                firstUpdate = false;
                return;
            }

            if (loc != null) {
                // Check lat and lon are > 0
                if (Math.abs(loc.getLatitude()) > 0.001 && Math.abs(loc.getLongitude()) > 0.001) {
                    RoutePoint point = new RoutePoint();
                    try {
                        Log.d(TAG, "Location changed: " + loc.getLatitude() +
                                "," + loc.getLongitude());

                        // Set default projection details
                        int projId = Projection.SYS_UTM_WGS84;
                        int zone = GeoConvert.calcUTMZone(loc.getLatitude(), loc.getLongitude());

                        if (ClimbController.getInstance().isAttemptInProgress()) {
                            // Override with settings for the climb (should ensure that zone changes get ignored)
                            projId = ClimbController.getInstance().getClimb().getProjectionId();
                            zone = ClimbController.getInstance().getClimb().getZone();
                        } else if (ClimbController.getInstance().isRouteInProgress()) {
                            projId = ClimbController.getInstance().getRoute().getProjectionId();
                            zone = ClimbController.getInstance().getRoute().getZone();
                        }
                        //FROM DB: 560583.5, 5914919.0
                        point.setLat(loc.getLatitude());
                        point.setLon(loc.getLongitude());
                        point.setENFromLL(Database.getProjection(projId), zone);
                        point.setElevation(loc.getAltitude());
                        point.setAccuracy(loc.hasAccuracy() ? loc.getAccuracy() : 0);
                        point.setBearing(loc.getBearing());

                        DecimalFormat formatter = new DecimalFormat("#.###");
                        Log.d(TAG, "Location: " + formatter.format(point.getEasting()) + ", " + formatter.format(point.getNorthing()));

                        ClimbController.getInstance().updateClimbData(point, notifyActivity);
                    } catch (Exception e) {
                        Log.d(TAG, "Exception: " + e.getMessage());
                    } finally {
                        // Send notification to parent activity
                        notifyActivity.locationChanged(point);
                    }
                }
            }
        }
    }
}
