package com.sk7software.climbviewer;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.list.ClimbListActivity;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.view.ScreenController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class ClimbsFragment extends Fragment {

    private final ArrayList<HashMap<String,String>> climbList = new ArrayList<>();
    private ListView climbListView;
    private SimpleAdapter climbListAdapter;
    private int currentClimbId;
    private String currentClimb;
    private List<GPXRoute> allClimbs;
    private TextView txtClimb;
    private Button showClimbButton;
    private Button deleteClimbButton;
    private Button showClimbListButton;
    private MaterialButton monitorButton;
    private List<ValueAnimator> animations;
    private MainActivity mainActivity;

    private static final String TAG = ClimbsFragment.class.getSimpleName();

    public ClimbsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_climbs, container, false);
        mainActivity = (MainActivity)getActivity();

        climbListView = view.findViewById(R.id.climbListSel);
        showClimbListButton = view.findViewById(R.id.btnChange);
        showClimbButton = view.findViewById(R.id.viewClimbBtn);
        deleteClimbButton = view.findViewById(R.id.deleteClimb);
        monitorButton = view.findViewById(R.id.monitorClimbBtn);
        txtClimb = view.findViewById(R.id.txtClimb);

        ActivityResultLauncher<Intent> listResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        handleListResult(result);
                    }
                });

        HashMap<String,String> climbMap = new HashMap<>();
        if (currentClimb != null && currentClimb.length() > 0) {
            climbMap.put("value", currentClimb);
            txtClimb.setText(currentClimb);
            enableClimbButtons(true);
        } else {
            climbMap.put("value", "No climb selected");
            txtClimb.setText("No climb selected");
            enableClimbButtons(false);
        }
        climbList.add(climbMap);

        climbListAdapter = new SimpleAdapter(mainActivity, climbList, R.layout.list_item,
                new String[]{"value"}, new int[]{R.id.firstLine});

        climbListView.setAdapter(climbListAdapter);

        showClimbListButton.setOnClickListener(v -> {
            Intent i = new Intent(mainActivity, ClimbListActivity.class);
            listResultLauncher.launch(i);
        });

        showClimbButton.setOnClickListener(v -> {
            Intent i = new Intent(mainActivity, ClimbViewActivity.class);
            showClimb(currentClimbId, i);
        });

        deleteClimbButton.setOnClickListener(v -> mainActivity.deleteConfirm("climb", currentClimb, currentClimbId));

        monitorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mainActivity.toggleMonitoring(PositionMonitor.MonitorType.CLIMB)) {
                    PositionMonitor.getInstance().setClimbs(allClimbs);
                    animateButtonBackground();
                } else {
                    animations.forEach(a -> a.cancel());
                    animations.clear();
                    monitorButton.setBackgroundColor(ScreenController.getThemeColour(getContext(), R.attr.colorPrimary));
                    monitorButton.setTextSize(24);
                    monitorButton.setIconSize(88);
                }
            }
        });

        reselectClimb();
        allClimbs = Arrays.asList(Database.getInstance().getClimbs());
        return view;
    }

    @Override
    public void onStop() {
        if (animations != null && !animations.isEmpty()) {
            animations.forEach(a -> a.cancel());
        }
        super.onStop();
    }

    private void animateButtonBackground() {
        animations = new ArrayList<>();
        int colorFrom = ScreenController.getThemeColour(getContext(), R.attr.colorPrimary);
        int colorTo = ScreenController.getThemeColour(getContext(), R.attr.colorSecondary);
        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.setDuration(1500); // milliseconds
        colorAnimation.setRepeatMode(ValueAnimator.REVERSE);
        colorAnimation.setRepeatCount(ValueAnimator.INFINITE);
        colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                monitorButton.setBackgroundColor((int) animator.getAnimatedValue());
            }
        });
        animations.add(colorAnimation);
        colorAnimation.start();

        int textSizeFrom = 24;
        int textSizeTo = 8;
        int buttonHeight = monitorButton.getHeight();
        float iconMultiplier = (float)monitorButton.getIconSize()/(float)textSizeFrom;
        ValueAnimator textAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), textSizeFrom, textSizeTo);
        textAnimator.setDuration(2000);
        textAnimator.setRepeatMode(ValueAnimator.REVERSE);
        textAnimator.setRepeatCount(ValueAnimator.INFINITE);
        textAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator valueAnimator) {
                int animatedValue = (int)valueAnimator.getAnimatedValue();
                monitorButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, animatedValue);
                monitorButton.setIconSize((int)((float)animatedValue * iconMultiplier));
                monitorButton.setHeight(buttonHeight);
            }
        });
        animations.add(textAnimator);
        textAnimator.start();
    }
    private void enableClimbButtons(boolean enable) {
        showClimbButton.setEnabled(enable);
        monitorButton.setEnabled(true); // Always available
        deleteClimbButton.setEnabled(enable);
    }

    private void handleListResult(ActivityResult result) {
        // Check which request we're responding to
        int resultCode = result.getResultCode();
        Intent data = result.getData();

        // Make sure the request was successful
        if (resultCode == ClimbListActivity.CLIMB_LIST_OK) {
            currentClimb = data.getStringExtra("climb");
            currentClimbId = data.getIntExtra("id", 0);
            HashMap<String, String> h = new HashMap<String, String>();
            h = climbList.get(0);
            h.put("value", currentClimb);
            climbListAdapter.notifyDataSetChanged();
            txtClimb.setText(currentClimb);
            enableClimbButtons(true);
            Preferences.getInstance().addPreference(Preferences.PREFERENCES_LAST_SELECTED_CLIMB, currentClimbId);
            Log.d(TAG, "Current climb: " + currentClimb + ":" + currentClimbId);
        }
    }
    private void showClimb(int climbId, Intent nextIntent) {
        ClimbController.getInstance().loadClimb(Database.getInstance().getClimb(climbId));

        if (nextIntent == null) {
            ClimbController.getInstance().startAttempt(0);
            ClimbController.getInstance().loadPB();
            nextIntent = getNextScreen();
        }

        if (nextIntent != null) {
            nextIntent.putExtra("id", climbId);
            startActivity(nextIntent);
        }
    }

    private void reselectClimb() {
        int savedClimbId = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_LAST_SELECTED_CLIMB, 0);

        if (savedClimbId > 0) {
            // There is a route to resume so set route id and select in list
            GPXRoute savedClimb = Database.getInstance().getClimb(savedClimbId);

            if (savedClimb != null) {
                currentClimb = savedClimb.getName();
                currentClimbId = savedClimbId;
                HashMap<String, String> h = climbList.get(0);
                h.put("value", currentClimb);
                txtClimb.setText(currentClimb);
                climbListAdapter.notifyDataSetChanged();
                enableClimbButtons(true);
            }
        }
    }

    private Intent getNextScreen() {
        return new Intent(mainActivity, SectionViewActivity.class);
    }

}