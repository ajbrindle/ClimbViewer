package com.sk7software.climbviewer.view;

import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.sk7software.climbviewer.ActivityUpdateInterface;
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

    public void showSummary(View panel, int lastClimbId, ActivityUpdateInterface currentScreen) {
        AttemptStats stats = ClimbController.getInstance().getLastAttemptStats(lastClimbId);

        if (stats != null) {
            TextView txtLastDist = panel.findViewById(R.id.txtSegmentDist);
            TextView txtLastTime = panel.findViewById(R.id.txtSegmentTime);
            TextView txtPB = panel.findViewById(R.id.txtSegmentPB);
            TextView txtTitle = panel.findViewById(R.id.txtLastSegment);
            TextView txtNewPB = panel.findViewById(R.id.txtNewPB);

            txtTitle.setText(stats.getName().toUpperCase());
            DisplayFormatter.setDistanceText(stats.getDistanceM(), "km", txtLastDist, true);
            DisplayFormatter.setFullTimeText(stats.getDuration(), txtLastTime);
            DisplayFormatter.setFullTimeText(stats.getPb(), txtPB);

            if (stats.getPos() == 1) {
                txtNewPB.setTextColor(ContextCompat.getColor(ApplicationContextProvider.getContext(), R.color.greenish));
                if (stats.isThisAttemptIsPb()) {
                    txtNewPB.setText("*** NEW PB ***");
                } else if (stats.getTotal() > 1) {
                    txtNewPB.setText("=== PB ===");
                } else {
                    // First attempt, so no PB yet
                    txtPB.setText("-:--s");
                    txtNewPB.setVisibility(View.GONE);
                }
            } else {
                txtNewPB.setTextColor(ContextCompat.getColor(ApplicationContextProvider.getContext(), R.color.reddish));
                txtNewPB.setText(stats.getPos() + "/" + stats.getTotal() + " Attempts");
            }

            panel.setVisibility(View.VISIBLE);
            SummaryPanel.visible = true;
        }

        Handler handler = new Handler();

        Log.d(TAG, "Set task to close completion panel");
        handler.postDelayed(new Runnable() {
            public void run() {
                SummaryPanel.visible = false;
                currentScreen.clearCompletionPanel();
            }
        }, 15000);
    }

    public static void setVisible(boolean visible) {
        SummaryPanel.visible = visible;
    }
    public static boolean isVisible() {
        return SummaryPanel.visible;
    }
}
