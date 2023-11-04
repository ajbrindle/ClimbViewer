package com.sk7software.climbviewer;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Point;
import android.os.Bundle;
import android.text.InputType;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.maps.IMapFragment;
import com.sk7software.climbviewer.maps.MapFragmentFactory;
import com.sk7software.climbviewer.maps.MapProvider;
import com.sk7software.climbviewer.model.GPXFile;
import com.sk7software.climbviewer.model.GPXMetadata;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.model.TrackFile;
import com.sk7software.climbviewer.view.ClimbView;
import com.sk7software.climbviewer.view.PlotPoint;
import com.sk7software.climbviewer.view.ScreenController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ClimbFinderActivity extends AppCompatActivity implements DrawableUpdateInterface {

    private IMapFragment map;
    private ClimbView routeClimbView;
    private ClimbView zoomClimbView;
    private int routeId;
    private GPXRoute route;
    private TextView txtClimbRating;
    private Button defineClimb;
    private LinearLayout zoomPanel;
    private boolean layoutResizing;

    private static final String TAG = ClimbFinderActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_climb_finder);
        getSupportActionBar().hide();

        routeId = getIntent().getIntExtra("id", 0);
        route = Database.getInstance().getRoute(routeId);
        ClimbController.getInstance().loadRoute(route);

        int viewHeight = setClimbViewHeight();

        zoomPanel = (LinearLayout)findViewById(R.id.zoomPanel);
        zoomPanel.setVisibility(View.GONE);

        // Find climbs on the route
        List<GPXRoute> climbsOnRoute = TrackFile.findClimbsOnTrackFromPoints(route);
        String climbIds = climbsOnRoute.stream()
                .map(r -> String.valueOf(r.getId()))
                .collect(Collectors.joining(","));

        routeClimbView = findViewById(R.id.climbFinder);
        routeClimbView.setParent(this);
        routeClimbView.setClimb(route, 20);
        routeClimbView.setHeight(viewHeight, false);
        routeClimbView.setTransparency(0xFF);
        routeClimbView.setShowClimbsList(climbIds, true);
        routeClimbView.invalidate();

        // Zoom view is 80% of the width
        Point screenSize = ScreenController.getScreenSize();
        int zoomPadding = (int)(screenSize.x - (0.8 * screenSize.x)) / 2;
        zoomClimbView = findViewById(R.id.climbZoom);
        zoomClimbView.setParent(this);
        zoomClimbView.setClimb(route, zoomPadding);
        zoomClimbView.setHeight(viewHeight, true);
        zoomClimbView.setTransparency(0xFF);

        // Ajdust size of nudge buttons to fit within the padding area
        ImageButton btnNudgeLRight = (ImageButton)findViewById(R.id.btnNudgeLRight);
        ImageButton btnNudgeLLeft = (ImageButton)findViewById(R.id.btnNudgeLLeft);
        btnNudgeLRight.setMaxWidth((int)(zoomPadding * 0.75));
        btnNudgeLRight.setMaxHeight((int)(zoomPadding * 0.75));
        btnNudgeLLeft.setMaxWidth((int)(zoomPadding * 0.75));
        btnNudgeLLeft.setMaxHeight((int)(zoomPadding * 0.75));

        btnNudgeLRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                zoomClimbView.nudgeX0(true);
                map.plotClimbTrack(zoomClimbView.getMarkedPoints());
                zoomClimbView.invalidate();
                showClimbRating();
            }
        });
        btnNudgeLLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                zoomClimbView.nudgeX0(false);
                map.plotClimbTrack(zoomClimbView.getMarkedPoints());
                zoomClimbView.invalidate();
                showClimbRating();
            }
        });

        ImageButton btnNudgeRRight = (ImageButton)findViewById(R.id.btnNudgeRRight);
        ImageButton btnNudgeRLeft = (ImageButton)findViewById(R.id.btnNudgeRLeft);
        btnNudgeRRight.setMaxWidth((int)(zoomPadding * 0.75));
        btnNudgeRRight.setMaxHeight((int)(zoomPadding * 0.75));
        btnNudgeRLeft.setMaxWidth((int)(zoomPadding * 0.75));
        btnNudgeRLeft.setMaxHeight((int)(zoomPadding * 0.75));

        btnNudgeRRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                zoomClimbView.nudgeXN(true);
                map.plotClimbTrack(zoomClimbView.getMarkedPoints());
                zoomClimbView.invalidate();
                showClimbRating();
            }
        });
        btnNudgeRLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                zoomClimbView.nudgeXN(false);
                map.plotClimbTrack(zoomClimbView.getMarkedPoints());
                zoomClimbView.invalidate();
                showClimbRating();
            }
        });

        routeClimbView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN ) {
                    routeClimbView.setShowGradientAt((int)motionEvent.getX());
                    routeClimbView.setX0((int)motionEvent.getX());
                    zoomClimbView.clearClimbMarker();

                    // Calculate start index on the zoom plot based on where the x position is
                    double startDistance = routeClimbView.getDistanceAtX((int)motionEvent.getX());
                    zoomClimbView.setStartIndex(startDistance);
                    map.setReady(false);
                    layoutResizing = true;
                    zoomPanel.setVisibility(View.VISIBLE);
                    defineClimb.setEnabled(false);
                } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
                    routeClimbView.setShowGradientAt((int)motionEvent.getX());
                    routeClimbView.setXN((int)motionEvent.getX());

                    // Calculate distance to be shown on the zoom plot based on where the x position is
                    double endDistance = routeClimbView.getDistanceAtX((int)motionEvent.getX());
                    zoomClimbView.setTrackDistance(endDistance);
                    map.plotClimbTrack(zoomClimbView.getTrackPoints());
                }
                routeClimbView.invalidate();
                zoomClimbView.clearPoints();
                zoomClimbView.setFixedYRange(routeClimbView.getFixedYRange());
                zoomClimbView.invalidate();
                return true;
            }
        });

        zoomClimbView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    zoomClimbView.setShowGradientAt((int) motionEvent.getX());
                    zoomClimbView.setX0((int) motionEvent.getX());
                } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
                    zoomClimbView.setShowGradientAt((int) motionEvent.getX());
                    zoomClimbView.setXN((int) motionEvent.getX());
                    map.plotClimbTrack(zoomClimbView.getMarkedPoints());
                    defineClimb.setEnabled(true);
                    showClimbRating();
                }
                zoomClimbView.invalidate();
                return true;
            }
        });

        map = MapFragmentFactory.getProviderMap(this, setMapFragmentIds());

        ImageButton findAuto = findViewById(R.id.btnAuto);
        defineClimb = findViewById(R.id.btnSetClimb);
        defineClimb.setEnabled(false);
        txtClimbRating = findViewById(R.id.txtRating);

        findAuto.setMaxWidth((int)(zoomPadding * 0.75));
        findAuto.setMaxHeight((int)(zoomPadding * 0.75));
        findAuto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                zoomClimbView.autoX0XN();
                map.plotClimbTrack(zoomClimbView.getMarkedPoints());
                zoomClimbView.invalidate();
                showClimbRating();
                defineClimb.setEnabled(true);
            }
        });

        defineClimb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showCreateClimbDialog(route.getName() + " climb");
            }
        });
    }

    private Map<MapProvider, Integer> setMapFragmentIds() {
        Map<MapProvider, Integer> fragmentIds = new HashMap<>();
        fragmentIds.put(MapProvider.GOOGLE_MAPS, R.id.mapClimbView);
        fragmentIds.put(MapProvider.MAPBOX, R.id.mapboxClimbView);
        return fragmentIds;
    }

    private void showClimbRating() {
        txtClimbRating.setText("Rating: " + zoomClimbView.getClimbRating());
    }

    private int setClimbViewHeight() {
        WindowManager wm = (WindowManager) ApplicationContextProvider.getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        int s=0;
        int resource = ApplicationContextProvider.getContext().getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resource > 0) {
            s = ApplicationContextProvider.getContext().getResources().getDimensionPixelSize(resource);
        }

        return (size.y - s)/3;
    }

    private void showCreateClimbDialog(String defaultName) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(ClimbFinderActivity.this);
        builder.setTitle("Climb Name");
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup messageView = (ViewGroup)inflater.inflate(R.layout.entry_message, null);

        EditText input = messageView.findViewById(R.id.txtEdit);
        builder.setView(messageView);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                createClimb(input.getText().toString());
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        AlertDialog dialog = builder.show();

        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                input.post(new Runnable() {
                    @Override
                    public void run() {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (hasFocus) {
                            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                        } else {
                            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                        }
                    }
                });
            }
        });

        input.setText(defaultName);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.requestFocus();
        input.selectAll();
    }

    private void createClimb(String climbName) {
        GPXFile climbFile = new GPXFile();
        GPXMetadata metadata = new GPXMetadata();

        metadata.setName(climbName);
        climbFile.setMetadata(metadata);

        GPXRoute climbPoints = new GPXRoute();
        for (PlotPoint p : zoomClimbView.getClimbPoints()) {
            RoutePoint rp = new RoutePoint();
            rp.setLat(p.getLocation().latitude);
            rp.setLon(p.getLocation().longitude);
            rp.setElevation(p.getElevation());
            climbPoints.addPoint(rp);
        }

        climbFile.setRoute(climbPoints);
        if (Database.getInstance().addClimb(climbFile)) {
            Toast.makeText(getApplicationContext(), "Climb '" + climbName + "' created", Toast.LENGTH_SHORT).show();
        } else {
            showErrorDialog(climbName);
        }
    }

    private void showErrorDialog(final String name) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(ClimbFinderActivity.this);
        builder.setTitle("Climb not added");
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup messageView = (ViewGroup)inflater.inflate(R.layout.alert_message, null);

        TextView message = messageView.findViewById(R.id.txtAlertMessage);
        message.setText("Climb could not be added as that name already exists: " + name);
        builder.setView(messageView);

        builder.setPositiveButton("OK", (dialog, which) -> {
            showCreateClimbDialog(name);
        });
        builder.show();
    }

    @Override
    public void updateAfterDraw(boolean resizeable) {
        showClimbRating();

        if (resizeable && layoutResizing) {
            map.setReady(true);
            layoutResizing = false;
        }
    }
}