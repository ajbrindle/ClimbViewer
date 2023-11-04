package com.sk7software.climbviewer.maps;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.sk7software.climbviewer.db.Preferences;

import java.util.Map;
import java.util.Objects;

public class MapFragmentFactory {

    public static IMapFragment getProviderMap(AppCompatActivity activity, Map<MapProvider, Integer> fragmentIds) {
        IMapFragment map = null;
        MapProvider provider = MapProvider.fromValue(Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_MAP_TYPE, 1));

        // Force a value
        if (!fragmentIds.containsKey(provider)) {
            provider = fragmentIds.keySet().stream().filter(Objects::nonNull).findFirst().get();
        }

        for (Map.Entry<MapProvider, Integer> id : fragmentIds.entrySet()) {
            if (id.getKey() == provider) {
                map = (IMapFragment) activity.getSupportFragmentManager().findFragmentById(id.getValue().intValue());
            } else {
                Fragment fragment = activity.getSupportFragmentManager().findFragmentById(id.getValue().intValue());
                if (fragment != null) {
                    activity.getSupportFragmentManager().beginTransaction().remove(fragment).commit();
                }

            }
        }

        // Set some defaults
        if (map != null) {
            map.setMapType(MapType.NORMAL, null, false);
        }

        return map;
    }

    public static void removeFragment(AppCompatActivity activity, Map<MapProvider, Integer> fragmentIds) {
        MapProvider provider = MapProvider.fromValue(Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_MAP_TYPE, 1));

        // Force a value
        if (!fragmentIds.containsKey(provider)) {
            provider = fragmentIds.keySet().stream().filter(Objects::nonNull).findFirst().get();
        }

        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(fragmentIds.get(provider));
        if (fragment != null) {
            activity.getSupportFragmentManager().beginTransaction().remove(fragment).commit();
        }
    }

    public static void addFragment(AppCompatActivity activity, Map<MapProvider, Integer> fragmentIds) {
        MapProvider provider = MapProvider.fromValue(Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_MAP_TYPE, 1));

        // Force a value
        if (!fragmentIds.containsKey(provider)) {
            provider = fragmentIds.keySet().stream().filter(Objects::nonNull).findFirst().get();
        }

        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(fragmentIds.get(provider));
        if (fragment != null && !activity.getSupportFragmentManager().findFragmentById(fragmentIds.get(provider)).isAdded()) {
            activity.getSupportFragmentManager().beginTransaction().add(fragment, null).commit();
        }
    }
}
