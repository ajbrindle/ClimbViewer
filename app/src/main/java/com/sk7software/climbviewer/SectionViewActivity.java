package com.sk7software.climbviewer;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.view.ClimbView;
import com.sk7software.climbviewer.view.DisplayFormatter;
import com.sk7software.climbviewer.view.ScreenController;
import com.sk7software.climbviewer.view.SummaryPanel;

import java.util.Date;
import java.util.Map;

public class SectionViewActivity extends AppCompatActivity implements ActivityUpdateInterface {
    // Segment
    private int climbId;
    private GPXRoute climb;
    private ClimbView climbView;

    // Maps (mirror is for pursuit mode)
    private MapFragment map;
    private MapFragment mirrorMap;
    private LocationMonitor monitor;
    private LinearLayout mirrorPanel;

    // Panels
    private TextView txtPanel1;
    private TextView txtPanel2;
    private TextView txtPanel3;
    private TextView txtPanel4;
    private TextView lblPanel1;
    private TextView lblPanel2;
    private TextView lblPanel3;
    private TextView lblPanel4;
    private LinearLayout panel3;
    private LinearLayout panel4;

    // State
    private long loadTime;
    private MapFragment.PlotType plotType;

    private static final String TAG = SectionViewActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_section_view);
        getSupportActionBar().hide();

        loadTime = new Date().getTime();

        climbId = getIntent().getIntExtra("id", 0);
        Log.d(TAG, "Climb id: " + climbId);
        climb = Database.getInstance().getClimb(climbId);

        climbView = (ClimbView) findViewById(R.id.sectionView);
        climbView.setClimb(climb);
        climbView.setTransparency(0xFF);
        setClimbViewHeight();
        climbView.invalidate();

        txtPanel1 = (TextView)findViewById(R.id.txtPanel1);
        txtPanel2 = (TextView)findViewById(R.id.txtPanel2);
        txtPanel3 = (TextView)findViewById(R.id.txtPanel3);
        txtPanel4 = (TextView)findViewById(R.id.txtPanel4);
        lblPanel1 = (TextView)findViewById(R.id.lblPanel1);
        lblPanel2 = (TextView)findViewById(R.id.lblPanel2);
        lblPanel3 = (TextView)findViewById(R.id.lblPanel3);
        lblPanel4 = (TextView)findViewById(R.id.lblPanel4);
        panel3 = (LinearLayout)findViewById(R.id.panel3);
        panel4 = (LinearLayout)findViewById(R.id.panel4);

        // Load first screen type
        plotType = null;
        mirrorPanel = (LinearLayout)findViewById(R.id.mirror);
        map = (MapFragment)getSupportFragmentManager().findFragmentById(R.id.mapView);
        mirrorMap = (MapFragment)getSupportFragmentManager().findFragmentById(R.id.mirrorMap);
        loadNextScreen(true);

        if (ClimbController.getInstance().isAttemptInProgress()) {
            climbView.addPlot(ClimbController.PointType.ATTEMPT);
            monitor = new LocationMonitor(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTime = new Date().getTime();
        if (monitor != null && !monitor.isListenerRunning()) {
            monitor.resumeListener();
        }
    }

    @Override
    protected void onStop() {
        if (monitor != null && monitor.isListenerRunning()) {
            monitor.stopListener();
        }
        super.onStop();
    }

    private void setClimbViewHeight() {
        WindowManager wm = (WindowManager) ApplicationContextProvider.getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        int s=0;
        int resource = ApplicationContextProvider.getContext().getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resource > 0) {
            s = ApplicationContextProvider.getContext().getResources().getDimensionPixelSize(resource);
        }

        Log.d(TAG, "Setting route view height: " + (size.y - s)/3 + "/" + size.y + " (" + s + ")");
        climbView.setHeight((size.y - s)/3, false);
    }

    @Override
    public void onBackPressed() {
        Intent i = new Intent(ApplicationContextProvider.getContext(), ClimbChooserActivity.class);
        startActivity(i);
    }

    @Override
    public void locationChanged(RoutePoint point) {
        if (PositionMonitor.getInstance().getMonitoring().contains(PositionMonitor.MonitorType.CLIMB)) {
            PositionMonitor.getInstance().locationChanged(point);

            if (ClimbController.getInstance().isAttemptInProgress()) {
                if (!climbView.isInitialised()) {
                    return;
                }

                climbView.startUpdating();
                climbView.invalidate();

                updatePanels();

                RoutePoint snappedPos = ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ATTEMPT).getSnappedPosition();
                plotMarkers(snappedPos);

                long now = new Date().getTime();
                if (now - loadTime > ClimbController.DISPLAY_INTERVAL) {
                    // Go to next mode
                    loadNextScreen(false);
                }
            } else {
                showCompletionPanel();
                return;
            }
        }
    }

    private void plotMarkers(RoutePoint snappedPos) {
        if (map != null && snappedPos != null) {
            LatLng climbPoint = new LatLng(snappedPos.getLat(), snappedPos.getLon());
            map.addMarker(climbPoint, ClimbController.PointType.ATTEMPT,
                    ClimbController.PointType.ROUTE.getColor(), true);

            if (plotType == MapFragment.PlotType.PURSUIT) {
                mirrorMap.addMarker(climbPoint, ClimbController.PointType.ATTEMPT,
                        ClimbController.PointType.ROUTE.getColor(), false);
            }

            if (ClimbController.getInstance().getAttempts().get(ClimbController.PointType.PB) != null) {
                RoutePoint pbPos = ClimbController.getInstance().getAttempts().get(ClimbController.PointType.PB).getSnappedPosition();
                map.addMarker(new LatLng(pbPos.getLat(), pbPos.getLon()),
                        ClimbController.PointType.PB, Color.GREEN, true);

                if (plotType == MapFragment.PlotType.PURSUIT) {
                    mirrorMap.addMarker(new LatLng(pbPos.getLat(), pbPos.getLon()),
                            ClimbController.PointType.PB, Color.GREEN, false);

                    if (ClimbController.getInstance().getDistToPB() > 20) {
                        mirrorPanel.setVisibility(View.VISIBLE);
                    } else {
                        mirrorPanel.setVisibility(View.INVISIBLE);
                    }
                }
            }

            // Move camera (and adjust zoom if it is pursuit mode)
            map.moveCamera(snappedPos, false, plotType == MapFragment.PlotType.PURSUIT);

            if (plotType == MapFragment.PlotType.PURSUIT) {
                mirrorMap.moveCamera(snappedPos, true, true);
            }
        }
    }

    private void updatePanels() {
        int numClimbPoints = ClimbController.getInstance().getClimb().getPoints().size();

        switch (plotType) {
            case FULL_CLIMB:
                panel3.setVisibility(View.VISIBLE);
                panel4.setVisibility(View.VISIBLE);
                lblPanel1.setText("GRADIENT");
                lblPanel2.setText("REMAINING");
                lblPanel3.setText("NEXT");
                lblPanel4.setText("DIST LEFT");
                txtPanel1.setTextColor(Color.GRAY);
                txtPanel2.setTextColor(Color.GRAY);
                DisplayFormatter.setGradientText(ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ATTEMPT).getCurrentGradient(), txtPanel1);
                DisplayFormatter.setGradientText(ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ATTEMPT).getNextGradient(), txtPanel3);
                DisplayFormatter.setDistanceText(ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ATTEMPT).getSegmentToGo(), "m", txtPanel2, true);

                DisplayFormatter.setDistanceText(ClimbController.getInstance().getClimb().getPoints().get(numClimbPoints-1).getDistFromStart() -
                        ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ATTEMPT).getDist(), "km", txtPanel4, true);
                break;
            case PURSUIT:
                panel3.setVisibility(View.GONE);
                panel4.setVisibility(View.GONE);
                lblPanel1.setText("DISTANCE");
                lblPanel2.setText("TIME");
                DisplayFormatter.setDistanceText(ClimbController.getInstance().getDistToPB(),
                        "m", txtPanel1, false);
                DisplayFormatter.setTimeText(ClimbController.getInstance().getTimeDiffToPB(), txtPanel2);

                if (ClimbController.getInstance().getDistToPB() < 0) {
                    txtPanel1.setTextColor(Color.RED);
                    txtPanel2.setTextColor(Color.RED);
                } else {
                    txtPanel1.setTextColor(Color.GREEN);
                    txtPanel2.setTextColor(Color.GREEN);
                }
                break;
            case NORMAL:
                panel3.setVisibility(View.GONE);
                panel4.setVisibility(View.GONE);
                lblPanel1.setText("DISTANCE");
                lblPanel2.setText("TO GO");
                txtPanel1.setTextColor(Color.GRAY);
                txtPanel2.setTextColor(Color.GRAY);
                DisplayFormatter.setDistanceText(ClimbController.getInstance().getClimb().getPoints().get(numClimbPoints-1).getDistFromStart(),
                        "km", txtPanel1, true);
                DisplayFormatter.setDistanceText(ClimbController.getInstance().getClimb().getPoints().get(numClimbPoints-1).getDistFromStart() -
                        ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ATTEMPT).getDist(), "km", txtPanel2, true);
                break;
        }
    }

    private void showCompletionPanel() {
        if (SummaryPanel.isVisible()) {
            return;
        }

        RelativeLayout completionPanel = (RelativeLayout)findViewById(R.id.climbCompletePanel);
        SummaryPanel panel = new SummaryPanel();
        panel.showSummary(completionPanel, ClimbController.getInstance().getLastClimbId(), this);
    }

    private void loadNextScreen(boolean firstLoad) {
        MapFragment.PlotType currentType = plotType;
        plotType = ScreenController.getInstance().getNextPlotType(currentType);
        loadTime = new Date().getTime();

        if (!firstLoad && plotType == null) {
            // No change required
            plotType = currentType;
            return;
        }

        if (firstLoad && plotType == null) {
            // Restore to last type and allow to load
            plotType = currentType;
            map.setCentre(ClimbController.getInstance().getLastPointLL());
        }

        if (plotType == MapFragment.PlotType.FULL_CLIMB) {
            mirrorPanel.setVisibility(View.INVISIBLE);
            map.setMapType(GoogleMap.MAP_TYPE_NORMAL, MapFragment.PlotType.FULL_CLIMB, false);
            map.setZoom(18);
            map.setTilt(45);
        } else if (plotType == MapFragment.PlotType.PURSUIT) {
            map.setMapType(GoogleMap.MAP_TYPE_HYBRID, MapFragment.PlotType.PURSUIT, false);
            map.setTilt(67.5f);
            mirrorMap.setMapType(GoogleMap.MAP_TYPE_HYBRID, MapFragment.PlotType.PURSUIT, true);
            mirrorMap.setCentre(ClimbController.getInstance().getLastPointLL());
            mirrorMap.setTilt(67.5f);
        } else if (plotType == MapFragment.PlotType.NORMAL) {
            mirrorPanel.setVisibility(View.INVISIBLE);
            map.setMapType(GoogleMap.MAP_TYPE_NORMAL, MapFragment.PlotType.NORMAL, false);
            map.setTilt(0);
        }

        if (!firstLoad) {
            map.updateMap();
            map.plotTrack();
        }
    }

    @Override
    public void setProgress(boolean showProgressDialog, String progressMessage) {}
}