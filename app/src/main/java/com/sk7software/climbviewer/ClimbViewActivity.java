package com.sk7software.climbviewer;

import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.model.AttemptPoint;
import com.sk7software.climbviewer.model.AttemptStats;
import com.sk7software.climbviewer.model.ClimbAttempt;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.view.ClimbView;
import com.sk7software.climbviewer.view.DisplayFormatter;
import com.sk7software.climbviewer.view.TrackView;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public class ClimbViewActivity extends AppCompatActivity {

    private ClimbView elevationView;
    private MapFragment map;
    private int climbId;
    private GPXRoute climb;
    private ClimbAttempt pb;

    private static final String TAG = ClimbViewActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_climb_view);
        getSupportActionBar().hide();

        climbId = getIntent().getIntExtra("id", 0);
        climb = Database.getInstance().getClimb(climbId);

        if (climb != null) {
            TextView txtDist = (TextView) findViewById(R.id.txtTotalDist);
            int numClimbPoints = ClimbController.getInstance().getClimb().getPoints().size();
            DisplayFormatter.setDistanceText(ClimbController.getInstance().getClimb().getPoints().get(numClimbPoints-1).getDistFromStart(),
                    "km", txtDist, true);
        }

        pb = Database.getInstance().getClimbPB(climbId);
        TextView txtPB = (TextView) findViewById(R.id.txtPBTime);

        if (pb != null && pb.getPoints() != null && pb.getPoints().size() > 0) {
            int pbMins = (int)(pb.getDuration() / 60);
            int pbSecs = pb.getDuration() % 60;
            txtPB.setText(pbMins + ":" + pbSecs + "s");
        } else {
            txtPB.setText("-:--s");
        }

        // Display number of attempts
        AttemptStats attempts = Database.getInstance().getLastAttempt(climbId);
        if (attempts != null) {
            TextView txtAttempts = (TextView) findViewById(R.id.txtAttempts);
            txtAttempts.setText("Attempts: " + attempts.getTotal());
        }

        elevationView = (ClimbView) findViewById(R.id.elevationView);
        elevationView.setClimb(climb,false);
        elevationView.setPB(pb);
        setClimbViewHeight();
        elevationView.invalidate();

        map = (MapFragment)getSupportFragmentManager().findFragmentById(R.id.mapView);
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL, MapFragment.PlotType.FULL_CLIMB, false);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void setClimbViewHeight() {
        WindowManager wm = (WindowManager) ApplicationContextProvider.getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        LinearLayout panel = (LinearLayout)findViewById(R.id.panel);

        int s=0;
        int resource = ApplicationContextProvider.getContext().getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resource > 0) {
            s = ApplicationContextProvider.getContext().getResources().getDimensionPixelSize(resource);
        }

        Log.d(TAG, "Setting climb view height: " + (size.y - panel.getHeight() - s) + "/" + size.y + " (" + s + ")");
        elevationView.setHeight(size.y - panel.getHeight() - s);
    }
}