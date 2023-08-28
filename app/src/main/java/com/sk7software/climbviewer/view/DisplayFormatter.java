package com.sk7software.climbviewer.view;

import android.widget.TextView;

import java.text.DecimalFormat;

public class DisplayFormatter {
    public static void setDistanceText(float distance, String unit, TextView txt, boolean limitToZero) {
        int dp = 0;

        if ("km".equals(unit)) {
            distance /= 1000.0f;
            dp = 1;
            if (distance < 10) {
                dp = 2;
            }
        }

        if (limitToZero && distance < 0) {
            distance = 0;
        }

        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(dp);
        txt.setText(df.format(distance) + unit);
    }

    public static void setGradientText(float gradient, TextView txt) {
        int dp = 1;
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(dp);
        txt.setText(df.format(gradient) + "%");
    }

    public static void setTimeText(float time, TextView txt) {
        int dp = 1;
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(dp);
        txt.setText(df.format(time) + "s");
    }

    public static void setFullTimeText(float time, TextView txt) {
        int mins = (int)(time / 60);
        int secs = (int)(time % 60);
        txt.setText(mins + ":" + String.format("%02d", secs));
    }
}
