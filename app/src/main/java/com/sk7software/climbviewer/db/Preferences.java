package com.sk7software.climbviewer.db;

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
    public static final String PREFERENCES_LAST_SELECTED_ROUTE = "PREF_LAST_ROUTE";
    public static final String PREFERENCES_LAST_SELECTED_CLIMB = "PREF_LAST_CLIMB";
    public static final String PREFERENCES_SELECTED_TAB = "PREF_TAB_SEL";
    public static final String PREFERENCES_ON_CLIMB_ID = "PREF_ON_CLIMB_ID";
    public static final String PREFERENCES_CLIMB_START_TIME = "PREF_START_TIME";
    public static final String PREFERENCES_TRANSPARENCY = "PREF_TRANSPARENCY";
    public static final String PREFERENCES_CLIMB_LABELS = "PREF_CLIMB_LABELS";
    public static final String PREFERENCES_MAP_TYPE = "PREF_MAP_TYPE";
    public static final String PREFERENCES_MAPBOX_2D_MAP = "PREF_MAPBOX_2D";
    public static final String PREFERENCES_MAPBOX_2D_MAP_ID = "PREF_MAPBOX_2D_ID";
    public static final String PREFERENCES_MAPBOX_FOLLOW_MAP = "PREF_MAPBOX_FOLLOW";
    public static final String PREFERENCES_MAPBOX_FOLLOW_MAP_ID = "PREF_MAPBOX_FOLLOW_ID";
    public static final String PREFERENCES_MAPBOX_3D_MAP = "PREF_MAPBOX_3D";
    public static final String PREFERENCES_MAPBOX_3D_MAP_ID = "PREF_MAPBOX_3D_ID";
    public static final String PREFERENCES_DEFAULT_ZOOM = "PREF_ZOON";
    public static final String PREFERENCES_DEFAULT_PITCH = "PREF_PITCH";
    public static final String PREFERENCES_STRAVA_AUTH_TOKEN = "PREF_STRAVA_AUTH";
    public static final String PREFERENCES_STRAVA_REFRESH_TOKEN = "PREF_STRAVA_REFRESH";
    public static final String PREFERENCES_STRAVA_EXPIRY = "PREF_STRAVA_EXP";
    public static final String PREFERENCES_DOWNLOADED_ROUTE = "PREF_DOWNLOADED_ROUTE";
    public static final String PREFERENCES_FILTER_LIST = "PREF_LIST_FILTER";
    public static final String PREFERENCES_SHOW_SONG = "PREF_SHOW_SONG";
    public static final String PREFERENCES_SONG_TIME = "PREF_SONG_TIME";
    public static final String PREFS_SET = "Y";
    private static Preferences instance;
    private final SharedPreferences prefs;

    private Preferences(Context context) {
        prefs = context.getSharedPreferences(APP_PREFERENCES_KEY, Context.MODE_PRIVATE);
    }

    public static synchronized void init(Context context) {
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
    public void addPreference(String name, long value) {
        prefs.edit().putLong(name, value).commit();
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
    public long getLongPreference(String name, long defVal) { return prefs.getLong(name, defVal); }
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
