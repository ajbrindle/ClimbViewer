package com.sk7software.climbviewer.view;

import android.content.Intent;
import android.graphics.PointF;
import android.util.Log;

import com.sk7software.climbviewer.ApplicationContextProvider;
import com.sk7software.climbviewer.ClimbChooserActivity;
import com.sk7software.climbviewer.ClimbController;
import com.sk7software.climbviewer.FullClimbActivity;
import com.sk7software.climbviewer.MapActivity;
import com.sk7software.climbviewer.PositionMonitor;
import com.sk7software.climbviewer.PursuitActivity;
import com.sk7software.climbviewer.RouteViewActivity;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.model.GPXRoute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScreenController {
    private static ScreenController INSTANCE = null;
    private static final String TAG = ScreenController.class.getSimpleName();

    private ScreenController() {
        super();
    }
    public static ScreenController getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ScreenController();
        }
        return INSTANCE;
    }

    public Intent getNextIntent(Class<?> currentScreen) {
        // For climbs, loop through selected screens
        if (ClimbController.getInstance().isAttemptInProgress()) {
            Log.d(TAG, "Checking next screen: " + currentScreen.getSimpleName());
            List<Class<?>> availableScreens = new ArrayList<>();
            if (Preferences.getInstance().getBooleanPreference(Preferences.PREFERNECE_2D)) {
                availableScreens.add(MapActivity.class);
            }
            if (Preferences.getInstance().getBooleanPreference(Preferences.PREFERNECE_ELEVATION)) {
                availableScreens.add(FullClimbActivity.class);
            }
            if (Preferences.getInstance().getBooleanPreference(Preferences.PREFERNECE_PURSUIT)) {
                availableScreens.add(PursuitActivity.class);
            }

            if (availableScreens.size() == 1) {
                // Just stay on current screen
                return null;
            }

            for (int i=0; i<availableScreens.size(); i++) {
                if (availableScreens.get(i).getSimpleName().equals(currentScreen.getSimpleName())) {
                    Log.d(TAG, "Found current screen");
                    int nextIdx = i+1;
                    if (nextIdx >= availableScreens.size()) {
                        nextIdx = 0;
                    }
                    Log.d(TAG, "Next screen: " + availableScreens.get(nextIdx).getSimpleName());
                    return new Intent(ApplicationContextProvider.getContext(), availableScreens.get(nextIdx));
                }
            }
        } else {
            // Not on climb so go back to route view, if following one, or home screen
            if (PositionMonitor.getInstance().isOnRoute()) {
                Intent i = new Intent(ApplicationContextProvider.getContext(), RouteViewActivity.class);
                i.putExtra("id", PositionMonitor.getInstance().getRouteId());
                i.putExtra("startIdx", PositionMonitor.getInstance().getRouteStartIdx());
                return i;
            } else {
                Intent i = new Intent(ApplicationContextProvider.getContext(), ClimbChooserActivity.class);
                i.putExtra("id", ClimbController.getInstance().getLastClimbId());
                return i;
            }
        }

        return null;
    }
}