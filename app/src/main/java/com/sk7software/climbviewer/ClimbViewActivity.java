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
        //setDistances();

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

            /* Plot location on map
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
            }*/
        }

        lastPoint = currentPoint;
    }





}