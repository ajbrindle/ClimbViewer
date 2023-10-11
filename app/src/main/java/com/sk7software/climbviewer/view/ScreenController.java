package com.sk7software.climbviewer.view;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;

import com.sk7software.climbviewer.ApplicationContextProvider;
import com.sk7software.climbviewer.ClimbController;
import com.sk7software.climbviewer.MainActivity;
import com.sk7software.climbviewer.MapFragment;
import com.sk7software.climbviewer.PositionMonitor;
import com.sk7software.climbviewer.RouteViewActivity;
import com.sk7software.climbviewer.SectionViewActivity;
import com.sk7software.climbviewer.db.Preferences;

import java.util.ArrayList;
import java.util.List;

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

    public MapFragment.PlotType getNextPlotType(MapFragment.PlotType currentType, boolean inPursuit) {
        // For climbs, loop through selected screens
        if (ClimbController.getInstance().isAttemptInProgress()) {
            List<MapFragment.PlotType> availableScreens = new ArrayList<>();
            if (Preferences.getInstance().getBooleanPreference(Preferences.PREFERNECE_2D)) {
                availableScreens.add(MapFragment.PlotType.NORMAL);
            }
            if (Preferences.getInstance().getBooleanPreference(Preferences.PREFERNECE_ELEVATION)) {
                availableScreens.add(MapFragment.PlotType.FULL_CLIMB);
            }
            if (Preferences.getInstance().getBooleanPreference(Preferences.PREFERNECE_PURSUIT) && inPursuit) {
                availableScreens.add(MapFragment.PlotType.PURSUIT);
            }

            if (availableScreens.size() == 1 && currentType != null && currentType == availableScreens.get(0)) {
                // Just stay on current screen
                return null;
            } else if (!availableScreens.isEmpty() && currentType == null) {
                return availableScreens.get(0);
            } else if (availableScreens.size() == 1 && currentType != availableScreens.get(0)) {
                return availableScreens.get(0);
            }

            for (int i = 0; i < availableScreens.size(); i++) {
                if (availableScreens.get(i) == currentType) {
                    int nextIdx = i + 1;
                    if (nextIdx >= availableScreens.size()) {
                        nextIdx = 0;
                    }
                    Log.d(TAG, "Next screen: " + availableScreens.get(nextIdx).name());
                    return availableScreens.get(nextIdx);
                }
            }
        }
        return null;
    }

    public Intent getNextIntent(Activity currentScreen) {
        // For climbs, loop through selected screens
        if (ClimbController.getInstance().isAttemptInProgress()) {
            List<Class<?>> availableScreens = new ArrayList<>();
            if (Preferences.getInstance().getBooleanPreference(Preferences.PREFERNECE_ELEVATION)) {
                availableScreens.add(SectionViewActivity.class);
            }
            if (availableScreens.size() == 1) {
                // Just stay on current screen
                return null;
            }

            for (int i=0; i<availableScreens.size(); i++) {
                if (availableScreens.get(i).getSimpleName().equals(currentScreen.getClass().getSimpleName())) {
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
            if (ClimbController.getInstance().getRoute() != null) {
                Intent i = new Intent(ApplicationContextProvider.getContext(), RouteViewActivity.class);
                i.putExtra("id", PositionMonitor.getInstance().getRouteId());
                i.putExtra("startIdx", PositionMonitor.getInstance().getRouteStartIdx());
                return i;
            } else {
                Intent i = new Intent(ApplicationContextProvider.getContext(), MainActivity.class);
                i.putExtra("id", ClimbController.getInstance().getLastClimbId());
                return i;
            }
        }

        return null;
    }

    public static Point getScreenSize() {
        // Find screen width
        WindowManager wm = (WindowManager) ApplicationContextProvider.getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size;
    }

    public static int getThemeColour(Context ctxt, int attrId) {
        TypedValue typedValue = new TypedValue();
        ctxt.getTheme().resolveAttribute(attrId, typedValue, true);
        return ContextCompat.getColor(ctxt, typedValue.resourceId);
    }
}