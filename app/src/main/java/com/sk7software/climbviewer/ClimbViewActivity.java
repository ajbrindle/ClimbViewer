package com.sk7software.climbviewer;

import android.graphics.PointF;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.model.AttemptPoint;
import com.sk7software.climbviewer.model.ClimbAttempt;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.view.ClimbView;
import com.sk7software.climbviewer.view.TrackView;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public class ClimbViewActivity extends AppCompatActivity implements ActivityUpdateInterface {

    private ClimbView elevationView;
    private TrackView trackView;
    private MapFragment map;
    private int climbId;
    private GPXRoute climb;
    private LocationMonitor monitor;
    private PointF lastPoint;
    private ClimbAttempt attempt;
    private ClimbAttempt pb;
    private LocalDateTime pbStart;
    private TextView txtProgress;

    private static final String TAG = ClimbViewActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_climb_view);

        monitor = new LocationMonitor(this);

        climbId = getIntent().getIntExtra("id", 0);
        climb = Database.getInstance().getClimb(climbId);

        // Set cumulative distances
        setDistances();

        attempt = new ClimbAttempt();
        attempt.setDatetime(LocalDateTime.now());
        attempt.setInProgress(true);

        pb = Database.getInstance().getClimbPB(climbId);
        if (pb != null && pb.getPoints() != null && pb.getPoints().size() > 0) {
            pbStart = pb.getPoints().get(0).getTimestamp();
            pb.getPoints().stream()
                    .forEach(p -> p.setSecondsFromStart(ChronoUnit.SECONDS.between(pbStart, p.getTimestamp())));
        }

        elevationView = (ClimbView) findViewById(R.id.elevationView);
        elevationView.setClimb(climb);
        elevationView.setPB(pb);
        elevationView.invalidate();

        trackView = (TrackView)findViewById(R.id.trackView);

        txtProgress = (TextView)findViewById(R.id.txtProgress);
        map = (MapFragment)getSupportFragmentManager().findFragmentById(R.id.fragmentContainerView);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        if (monitor != null && !monitor.isListenerRunning()) {
            monitor.resumeListener();
        }
        super.onResume();
    }

    @Override
    protected void onStop() {
        if (monitor.isListenerRunning()) {
            monitor.stopListener();
        }
        super.onStop();
    }

    @Override
    public void locationChanged(RoutePoint point) {
        if (lastPoint == null) {
            lastPoint = new PointF((float)point.getEasting(), (float)point.getNorthing());
            return;
        }

        PointF currentPoint = new PointF((float)point.getEasting(), (float)point.getNorthing());

        if (attempt.isInProgress()) {
            LocalDateTime now = LocalDateTime.now();
            attempt.addPoint(point, now);

            int endIndex = climb.getPoints().size() - 1;

            // Plot location on map
            map.addMarker(new LatLng(point.getLat(), point.getLon()), BitmapDescriptorFactory.HUE_BLUE);
            float attemptDist = elevationView.plotLocation(point);
            RoutePoint pbPt = calcPbLocation(now);
            float pbDist = elevationView.pbLocation(pbPt);
            float timeDiff = calcTimeDiff(point, now);
            if (timeDiff != -99) {
                txtProgress.setText("Diff to PB: " + timeDiff);
            }
            trackView.setDistances(attemptDist, pbDist);
            elevationView.invalidate();
            trackView.invalidate();

            Log.d(TAG, "Dist between: " + Math.sqrt(Math.pow(pbPt.getEasting()-point.getEasting(), 2.0) +
                                                Math.pow(pbPt.getNorthing()-point.getNorthing(), 2.0)));

            // Check if the climb has finished
            PointF end = new PointF((float) climb.getPoints().get(endIndex).getEasting(), (float) climb.getPoints().get(endIndex).getNorthing());

            if (LocationMonitor.pointWithinLineSegment(end, lastPoint, currentPoint)) {
                // Climb has finished
                attempt.setInProgress(false);

                // Calculate duration
                long startTime = attempt.getPoints().get(0).getTimestamp().atZone(ZoneId.systemDefault()).toEpochSecond();
                long endTime = attempt.getPoints().get(attempt.getPoints().size()-1).getTimestamp().atZone(ZoneId.systemDefault()).toEpochSecond();
                attempt.setDuration((int)((endTime - startTime)/1000));

                // Add to database
                Database.getInstance().addAttempt(attempt, climbId);
            }
        }

        lastPoint = currentPoint;
    }

    private RoutePoint calcPbLocation(LocalDateTime now) {
        if (pb == null || pb.getPoints() == null || pb.getPoints().size() == 0) return null;

        long secondsSinceStart = ChronoUnit.SECONDS.between(attempt.getDatetime(), now);

        // Find point on pb that is this many seconds from the start
        AttemptPoint pt = pb.getPoints().stream()
                .filter(p -> p.getSecondsFromStart() <= secondsSinceStart)
                .sorted((a,b) -> Long.compare(b.getSecondsFromStart(), a.getSecondsFromStart()))
                .findFirst()
                .orElse(null);
        if (pt != null) {
            // Found earliest point
            if (pt.getSecondsFromStart() == secondsSinceStart) {
                // Exact match
                Log.d(TAG, "PB: " + pt.getPoint().getLat() + "," + pt.getPoint().getLon());
                map.addMarker(new LatLng(pt.getPoint().getLat(), pt.getPoint().getLon()), BitmapDescriptorFactory.HUE_GREEN);
                return pt.getPoint();
            } else {
                // Get next point
                Log.d(TAG, "PB1: " + pt.getPoint().getLat() + "," + pt.getPoint().getLon());
                Log.d(TAG, "PB1: " + pt.getPoint().getEasting() + "," + pt.getPoint().getNorthing());
                AttemptPoint nextPt = pb.getPoints().stream()
                        .filter(p -> p.getSecondsFromStart() > secondsSinceStart)
                        .findFirst()
                        .orElse(null);

                if (nextPt != null) {
                    // Next point
                    Log.d(TAG, "PB2: " + nextPt.getPoint().getLat() + "," + nextPt.getPoint().getLon());
                    Log.d(TAG, "PB2: " + nextPt.getPoint().getEasting() + "," + nextPt.getPoint().getNorthing());
                    long timeDiff = nextPt.getSecondsFromStart() - pt.getSecondsFromStart();
                    double weighting = (double)(secondsSinceStart - pt.getSecondsFromStart()) / (double)timeDiff;
                    RoutePoint routePoint = new RoutePoint();
                    routePoint.setEasting(pt.getPoint().getEasting() + (weighting * (nextPt.getPoint().getEasting() - pt.getPoint().getEasting())));
                    routePoint.setNorthing(pt.getPoint().getNorthing() + (weighting * (nextPt.getPoint().getNorthing() - pt.getPoint().getNorthing())));
                    routePoint.setLat(pt.getPoint().getLat() + (weighting * (nextPt.getPoint().getLat() - pt.getPoint().getLat())));
                    routePoint.setLon(pt.getPoint().getLon() + (weighting * (nextPt.getPoint().getLon() - pt.getPoint().getLon())));

                    Log.d(TAG, "PBx: " + routePoint.getLat() + "," + routePoint.getLon());
                    Log.d(TAG, "PBx: " + routePoint.getEasting() + "," + routePoint.getNorthing());
                    map.addMarker(new LatLng(routePoint.getLat(), routePoint.getLon()), BitmapDescriptorFactory.HUE_GREEN);
                    return routePoint;
                } else {
                    map.addMarker(new LatLng(pt.getPoint().getLat(), pt.getPoint().getLon()), BitmapDescriptorFactory.HUE_GREEN);
                    return pt.getPoint();
                }
            }
        }
        return null;
    }

    private void setDistances() {
        float dist = 0;
        climb.getPoints().get(0).setDistFromStart(0);

        for (int i=1; i<climb.getPoints().size(); i++) {
            dist += Math.sqrt(Math.pow(climb.getPoints().get(i).getEasting() - climb.getPoints().get(i-1).getEasting(), 2.0) +
                            Math.pow(climb.getPoints().get(i).getNorthing() - climb.getPoints().get(i-1).getNorthing(), 2.0));
            climb.getPoints().get(i).setDistFromStart(dist);
        }
    }

    private float calcTimeDiff(RoutePoint loc, LocalDateTime now) {
        // Determine where this point is on the PB
        RoutePoint lastPoint = null;
        PointF locPt = new PointF((float)loc.getEasting(), (float)loc.getNorthing());
        float secondsToPoint = 0;
        int lastIndex = 0;

        for (AttemptPoint attPt : pb.getPoints()) {
            if (lastPoint == null) {
                lastPoint = attPt.getPoint();
                continue;
            }

            RoutePoint pt = attPt.getPoint();

            PointF lastPointPt = new PointF((float)lastPoint.getEasting(), (float)lastPoint.getNorthing());
            PointF attemptPointPt = new PointF((float)attPt.getPoint().getEasting(), (float)attPt.getPoint().getNorthing());

            // Determine if location is between this one and last one
            if (LocationMonitor.pointWithinLineSegment(locPt, lastPointPt, attemptPointPt)) {
                // Find the point on the route
                PointF nearestPt = LocationMonitor.getXXYY(locPt, lastPointPt, attemptPointPt);
                RoutePoint routePt = new RoutePoint();
                routePt.setEasting(nearestPt.x);
                routePt.setNorthing(nearestPt.y);
                float distAlong = (float)calcDelta(routePt, pb.getPoints().get(lastIndex).getPoint().getEasting(), pb.getPoints().get(lastIndex).getPoint().getNorthing());
                float distBetween = (float)Math.sqrt(Math.pow(attPt.getPoint().getEasting() - pb.getPoints().get(lastIndex).getPoint().getEasting(), 2.0) +
                                            Math.pow(attPt.getPoint().getNorthing() - pb.getPoints().get(lastIndex).getPoint().getNorthing(), 2.0));
                float weighting = distAlong / distBetween;
                secondsToPoint = weighting * (attPt.getSecondsFromStart() - pb.getPoints().get(lastIndex).getSecondsFromStart());
                secondsToPoint += pb.getPoints().get(lastIndex).getSecondsFromStart();
                return secondsToPoint - (ChronoUnit.SECONDS.between(attempt.getDatetime(), now));
            }

            lastIndex++;
            lastPoint = pt;
        }
        return -99;
    }

    private double calcDelta(RoutePoint pt, double e, double n) {
        return Math.sqrt(Math.pow(e - pt.getEasting(), 2.0) + Math.pow(n - pt.getNorthing(), 2.0));
    }
}