package com.sk7software.climbviewer;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Point;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.GoogleMap;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.model.GPXFile;
import com.sk7software.climbviewer.model.GPXMetadata;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.model.Track;
import com.sk7software.climbviewer.model.TrackFile;
import com.sk7software.climbviewer.model.TrackSegment;
import com.sk7software.climbviewer.view.ClimbView;
import com.sk7software.climbviewer.view.PlotPoint;
import com.sk7software.climbviewer.view.ScreenController;

import java.util.List;
import java.util.stream.Collectors;

public class ClimbFinderActivity extends AppCompatActivity implements DrawableUpdateInterface {

    private MapFragment map;
    private ClimbView routeClimbView;
    private ClimbView zoomClimbView;
    private int routeId;
    private GPXRoute route;
    private TextView txtClimbRating;
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
        List<GPXRoute> climbsOnRoute = findClimbsOnRoute();
        String climbIds = climbsOnRoute.stream()
                .map(r -> String.valueOf(r.getId()))
                .collect(Collectors.joining(","));

        routeClimbView = findViewById(R.id.climbFinder);
        routeClimbView.setParent(this);
        routeClimbView.setClimb(route, 20);
        routeClimbView.setHeight(viewHeight, false);
        routeClimbView.setTransparency(0xFF);
        routeClimbView.setShowClimbsList(climbIds);
        routeClimbView.invalidate();

        // Zoom view is 80% of the width
        Point screenSize = ScreenController.getScreenSize();
        int zoomPadding = (int)(screenSize.x - (0.8 * (float)screenSize.x)) / 2;
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
                    Log.d(TAG, "X click: " + (int)motionEvent.getX());
                    routeClimbView.setShowGradientAt((int)motionEvent.getX());
                    routeClimbView.setX0((int)motionEvent.getX());
                    zoomClimbView.clearClimbMarker();

                    // Calculate start index on the zoom plot based on where the x position is
                    double startDistance = routeClimbView.getDistanceAtX((int)motionEvent.getX());
                    zoomClimbView.setStartIndex(startDistance);
                    map.setReady(false);
                    layoutResizing = true;
                    zoomPanel.setVisibility(View.VISIBLE);
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
                    Log.d(TAG, "X click: " + (int) motionEvent.getX());
                    zoomClimbView.setShowGradientAt((int) motionEvent.getX());
                    zoomClimbView.setX0((int) motionEvent.getX());
                } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
                    zoomClimbView.setShowGradientAt((int) motionEvent.getX());
                    zoomClimbView.setXN((int) motionEvent.getX());
                    map.plotClimbTrack(zoomClimbView.getMarkedPoints());
                    showClimbRating();
                }
                zoomClimbView.invalidate();
                return true;
            }
        });

        map = (MapFragment) getSupportFragmentManager().findFragmentById(R.id.mapClimbView);
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL, MapFragment.PlotType.ROUTE, false);

        ImageButton findAuto = findViewById(R.id.btnAuto);
        Button defineClimb = findViewById(R.id.btnSetClimb);
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
            }
        });

        defineClimb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showCreateClimbDialog(route.getName() + " climb");
            }
        });
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Climb Name");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(defaultName);
        builder.setView(input);

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

        builder.show();
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
        Database.getInstance().addClimb(climbFile);
    }

    private List<GPXRoute> findClimbsOnRoute() {
        TrackFile dummyFile = new TrackFile();
        Track track = new Track();
        TrackSegment segment = new TrackSegment();
        segment.setPoints(route.getPoints());
        track.setTrackSegment(segment);
        dummyFile.setRoute(track);

        List<GPXRoute> climbsOnRoute = dummyFile.matchToClimbs();

        for (GPXRoute r : climbsOnRoute) {
            Log.d(TAG, "Climbs on route: " + r.getName());
        }

        return climbsOnRoute;
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