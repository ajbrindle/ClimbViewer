package com.sk7software.climbviewer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.maps.IMapFragment;
import com.sk7software.climbviewer.maps.MapFragmentFactory;
import com.sk7software.climbviewer.maps.MapProvider;
import com.sk7software.climbviewer.maps.MapType;
import com.sk7software.climbviewer.model.AttemptStats;
import com.sk7software.climbviewer.model.ClimbAttempt;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.view.ClimbView;
import com.sk7software.climbviewer.view.DisplayFormatter;
import com.sk7software.climbviewer.view.PositionMarker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClimbViewActivity extends AppCompatActivity implements DrawableUpdateInterface {

    private ClimbView elevationView;
    private int elevationViewX;
    private IMapFragment map;
    private int climbId;
    private GPXRoute climb;
    private boolean infoShown;
    private EditText txtClimbName;
    private boolean map3dView;
    private int lastMotionX;
    private boolean acceptMoveEvent;

    private static final String TAG = ClimbViewActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_climb_view);
        getSupportActionBar().hide();


        infoShown = false;
        map3dView = false;
        acceptMoveEvent = true;
        climbId = getIntent().getIntExtra("id", 0);
        climb = Database.getInstance().getClimb(climbId);
        ClimbController.getInstance().loadClimb(climb);


        txtClimbName = (EditText) findViewById(R.id.txtClimbName);
        txtClimbName.setText(climb.getName());
        txtClimbName.setEnabled(false);

        elevationView = findViewById(R.id.elevationView);
        elevationView.setParent(this);
        elevationView.setClimb(climb, 20);
        setClimbViewHeight();
        elevationView.invalidate();

        elevationView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN || motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
                    elevationViewX = (int)motionEvent.getX();
                    elevationView.setShowGradientAt((int)motionEvent.getX());
                    LatLng ll = elevationView.getLatLongAtX((int)motionEvent.getX());
                    if (ll != null) {
                        if (map3dView) {
                            if (acceptMoveEvent) {
                                List<LatLng> animationPoints = elevationView.getAllLatLngsBetween(lastMotionX, (int)motionEvent.getX());
                                map.addMarker(animationPoints, ClimbController.PointType.ROUTE, Color.YELLOW, PositionMarker.Size.MEDIUM);
                                RoutePoint mapPt = new RoutePoint();
                                mapPt.setLat(ll.latitude);
                                mapPt.setLon(ll.longitude);
                                float bearing = (float) elevationView.getBearingAtX((int) motionEvent.getX());
                                acceptMoveEvent = false;
                                lastMotionX = (int)motionEvent.getX();
                                map.moveCamera(mapPt, false, false, true, ClimbController.PointType.ROUTE, bearing, ClimbViewActivity.this);
                            }
                        } else {
                            map.showPosition(ll);
                        }
                    }
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    elevationView.setShowGradientAt(-1);
                }
                elevationView.invalidate();
                return true;
            }
        });

        SeekBar smoothDist = (SeekBar)findViewById(R.id.viewSmooth);

        int smoothDistProgress = climb.getSmoothDist();
        if (smoothDistProgress == 0) {
            smoothDistProgress = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_SMOOTH_DIST, 50);
        }
        smoothDist.setProgress(smoothDistProgress);
        smoothDist.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                Database.getInstance().updateSmoothDist(climbId, i);
                climb.setSmoothDist(i);
                elevationView.setClimb(climb, 20);
                elevationView.clearPoints();
                setClimbViewHeight();
                elevationView.invalidate();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        ImageButton btnEdit = (ImageButton)findViewById(R.id.btnEdit);
        ImageButton btnOK = (ImageButton)findViewById(R.id.btnOK);

        btnEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnEdit.setVisibility(View.GONE);
                btnOK.setVisibility(View.VISIBLE);
                txtClimbName.setEnabled(true);
                txtClimbName.selectAll();
            }
        });

        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String newName = txtClimbName.getText().toString();
                if (!"".equals(newName)) {
                    Database.getInstance().updateClimbName(climb.getId(), newName);
                    btnOK.setVisibility(View.GONE);
                    btnEdit.setVisibility(View.VISIBLE);
                    txtClimbName.setEnabled(false);
                }
            }
        });

        Map<MapProvider, Integer> mapFragments = setMapFragmentIds();
        map = MapFragmentFactory.getProviderMap(this, mapFragments);
        map.setMapType(MapType.NORMAL, IMapFragment.PlotType.NORMAL, false);

        Button btn3d = findViewById(R.id.btn3d);
        LinearLayout panelInfo = findViewById(R.id.panelClimbInfo);
        LinearLayout zoomPanel = findViewById(R.id.panelZoom);
        zoomPanel.setVisibility(View.GONE);

        SeekBar zoomLevel = findViewById(R.id.zoomLevel);
        SeekBar pitchLevel = findViewById(R.id.pitchLevel);

        int defaultZoom = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_DEFAULT_ZOOM, -1);
        int defaultPitch = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_DEFAULT_PITCH, -1);

        if (defaultZoom > 0) {
            zoomLevel.setProgress(defaultZoom);
        }
        if (defaultPitch > 0) {
            pitchLevel.setProgress(defaultPitch);
        }

        zoomLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                map.setZoom(seekBar.getProgress());
                map.updateMap();
                moveMapCamera(elevationViewX);
                Preferences.getInstance().addPreference(Preferences.PREFERENCES_DEFAULT_ZOOM, i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        pitchLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                map.setPitch(seekBar.getProgress());
                map.updateMap();
                moveMapCamera(elevationViewX);
                Preferences.getInstance().addPreference(Preferences.PREFERENCES_DEFAULT_PITCH, i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btn3d.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!map3dView) {
                    map3dView = true;
                    acceptMoveEvent = false;
                    btn3d.setText("2D");
                    panelInfo.setVisibility(View.GONE);
                    zoomPanel.setVisibility(View.VISIBLE);
                    map.setMapType(MapType.HYBRID, IMapFragment.PlotType.CLIMB_3D, false);
                    map.setPitch(pitchLevel.getProgress());
                    map.setZoom(zoomLevel.getProgress());
                    map.updateMap();
                    map.plotTrack();
                    moveMapCamera(-1);
                } else {
                    map3dView = false;
                    btn3d.setText("3D");
                    panelInfo.setVisibility(View.VISIBLE);
                    zoomPanel.setVisibility(View.GONE);
                    map.setMapType(MapType.NORMAL, IMapFragment.PlotType.NORMAL, false);
                    map.setTilt(0);
                    map.updateMap();

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    map.plotTrack();
                                }
                            });
                        }
                    }).start();
                }
            }
        });
    }

    private Map<MapProvider, Integer> setMapFragmentIds() {
        Map<MapProvider, Integer> fragmentIds = new HashMap<>();
        fragmentIds.put(MapProvider.GOOGLE_MAPS, R.id.mapView);
        fragmentIds.put(MapProvider.MAPBOX, R.id.mapboxView);
        return fragmentIds;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void updateAfterDraw(boolean resizeable) {
        displayClimbInfo();
    }

    public void acceptMoveEvents() {
        acceptMoveEvent = true;
    }

    private void moveMapCamera(int x) {
        LatLng ll = elevationView.getLatLongAtX(x);
        if (ll != null) {
            RoutePoint mapPt = new RoutePoint();
            mapPt.setLat(ll.latitude);
            mapPt.setLon(ll.longitude);
            float bearing = (float) elevationView.getBearingAtX(x);
            map.moveCamera(mapPt, false, false, false, ClimbController.PointType.ROUTE, bearing, ClimbViewActivity.this);
        }
    }

    private void displayClimbInfo() {
        if (infoShown) return;

        if (climb != null) {
            TextView txtDist = findViewById(R.id.txtSegmentDist);
            int numClimbPoints = ClimbController.getInstance().getClimb().getPoints().size();
            DisplayFormatter.setDistanceText(ClimbController.getInstance().getClimb().getPoints().get(numClimbPoints-1).getDistFromStart(),
                    "km", txtDist, true);

            TextView txtAverageGradient = findViewById(R.id.txtAverageGradient);
            TextView txtMaxGradient = findViewById(R.id.txtMaxGradient);
            TextView txtElevationChange = findViewById(R.id.txtElevationChange);
            TextView txtRating = findViewById(R.id.txtRating);

            double maxElevation = Double.MIN_VALUE;
            double minElevation = Double.MAX_VALUE;
            RoutePoint lastPoint = null;
            for (RoutePoint p : ClimbController.getInstance().getClimb().getPoints()) {
                if (p.getElevation() < minElevation) {
                    minElevation = p.getElevation();
                }
                if (p.getElevation() > maxElevation) {
                    maxElevation = p.getElevation();
                }
            }

            double elevationChange = maxElevation - minElevation;
            double averageGradient = elevationChange / ClimbController.getInstance().getClimb().getPoints().get(numClimbPoints-1).getDistFromStart();
            long rating = ClimbController.getInstance().getClimb().calcRating();

            DisplayFormatter.setDistanceText((float)elevationChange,"m", txtElevationChange, true);
            DisplayFormatter.setGradientText((float)averageGradient*100, txtAverageGradient);
            DisplayFormatter.setGradientText((float)elevationView.getMaxSmoothedGradient(), txtMaxGradient);
            txtRating.setText(String.valueOf(rating));
        }

        ClimbAttempt pb = Database.getInstance().getClimbTime(climbId, true);
        TextView txtPB = findViewById(R.id.txtPB);
        setTimeText(pb, txtPB);

        ClimbAttempt last = Database.getInstance().getClimbTime(climbId, false);
        TextView txtLastAttempt = findViewById(R.id.txtLastAttempt);
        setTimeText(last, txtLastAttempt);

        // Display number of attempts
        List<AttemptStats> attempts = Database.getInstance().getClimbAttemptDurations(climbId);
        AttemptStats stats = new AttemptStats();
        stats.calcStats(attempts);

        TextView txtAttempts = findViewById(R.id.txtAttempts);
        if (attempts != null) {
            txtAttempts.setText(String.valueOf(stats.getTotal()));
        } else {
            txtAttempts.setText("0");
        }
        infoShown = true;
    }

    private void setTimeText(ClimbAttempt attempt, TextView txtTime) {
        if (attempt != null) {
            int attMins = attempt.getDuration() / 60;
            int attSecs = attempt.getDuration() % 60;
            txtTime.setText(attMins + ":" + String.format("%02d", attSecs) + "s");
        } else {
            txtTime.setText("-:--s");
        }
    }

    private void setClimbViewHeight() {
        WindowManager wm = (WindowManager) ClimbViewActivity.this.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        LinearLayout panel = findViewById(R.id.panel);

        int s=0;
        int resource = ClimbViewActivity.this.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resource > 0) {
            s = ClimbViewActivity.this.getResources().getDimensionPixelSize(resource);
        }

        // Set height to 1/2 screen
        int height = (size.y - s) / 2;
        Log.d(TAG, "Setting climb view height: " + height + "/" + size.y + " (" + s + ")");
        elevationView.setHeight(height, false);
    }
}