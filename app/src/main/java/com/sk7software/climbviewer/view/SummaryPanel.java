package com.sk7software.climbviewer.view;

import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import com.sk7software.climbviewer.ClimbController;
import com.sk7software.climbviewer.R;
import com.sk7software.climbviewer.model.AttemptStats;

public class SummaryPanel {

    public void showSummary(View panel, int lastClimbId) {
        AttemptStats stats = ClimbController.getInstance().getLastAttemptStats(lastClimbId);

        if (stats != null) {
            TextView txtLastDist = (TextView) panel.findViewById(R.id.txtSegmentDist);
            TextView txtLastTime = (TextView) panel.findViewById(R.id.txtSegmentTime);
            TextView txtPB = (TextView) panel.findViewById(R.id.txtSegmentPB);
            TextView txtNewPB = (TextView) panel.findViewById(R.id.txtNewPB);

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
        }
    }
}
