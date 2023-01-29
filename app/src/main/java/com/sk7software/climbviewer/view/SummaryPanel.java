package com.sk7software.climbviewer.view;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.sk7software.climbviewer.ApplicationContextProvider;
import com.sk7software.climbviewer.ClimbController;
import com.sk7software.climbviewer.R;
import com.sk7software.climbviewer.model.AttemptStats;

public class SummaryPanel {

    private static boolean visible = false;
    private static final String TAG = SummaryPanel.class.getSimpleName();

    public SummaryPanel() {
        SummaryPanel.visible = false;
    }

    public void showSummary(View panel, int lastClimbId, Activity currentScreen) {
        AttemptStats stats = ClimbController.getInstance().getLastAttemptStats(lastClimbId);

        if (stats != null) {
            TextView txtLastDist = panel.findViewById(R.id.txtSegmentDist);
            TextView txtLastTime = panel.findViewById(R.id.txtSegmentTime);
            TextView txtPB = panel.findViewById(R.id.txtSegmentPB);
            TextView txtNewPB = panel.findViewById(R.id.txtNewPB);

            DisplayFormatter.setDistanceText(stats.getDistanceM(), "km", txtLastDist, true);
            DisplayFormatter.setFullTimeText(stats.getDuration(), txtLastTime);
            DisplayFormatter.setFullTimeText(stats.getPb(), txtPB);

            if (stats.getPos() == 1) {
                txtNewPB.setTextColor(Color.GREEN);
                txtNewPB.setText("*** NEW PB ***");
            } else {
                txtNewPB.setTextColor(Color.RED);
                txtNewPB.setText(stats.getPos() + "/" + stats.getTotal() + " Attempts");
            }

            panel.setVisibility(View.VISIBLE);
            SummaryPanel.visible = true;
        }

        Handler handler = new Handler();

        Log.d(TAG, "Set task to return to previous screen");
        handler.postDelayed(new Runnable() {
            public void run() {
                Intent i = ScreenController.getInstance().getNextIntent(currentScreen);
                if (i != null) {
                    SummaryPanel.visible = false;
                    currentScreen.startActivity(i);
                }
            }
        }, 10000);
    }

    public static boolean isVisible() {
        return SummaryPanel.visible;
    }
}
