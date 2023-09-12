package com.sk7software.climbviewer.db;

import static java.lang.reflect.Array.getInt;

import android.content.Context;
import android.content.SharedPreferences;

import com.sk7software.climbviewer.ApplicationContextProvider;

public class Preferences {
    public static final String APP_PREFERENCES_KEY = "SK7_CLIMBER_PREFS";
    public static final String PREFERNECE_2D = "PREF_2D";
    public static final String PREFERNECE_ELEVATION = "PREF_ELEVATION";
    public static final String PREFERNECE_PURSUIT = "PREF_PURSUIT";
    public static final String PREFERENCES_INIT = "PREFS_INIT";
    public static final String PREFERENCES_ROUTE_ID = "PREF_ROUTE_ID";
    public static final String PREFERENCES_ROUTE_START_IDX = "PREF_ROUTE_START_IDX";
    public static final String PREFERENCES_SMOOTH_DIST = "PREF_SMOOTH_DIST";
    public static final String PREFERENCES_SCREEN_DELAY_S = "PREF_SCREEN_DELAY_S";
    public static final String PREFERENCES_AUTO_MONITOR_CLIMBS = "PREF_AUTO_MONITOR_CLIBMS";
    public static final String PREFERENCES_CLIMB_ULTRA_TOLERANCE = "PREF_ULTRA_TOLERANCE";
    public static final String PREFERENCES_CLIMB_WARNING = "PREF_CLIMB_WARN";
    public static final String PREFERENCES_CLIMB_SORT_RATING = "PREF_CLIMB_SORT";
    public static final String PREFS_SET = "Y";
    private static Preferences instance;
    private final SharedPreferences prefs;

    private Preferences(Context context) {
        prefs = context.getSharedPreferences(APP_PREFERENCES_KEY, Context.MODE_PRIVATE);
    }

    public synchronized static void init(Context context) {
        if (instance == null) {
            instance = new Preferences(context);
        }
    }

    public static Preferences getInstance() {
        if (instance == null) {
            init(ApplicationContextProvider.getContext());
        }
        return instance;
    }

    public static boolean isPrefsSet() {
        return PREFS_SET.equals(getInstance().getStringPreference(PREFERENCES_INIT));
    }

    public void addPreference(String name, String value) {
        prefs.edit().putString(name, value).commit();
    }

    public void addPreference(String name, int value) {
        prefs.edit().putInt(name, value).commit();
    }

    public void addPreference(String name, boolean value) {
        prefs.edit().putBoolean(name, value).commit();
    }

    public String getStringPreference(String name) {
        return prefs.getString(name, "");
    }

    public int getIntPreference(String name) {
        return getIntPreference(name, 0);
    }

    public int getIntPreference(String name, int defVal) {
        return prefs.getInt(name, defVal);
    }

    public void clearAllPreferences() {
        prefs.edit().clear().commit();
    }

    public static void reset() {
        instance = null;
    }

    public boolean getBooleanPreference(String name) {
        return prefs.getBoolean(name, false);
    }

    public boolean getBooleanPreference(String name, boolean defVal) {
        return prefs.getBoolean(name, defVal);
    }

    public void clearStringPreference(String name) {
        prefs.edit().putString(name, "").commit();
    }

    public void clearIntPreference(String name) {
        prefs.edit().putInt(name, -1).commit();
    }
}
