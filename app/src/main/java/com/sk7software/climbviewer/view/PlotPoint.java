package com.sk7software.climbviewer.view;

import androidx.annotation.NonNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlotPoint {
    private float x;
    private float y;
    private float elevation;
    private float gradient;

    @NonNull
    @Override
    public String toString() {
        return x + "," + y + " [" + elevation + "]" + " " + gradient + "%";
    }
}
