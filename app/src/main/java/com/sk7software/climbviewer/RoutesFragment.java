package com.sk7software.climbviewer;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.list.RouteListActivity;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.model.Track;
import com.sk7software.climbviewer.model.TrackFile;
import com.sk7software.climbviewer.model.TrackSegment;
import com.sk7software.climbviewer.view.ScreenController;
import com.sk7software.util.aspectlogger.DebugTrace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class RoutesFragment extends Fragment {

    private Button showRouteListButton;
    private Button showRouteButton;
    private MaterialButton followRouteButton;
    private Button deleteRouteButton;
    private Button findClimbsButton;
    private final ArrayList<HashMap<String,String>> routeList = new ArrayList<>();
    private ListView routeListView;
    private TextView txtRoute;
    private SimpleAdapter routeListAdapter;
    private int currentRouteId;
    private String currentRoute;
    private List<GPXRoute> allRoutes;
    private List<ValueAnimator> animations;
    private MainActivity mainActivity;

    private static final String TAG = RoutesFragment.class.getSimpleName();
    public RoutesFragment() {
        // Required empty public constructor
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_routes, container, false);
        mainActivity = (MainActivity)getActivity();

        showRouteListButton = view.findViewById(R.id.btnChange);
        followRouteButton = view.findViewById(R.id.followRouteBtn);
        showRouteButton = view.findViewById(R.id.viewRouteBtn);
        deleteRouteButton = view.findViewById(R.id.deleteRoute);
        findClimbsButton = view.findViewById(R.id.findClimbsBtn);
        txtRoute = view.findViewById(R.id.txtRoute);

        ActivityResultLauncher<Intent> listResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        handleListResult(result);
                    }
                });

        routeListView = view.findViewById(R.id.routeListSel);
        HashMap<String,String> routeMap = new HashMap<String,String>();
        if (currentRoute != null && currentRoute.length() > 0) {
            routeMap.put("value", currentRoute);
            txtRoute.setText(currentRoute);
            enableRouteButtons(true);
        } else {
            routeMap.put("value", "No route selected");
            txtRoute.setText("No route selected");
            enableRouteButtons(false);
        }

        routeList.add(routeMap);

        routeListAdapter = new SimpleAdapter(mainActivity, routeList, R.layout.list_item,
                new String[]{"value"}, new int[]{R.id.firstLine});

        routeListView.setAdapter(routeListAdapter);
        showRouteListButton.setOnClickListener(vw -> {
            Intent i = new Intent(mainActivity, RouteListActivity.class);
            listResultLauncher.launch(i);
        });

        followRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mainActivity.toggleMonitoring(PositionMonitor.MonitorType.ROUTE)) {
                    // Check if new route, restarting or resuming
                    setRouteFollowPreferences();
                    animateButtonBackground();
                } else {
                    animations.forEach(a -> a.cancel());
                    animations.clear();
                    followRouteButton.setBackgroundColor(ScreenController.getThemeColour(getContext(), R.attr.colorPrimary));
                    followRouteButton.setTextSize(24);
                    followRouteButton.setIconSize(88);
                }
            }
        });

        showRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(mainActivity, RouteViewActivity.class);
                showRoute(i, -1, null);
            }
        });

        deleteRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mainActivity.deleteConfirm("route", currentRoute, currentRouteId);
            }
        });

        findClimbsButton.setOnClickListener(v -> {
            Intent i = new Intent(mainActivity, ClimbFinderActivity.class);
            showRoute(i, 0, null);
        });

        reselectRoute();
        allRoutes = Arrays.asList(Database.getInstance().getRoutes());
        return view;
    }

    @Override
    public void onStop() {
        if (animations != null && !animations.isEmpty()) {
            animations.forEach(a -> a.cancel());
        }
        super.onStop();
    }

    private void animateButtonBackground() {
        animations = new ArrayList<>();
        int colorFrom = ScreenController.getThemeColour(getContext(), R.attr.colorPrimary);
        int colorTo = ScreenController.getThemeColour(getContext(), R.attr.colorSecondary);
        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.setDuration(1500); // milliseconds
        colorAnimation.setRepeatMode(ValueAnimator.REVERSE);
        colorAnimation.setRepeatCount(ValueAnimator.INFINITE);
        colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                followRouteButton.setBackgroundColor((int) animator.getAnimatedValue());
            }
        });
        animations.add(colorAnimation);
        colorAnimation.start();

        int textSizeFrom = 24;
        int textSizeTo = 8;
        int buttonHeight = followRouteButton.getHeight();
        float iconMultiplier = (float)followRouteButton.getIconSize()/(float)textSizeFrom;
        ValueAnimator textAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), textSizeFrom, textSizeTo);
        textAnimator.setDuration(2000);
        textAnimator.setRepeatMode(ValueAnimator.REVERSE);
        textAnimator.setRepeatCount(ValueAnimator.INFINITE);
        textAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator valueAnimator) {
                int animatedValue = (int)valueAnimator.getAnimatedValue();
                followRouteButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, animatedValue);
                followRouteButton.setIconSize((int)((float)animatedValue * iconMultiplier));
                followRouteButton.setHeight(buttonHeight);
            }
        });
        animations.add(textAnimator);
        textAnimator.start();
    }
    private void handleListResult(ActivityResult result) {
        // Check which request we're responding to
        int resultCode = result.getResultCode();
        Intent data = result.getData();

        // Make sure the request was successful
        if (resultCode == RouteListActivity.ROUTE_LIST_OK && data.hasExtra("route")) {
            currentRoute = data.getStringExtra("route");
            currentRouteId = data.getIntExtra("id", 0);
            HashMap<String, String> h = new HashMap<String, String>();
            h = routeList.get(0);
            h.put("value", currentRoute);
            routeListAdapter.notifyDataSetChanged();
            txtRoute.setText(currentRoute);
            enableRouteButtons(true);
            Preferences.getInstance().addPreference(Preferences.PREFERENCES_LAST_SELECTED_ROUTE, currentRouteId);
            Log.d(TAG, "Current route: " + currentRoute + ":" + currentRouteId);
        }
    }

    private void enableRouteButtons(boolean enable) {
        findClimbsButton.setEnabled(enable);
        showRouteButton.setEnabled(enable);
        followRouteButton.setEnabled(enable);
        deleteRouteButton.setEnabled(enable);
    }

    private void reselectRoute() {
        int savedRouteId = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_LAST_SELECTED_ROUTE, 0);

        if (savedRouteId > 0) {
            // There is a route to resume so set route id and select in list
            GPXRoute savedRoute = Database.getInstance().getRoute(savedRouteId);

            if (savedRoute != null) {
                currentRoute = savedRoute.getName();
                currentRouteId = savedRouteId;
                HashMap<String, String> h = routeList.get(0);
                h.put("value", currentRoute);
                txtRoute.setText(currentRoute);
                routeListAdapter.notifyDataSetChanged();
                enableRouteButtons(true);
            }
        }
    }

    @DebugTrace
    private void showRoute(Intent nextIntent, int startIdx, RoutePoint point) {
        GPXRoute rt = Database.getInstance().getRoute(currentRouteId);
        if (startIdx >= 0) {
            rt.adjustRoute(startIdx);

            // Amend matching section index
            int matchingIndex = PositionMonitor.getInstance().getMatchingSectionIdx();
            matchingIndex -= startIdx;
            if (matchingIndex < 0) {
                matchingIndex += rt.getPoints().size();
            }

            PositionMonitor.getInstance().setMatchingSectionIdx(matchingIndex);
            ClimbController.getInstance().loadRoute(rt);

            if (point != null) {
                ClimbController.getInstance().rejoinRoute(matchingIndex);
            }
        }

        if (nextIntent != null) {
            nextIntent.putExtra("id", currentRouteId);
            nextIntent.putExtra("startIdx", startIdx);
            startActivity(nextIntent);
        }
    }

    private void setRouteFollowPreferences() {
        int savedRouteId = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_ROUTE_ID, -1);
        int startIdx = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_ROUTE_START_IDX, 0);

        if (savedRouteId != currentRouteId) {
            // Different route, so must be restarting
            Preferences.getInstance().clearIntPreference(Preferences.PREFERENCES_ROUTE_ID);
            Preferences.getInstance().clearIntPreference(Preferences.PREFERENCES_ROUTE_START_IDX);
            startFollowing(0);
            return;
        }

        // Pop-up alert to determine whether restarting or resuming
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(mainActivity);
        builder.setTitle("Follow Route");
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup messageView = (ViewGroup)inflater.inflate(R.layout.alert_message, null);

        TextView message = messageView.findViewById(R.id.txtAlertMessage);
        message.setText("You are already following this route. Restart or resume from current position?");
        builder.setView(messageView);

        builder.setPositiveButton("RESUME", (dialog, which) -> {
            PositionMonitor.getInstance().setRouteStartIdx(startIdx);
            PositionMonitor.getInstance().setTryingToResume(true);
            startFollowing(startIdx);
        });
        builder.setNegativeButton("RESTART", (dialog, which) -> {
            // Clear preferences to allow restart
            Preferences.getInstance().clearIntPreference(Preferences.PREFERENCES_ROUTE_ID);
            Preferences.getInstance().clearIntPreference(Preferences.PREFERENCES_ROUTE_START_IDX);
            PositionMonitor.getInstance().setTryingToResume(false);
            startFollowing(0);
        });
        builder.show();
    }

    private void startFollowing(int startIdx) {
        // Load route
        GPXRoute rt = Database.getInstance().getRoute(currentRouteId);
        if (startIdx >= 0) {
            // Adjust if already following
            rt.adjustRoute(startIdx);
        }
        ClimbController.getInstance().loadRoute(rt);

        PositionMonitor.getInstance().setRouteId(currentRouteId);
        PositionMonitor.getInstance().resetRejoin();
        boolean autoMonitorClimbs = Preferences.getInstance().getBooleanPreference(Preferences.PREFERENCES_AUTO_MONITOR_CLIMBS, true);
        if (autoMonitorClimbs) {
            PositionMonitor.getInstance().doMonitor(PositionMonitor.MonitorType.CLIMB);
            PositionMonitor.getInstance().setClimbs(findClimbsOnCurrentRoute(startIdx));
        }
    }

    private List<GPXRoute> findClimbsOnCurrentRoute(int startIdx) {
        // Create a track from the current route
        GPXRoute route = Database.getInstance().getRoute(currentRouteId);
        if (route != null) {
            TrackFile tf = new TrackFile();
            Track track = new Track();
            TrackSegment ts = new TrackSegment();
            ts.setPoints(route.getPoints());
            track.setTrackSegment(ts);
            tf.setRoute(track);
            return tf.matchToClimbsAfterIndex(startIdx);
        }

        return Collections.emptyList();
    }
}