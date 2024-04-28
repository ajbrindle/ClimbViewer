package com.sk7software.climbviewer;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Typeface;
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

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.common.util.Strings;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.list.StravaListActivity;
import com.sk7software.climbviewer.model.ClimbAttempt;
import com.sk7software.climbviewer.model.GPXFile;
import com.sk7software.climbviewer.model.GPXMetadata;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.model.Track;
import com.sk7software.climbviewer.model.TrackFile;
import com.sk7software.climbviewer.model.TrackMetadata;
import com.sk7software.climbviewer.model.TrackSegment;
import com.sk7software.climbviewer.model.strava.StravaActivityStream;
import com.sk7software.climbviewer.network.FileDescription;
import com.sk7software.climbviewer.network.FileList;
import com.sk7software.climbviewer.network.NetworkRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GPXLoadActivity extends AppCompatActivity implements ActivityUpdateInterface {
    private Uri gpxUri;
    private GPXFile gpxFile;
    private TrackFile trkFile;
    private CheckBox[] chkMatchedClimbs;
    private SeekBar seekTolerance;
    private AlertDialog.Builder progressDialogBuilder;
    private Dialog progressDialog;
    private FileList files;
    private RadioGroup grpType;
    private LoadType loadType;
    private GPXType gpxType;
    private LinearLayout panelFileList;
    private static final String TAG = GPXLoadActivity.class.getSimpleName();

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpxload);

        LinearLayout panelChoose = (LinearLayout)findViewById(R.id.panelChoose);
        panelFileList = (LinearLayout)findViewById(R.id.panelFileList);

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

        // Determine if this has been called from the network load
        String type = getIntent().getStringExtra("gpxType");
        if (!Strings.isEmptyOrWhitespace(type)) {
            if (getIntent().getSerializableExtra("stream") != null) {
                panelChoose.setVisibility(View.GONE);
                btnLoad.setVisibility(View.GONE);
                loadEmbeddedData();
            } else {
                // This is a network load
                loadType = LoadType.NETWORK;
                setGPXType(type);
                Bundle fileBundle = getIntent().getBundleExtra("fileList");
                files = fileBundle.getParcelable("files");

                refreshFileList();
                if ("attempts".equals(gpxType)) {
                    txtTolerance.setVisibility(View.VISIBLE);
                    seekTolerance.setProgress(1);
                    seekTolerance.setVisibility(View.VISIBLE);
                }
                panelChoose.setVisibility(View.GONE);
                panelFileList.setVisibility(View.VISIBLE);
            }
        } else {
            // Set up for loading single gpx file
            loadType = LoadType.LOCAL;
            Intent gpxIntent = getIntent();
            String mimeType = gpxIntent.getType();
            Log.d(TAG, "TYPE: " + mimeType);
            gpxUri = gpxIntent.getData();
            grpType = (RadioGroup)findViewById(R.id.grpType);

            radAttempt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    gpxType = GPXType.ATTEMPT;
                    txtTolerance.setVisibility(View.VISIBLE);
                    seekTolerance.setProgress(1);
                    seekTolerance.setVisibility(View.VISIBLE);
                }
            });

            radClimb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    gpxType = GPXType.CLIMB;
                }
            });

            radRoute.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    gpxType = GPXType.ROUTE;
                }
            });
        }

        btnLoad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (loadType == LoadType.LOCAL) {
                    loadLocalFile();
                } else {
                    loadNetworkFiles();
                }
            }
        });

        btnLoadSel.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                if (gpxType == GPXType.ROUTE) {
                    saveFile("route");
                } else if (gpxType == GPXType.CLIMB) {
                    saveFile("climb");
                } else if (gpxType == GPXType.ATTEMPT) {
                    setProgress(true, "Saving attempts...");
                    executorService.execute(new Runnable() {
                        @Override
                        public void run() {
                            saveClimbAttempts();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    setProgress(false, null);
                                    doNextAction();
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
    }

    private void refreshFileList() {
        boolean first = true;
        panelFileList.removeAllViews();
        ScrollView loadView = (ScrollView) findViewById(R.id.allItems);
        loadView.setVisibility(View.GONE);

        for (FileDescription file : files.getFiles()) {
            TextView t = new TextView(ApplicationContextProvider.getContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(5,3,0,0);
            t.setText(file.getName());
            t.setLayoutParams(params);
            if (first) {
                first = false;
                t.setTypeface(null, Typeface.BOLD_ITALIC);
            }
            panelFileList.addView(t);
        }

    }
    @SuppressLint("ResourceType") // Allows checking of nothing selected
    private void loadLocalFile() {
        // Check whether a file type has been selected
        if (grpType.getCheckedRadioButtonId() < 0) return;

        setProgress(true, "Loading file...");
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                if (loadFile()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showItems(gpxType);
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

    private void loadEmbeddedData() {
        setProgress(true, "Loading data...");
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        StravaActivityStream activityStream = (StravaActivityStream) getIntent().getParcelableExtra("stream");
                        String dateTime = getIntent().getStringExtra("dateTime");
                        String fileType = getIntent().getStringExtra("streamType");
                        String name = getIntent().getStringExtra("name");
                        gpxType = (StravaListActivity.StravaType.ACTIVITY.name().equals(fileType) ? GPXType.ATTEMPT : GPXType.ROUTE);
                        loadType = LoadType.EMBEDDED;
                        GPXRoute gpxData = createFromStream(activityStream, dateTime);
                        gpxData.setName("Strava Ride");
                        GPXFile gpx = new GPXFile();
                        gpx.setRoute(gpxData);
                        trkFile = new TrackFile();
                        TrackMetadata m = new TrackMetadata();
                        Track t = new Track();
                        TrackSegment s = new TrackSegment();
                        m.setTime(dateTime);
                        s.setPoints(gpxData.getPoints());
                        t.setName(name != null ? name : gpxData.getName());
                        t.setTrackSegment(s);
                        trkFile.setRoute(t);
                        trkFile.setMetadata(m);
                        trkFile.setGridPoints();
                        gpxFile = GPXFile.createFromTrackFile(trkFile);
                        showItems(gpxType);
                        setProgress(false, null);
                    }
                });
            }
        });
    }

    private void loadNetworkFiles() {
        if (files == null || files.getFiles().isEmpty()) {
            goToMainActivity();
        }

        FileDescription file = files.getFiles().get(0);
        NetworkRequest.fetchGPX(this.getApplicationContext(), gpxType.getValue(), file.getName(), this, new NetworkRequest.NetworkCallback() {
            @Override
            public void onRequestCompleted(Object callbackData) {
                if (callbackData != null) {
                    GPXRoute route = (GPXRoute) callbackData;
                    trkFile = new TrackFile();
                    TrackMetadata m = new TrackMetadata();
                    Track t = new Track();
                    TrackSegment s = new TrackSegment();
                    m.setTime(route.getTime());
                    s.setPoints(route.getPoints());
                    t.setName(route.getName());
                    t.setTrackSegment(s);
                    trkFile.setRoute(t);
                    trkFile.setMetadata(m);
                    trkFile.setGridPoints();
                    gpxFile = GPXFile.createFromTrackFile(trkFile);
                    showItems(gpxType);
                }
            }

            @Override
            public void onError(Exception e) {
                Log.d(TAG, "Error loading: " + file.getName() + " - " + e.getMessage());
            }
        });
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
        setProgress(true, "Saving: " + gpxFile.getMetadata().getName());
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                updateDatabase(type + "s", gpxFile.getRoute(), seekTolerance.getProgress());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setProgress(false, null);
                        doNextAction();
                    }
                });
            }
        });
    }

    private void doNextAction() {
        if (loadType == LoadType.LOCAL || loadType == LoadType.EMBEDDED) {
            goToMainActivity();
        } else {
            files.getFiles().remove(0);
            if (files.getFiles().isEmpty()) {
                goToMainActivity();
            } else {
                refreshFileList();
            }
        }
    }

    private void saveClimbAttempts() {
        if (chkMatchedClimbs != null && chkMatchedClimbs.length > 0) {
            for (int i=0; i<chkMatchedClimbs.length; i++) {
                if (chkMatchedClimbs[i].isChecked()) {
                    GPXRoute climb = (GPXRoute)chkMatchedClimbs[i].getTag();
                    ClimbAttempt attempt = trkFile.extractAttempt(climb, seekTolerance.getProgress());
                    if (!Database.getInstance().attemptExists(climb.getId(), attempt.getDatetime().atZone(ZoneId.systemDefault()).toEpochSecond())) {
                        Log.d(TAG, "Adding attempt for climb: " + climb.getName());
                        Database.getInstance().addAttempt(attempt, climb.getId());
                    }
                }
            }
        }
    }
    private void showItems(GPXType type) {
        // Reset any checkboxes
        if (chkMatchedClimbs != null && chkMatchedClimbs.length > 0) {
            LinearLayout chkBoxesView = (LinearLayout)findViewById(R.id.chkBoxes);
            for (int i=0; i<chkMatchedClimbs.length; i++) {
                chkBoxesView.removeView(chkMatchedClimbs[i]);
            }
            chkMatchedClimbs = null;
        }

        ScrollView loadView = (ScrollView) findViewById(R.id.allItems);
        LinearLayout chkBoxesView = (LinearLayout)findViewById(R.id.chkBoxes);
        CheckBox chkName = (CheckBox) findViewById(R.id.chkItem1);

        if (type == GPXType.ROUTE || type == GPXType.CLIMB) {
            chkName.setText(gpxFile.getRoute().getName());
            chkName.setChecked(true);
        } else if (type == GPXType.ATTEMPT) {
            chkName.setVisibility(View.GONE);
            int multiplier = seekTolerance.getProgress();
            List<GPXRoute> climbs = trkFile.matchToClimbs(multiplier, 0);
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

    @Override
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

    private void setGPXType(String type) {
        switch (type) {
            case "attempts":
                gpxType = GPXType.ATTEMPT;
                break;
            case "routes":
                gpxType = GPXType.ROUTE;
                break;
            case "climbs":
                gpxType = GPXType.CLIMB;
        }
    }

    private GPXRoute createFromStream(StravaActivityStream stream, String activityStartTime) {
        OffsetDateTime now = OffsetDateTime.parse(activityStartTime, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX"));
        GPXRoute attempt = new GPXRoute();
        for (int i=0; i<stream.getLatlng().getSize(); i++) {
            RoutePoint rp = new RoutePoint();
            rp.setLat(stream.getLatlng().getData().get(i).get(0));
            rp.setLon(stream.getLatlng().getData().get(i).get(1));
            rp.setElevation(stream.getAltitude().getData()[i]);
            if (stream.getTime() != null) {
                OffsetDateTime ldt = now.plusSeconds(stream.getTime().getData()[i]);
                rp.setTime(ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")) + "Z");
            }
            attempt.addPoint(rp);
        }
        return attempt;
    }

    private void goToMainActivity() {
        Intent i = new Intent(this, MainActivity.class);
        startActivity(i);
    }

    private static void updateDatabase(String dir, GPXRoute route, int multiplier) {
        if ("climbs".equals(dir)) {
            GPXFile f = new GPXFile();
            GPXMetadata m = new GPXMetadata();
            m.setName(route.getName());
            f.setMetadata(m);
            f.setRoute(route);
            f.getRoute().calcRating();
            Database.getInstance().addClimb(f);
        } else if ("attempts".equals(dir)) {
            TrackFile f = new TrackFile();
            TrackMetadata m = new TrackMetadata();
            Track t = new Track();
            TrackSegment s = new TrackSegment();
            m.setTime(route.getTime());
            s.setPoints(route.getPoints());
            t.setName(route.getName());
            t.setTrackSegment(s);
            f.setRoute(t);
            f.setMetadata(m);
            TrackFile.processTrackFile(f, multiplier);
        } else if ("routes".equals(dir)) {
            GPXFile f = new GPXFile();
            GPXMetadata m = new GPXMetadata();
            m.setName(route.getName());
            f.setMetadata(m);
            f.setRoute(route);
            Database.getInstance().addRoute(f);
        }
    }
    public enum LoadType {
        NETWORK,
        LOCAL,
        EMBEDDED;
    }

    public enum GPXType {
        ATTEMPT("attempts"),
        CLIMB("climbs"),
        ROUTE("routes");

        private String value;
        private GPXType(String value) {
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }
    }
}