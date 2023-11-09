package com.sk7software.climbviewer.maps;

public enum MapProvider {
    GOOGLE_MAPS(1),
    MAPBOX(2);

    private int value;

    MapProvider(int value) {
        this.value = value;
    }

    public int getValue() {
        return this.value;
    }

    public static MapProvider fromValue(int value) {
        for (MapProvider p : MapProvider.values()) {
            if (p.getValue() == value) {
                return p;
            }
        }
        return GOOGLE_MAPS;
    }
}
