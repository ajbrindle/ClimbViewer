package com.sk7software.climbviewer;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.volley.AsyncRequestQueue;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.list.ClimbListActivity;
import com.sk7software.climbviewer.model.ClimbAttempt;
import com.sk7software.climbviewer.model.GPXFile;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.TrackFile;
import com.sk7software.climbviewer.network.NetworkRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GPXLoadActivity extends AppCompatActivity {

    private Uri gpxUri;
    private GPXFile gpxFile;
    private TrackFile trkFile;
    private CheckBox[] chkMatchedClimbs;
    private SeekBar seekTolerance;
    private AlertDialog.Builder progressDialogBuilder;
    private Dialog progressDialog;

    private RadioGroup grpType;
    private static final String TAG = GPXLoadActivity.class.getSimpleName();

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpxload);

        Button btnLoad = (Button)findViewById(R.id.btnLoad);
        Button btnLoadSel = (Button)findViewById(R.id.btnCommit);
        Button btnCancel = (Button)findViewById(R.id.btnCancelLoad);
        RadioButton radRoute = (RadioButton) findViewById(R.id.radRoute);
        RadioButton radClimb = (RadioButton) findViewById(R.id.radClimb);
        RadioButton radAttempt = (RadioButton) findViewById(R.id.radAttempt);
        TextView txtTolerance = (TextView)findViewById(R.id.txtTolerance);
        seekTolerance = (SeekBar)findViewById(R.id.seekTolerance);

        progressDialogBuilder = new AlertDialog.Builder(GPXLoadActivity.this);
        progressDialogBuilder.setView(R.layout.progress);

        grpType = (RadioGroup)findViewById(R.id.grpType);

        radAttempt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                txtTolerance.setVisibility(View.VISIBLE);
                seekTolerance.setProgress(1);
                seekTolerance.setVisibility(View.VISIBLE);
            }
        });

        btnLoad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setProgress(true, "Loading file...");
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "Loading file");
                        if (loadFile()) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    showItems();
                                    setProgress(false, null);
                                }
                            });
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    setProgress(false, null);
                                }
                            });
                        }
                    }
                });
            }
        });

        btnLoadSel.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                if (radRoute.isChecked()) {
                    saveFile("route");
                } else if (radClimb.isChecked()) {
                    saveFile("climb");
                } else if (radAttempt.isChecked()) {
                    setProgress(true, "Saving attempts...");
                    executorService.execute(new Runnable() {
                        @Override
                        public void run() {
                            saveClimbAttempts();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    setProgress(false, null);
                                    goToMainActivity();
                                }
                            });
                        }
                    });
                }
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                goToMainActivity();
            }
        });

        Intent gpxIntent = getIntent();
        String action = gpxIntent.getAction();
        String type = gpxIntent.getType();
        gpxUri = (Uri) gpxIntent.getData();
    }

    private boolean loadFile() {
        try {
            InputStream is = getContentResolver().openInputStream(gpxUri);
            trkFile = TrackFile.createFromStream(is);

            if (trkFile != null) {
                // Set all easting and northing coords
                gpxFile = GPXFile.createFromTrackFile(trkFile);
                return true;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error retrieving file: " + e.getMessage());
        }
        return false;
    }

    private void saveFile(String type) {
        NetworkRequest.updateDatabase(type+"s", gpxFile.getRoute());
        goToMainActivity();
    }

    private void saveClimbAttempts() {
        if (chkMatchedClimbs != null && chkMatchedClimbs.length > 0) {
            for (int i=0; i<chkMatchedClimbs.length; i++) {
                if (chkMatchedClimbs[i].isChecked()) {
                    GPXRoute climb = (GPXRoute)chkMatchedClimbs[i].getTag();
                    ClimbAttempt attempt = trkFile.extractAttempt(climb);
                    if (!Database.getInstance().attemptExists(climb.getId(), attempt.getDatetime().atZone(ZoneId.systemDefault()).toEpochSecond())) {
                        Log.d(TAG, "Adding attempt for climb: " + climb.getName());
                        Database.getInstance().addAttempt(attempt, climb.getId());
                    }
                }
            }
        }
    }
    private void showItems() {
        // Reset any checkboxes
        if (chkMatchedClimbs != null && chkMatchedClimbs.length > 0) {
            LinearLayout chkBoxesView = (LinearLayout)findViewById(R.id.chkBoxes);
            for (int i=0; i<chkMatchedClimbs.length; i++) {
                chkBoxesView.removeView(chkMatchedClimbs[i]);
            }
            chkMatchedClimbs = null;
        }

        int type = grpType.getCheckedRadioButtonId();
        ScrollView loadView = (ScrollView) findViewById(R.id.allItems);
        LinearLayout chkBoxesView = (LinearLayout)findViewById(R.id.chkBoxes);
        CheckBox chkName = (CheckBox) findViewById(R.id.chkItem1);

        if (type == R.id.radClimb || type == R.id.radRoute) {
            chkName.setText(gpxFile.getRoute().getName());
            chkName.setChecked(true);
        } else if (type == R.id.radAttempt) {
            chkName.setVisibility(View.GONE);
            int multiplier = seekTolerance.getProgress();
            List<GPXRoute> climbs = trkFile.matchToClimbs(multiplier);
            if (climbs != null && !climbs.isEmpty()) {
                chkMatchedClimbs = new CheckBox[climbs.size()];
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMargins(5,5,0,0);
                int i = 0;
                for (GPXRoute climb : climbs) {
                    chkMatchedClimbs[i] = new CheckBox(ApplicationContextProvider.getContext());
                    chkMatchedClimbs[i].setChecked(true);
                    chkMatchedClimbs[i].setText(climb.getName());
                    chkMatchedClimbs[i].setTag(climb);
                    chkMatchedClimbs[i].setLayoutParams(params);
                    chkBoxesView.addView(chkMatchedClimbs[i]);
                    i++;
                }
            }
        }
        loadView.setVisibility(View.VISIBLE);
    }

    public void setProgress(boolean showProgressDialog, String progressMessage) {
        if (showProgressDialog && progressDialog == null) {
            progressDialog = progressDialogBuilder
                    .setMessage(progressMessage)
                    .create();
            progressDialog.show();
        } else {
            if (progressDialog != null) {
                progressDialog.dismiss();
                progressDialog = null;
            }
        }
    }

    private void goToMainActivity() {
        Intent i = new Intent(this, ClimbChooserActivity.class);
        startActivity(i);
    }
}