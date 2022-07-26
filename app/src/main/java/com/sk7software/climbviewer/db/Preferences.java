package com.sk7software.climbviewer.db;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {
    public static final String APP_PREFERENCES_KEY = "SK7_CLIMBER_PREFS";
    public static final String PREFERNECE_2D = "PREF_2D";
    public static final String PREFERNECE_ELEVATION = "PREF_ELEVATION";
    public static final String PREFERNECE_PURSUIT = "PREF_PURSUIT";
    public static final String PREFERENCES_INIT = "PREFS_INIT";
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
            throw new IllegalStateException("Preferences not initialised");
        } else {
            return instance;
        }
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
        return prefs.getInt(name, 0);
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

    public void clearStringPreference(String name) {
        prefs.edit().putString(name, "").commit();
    }

}
