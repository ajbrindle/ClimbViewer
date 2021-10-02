package com.sk7software.climbviewer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Switch;

import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.list.ClimbListActivity;
import com.sk7software.climbviewer.model.ClimbAttempt;
import com.sk7software.climbviewer.model.GPXFile;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.model.TrackFile;
import com.sk7software.climbviewer.network.NetworkRequest;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClimbChooserActivity extends AppCompatActivity implements ActivityUpdateInterface {

    private ArrayList<HashMap<String,String>> climbList = new ArrayList<HashMap<String,String>>();
    private ListView climbListView;
    private SimpleAdapter climbListAdapter;
    private int currentClimbId;
    private String currentClimb;
    private int selectedClimb;
    private List<GPXRoute> allClimbs;
    private LocationMonitor monitor;
    private PointF lastPoint;

    private Button showClimbButton;
    private Button monitorButton;
    private Switch mapSwitch;
    private Switch elevationSwitch;
    private Switch pursuitSwitch;

    private AlertDialog.Builder progressDialogBuilder;
    private Dialog progressDialog;

    private static final String TAG = ClimbChooserActivity.class.getSimpleName();
    private static final int LOCATION_PERMISSION = 1;

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_climb_chooser);

        Preferences.init(getApplicationContext());

        // Check location permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION);
        }

        // Set up database
        Database db = Database.getInstance();
        db.getWritableDatabase();
        //GPXFile.addLocalFiles();
        //TrackFile.addLocalFiles();

        // Create progress dialog for use later
        progressDialogBuilder = new AlertDialog.Builder(ClimbChooserActivity.this);
        progressDialogBuilder.setView(R.layout.progress);

        climbListView = (ListView) findViewById(R.id.climbListSel);
        HashMap<String,String> mapMap = new HashMap<String,String>();
        mapMap.put("name", "Selected Climb");
        mapMap.put("value", (currentClimb != null && currentClimb.length() > 0 ? currentClimb : "Tap to choose"));
        climbList.add(mapMap);

        climbListAdapter = new SimpleAdapter(this, climbList, R.layout.list_item,
                new String[]{"name", "value"}, new int[]{R.id.firstLine, R.id.secondLine});

        climbListView.setAdapter(climbListAdapter);
        climbListView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedClimb = position;
                Intent i = new Intent(ApplicationContextProvider.getContext(), ClimbListActivity.class);
                startActivityForResult(i,1);
            }
        });

        showClimbButton = (Button) findViewById(R.id.showClimbBtn);
        showClimbButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(ApplicationContextProvider.getContext(), ClimbViewActivity.class);
                showClimb(i);
            }
        });

        monitorButton = (Button) findViewById(R.id.monitorBtn);
        monitorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (monitor == null) {
                    monitor = new LocationMonitor(ClimbChooserActivity.this);
                    monitorButton.setBackgroundColor(Color.RED);
                    monitorButton.setTextColor(Color.WHITE);
                    monitorButton.setText("MONITORING RIDE");
                } else {
                    monitor.stopListener();
                    monitor = null;
                    monitorButton.setBackgroundColor(getResources().getColor(R.color.purple_700));
                    monitorButton.setTextColor(Color.WHITE);
                    monitorButton.setText("MONITOR RIDE");
                }
            }
        });

        mapSwitch = (Switch)findViewById(R.id.swiMap);
        elevationSwitch = (Switch)findViewById(R.id.swiClimb);
        pursuitSwitch = (Switch)findViewById(R.id.swiPursuit);
        setUpSwitch(mapSwitch, Preferences.PREFERNECE_2D);
        setUpSwitch(elevationSwitch, Preferences.PREFERNECE_ELEVATION);
        setUpSwitch(pursuitSwitch, Preferences.PREFERNECE_PURSUIT);

        boolean mapSet = Preferences.getInstance().getBooleanPreference(Preferences.PREFERNECE_2D);
        mapSwitch.setChecked(mapSet);
        mapSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Preferences.getInstance().addPreference(Preferences.PREFERNECE_2D, isChecked);
            }
        });

        // Start monitoring location
        allClimbs = Arrays.asList(Database.getInstance().getClimbs());
        for (GPXRoute r : allClimbs) {
            Log.d(TAG, "All climbs: " + r.getName() + " [" + r.getPoints().size() + "]" +
                            " (" + r.getPoints().get(0).getEasting() + "," + r.getPoints().get(0).getNorthing() + ") " +
                            " (" + r.getPoints().get(1).getEasting() + "," + r.getPoints().get(1).getNorthing() + ") ");
        }
    }

    @Override
    public void onResume() {
        if (monitor != null && !monitor.isListenerRunning()) {
            monitor.resumeListener();
        }
        super.onResume();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        if (monitor != null && monitor.isListenerRunning()) {
            monitor.stopListener();
        }
        super.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == 1) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Returned to form: " + selectedClimb);

                // Get extra data
                if (data.hasExtra("climb")) {
                    currentClimb = data.getStringExtra("climb");
                    currentClimbId = data.getIntExtra("id", 0);
                    HashMap<String, String> h = new HashMap<String, String>();
                    h = climbList.get(this.selectedClimb);
                    h.put("value", currentClimb);
                    climbListAdapter.notifyDataSetChanged();
                    Log.d(TAG, "Current climb: " + currentClimb + ":" + currentClimbId);
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void locationChanged(RoutePoint point) {
        if (lastPoint == null) {
            lastPoint = new PointF((float)point.getEasting(), (float)point.getNorthing());
            return;
        }

        PointF currentPoint = new PointF((float)point.getEasting(), (float)point.getNorthing());

        // Check if any of the listed climbs have started
        for (GPXRoute climb : allClimbs) {
            PointF start = new PointF((float)climb.getPoints().get(0).getEasting(), (float)climb.getPoints().get(0).getNorthing());
            if (LocationMonitor.pointWithinLineSegment(start, lastPoint, currentPoint)) {
                PointF second = new PointF((float)climb.getPoints().get(1).getEasting(), (float)climb.getPoints().get(1).getNorthing());
                if (LocationMonitor.isRightDirection(second, lastPoint, currentPoint)) {
                    Log.d(TAG, "STARTED CLIMB " + climb.getName());
                    currentClimb = climb.getName();
                    currentClimbId = climb.getId();
                    showClimb(null);
                    break;
                }
            }
        }

        lastPoint = currentPoint;
    }

    private void showClimb(Intent nextIntent) {
        ClimbController.getInstance().loadClimb(Database.getInstance().getClimb(currentClimbId));

        if (nextIntent == null) {
            ClimbController.getInstance().startAttempt();
            ClimbController.getInstance().loadPB();
            nextIntent = getNextScreen();
        }

        if (nextIntent != null) {
            nextIntent.putExtra("id", currentClimbId);
            startActivity(nextIntent);
        }
    }

    private Intent getNextScreen() {
        if (Preferences.getInstance().getBooleanPreference(Preferences.PREFERNECE_2D)) {
            return new Intent(ApplicationContextProvider.getContext(), MapActivity.class);
        } else if (Preferences.getInstance().getBooleanPreference(Preferences.PREFERNECE_ELEVATION)) {
            return new Intent(ApplicationContextProvider.getContext(), FullClimbActivity.class);
        } else if (Preferences.getInstance().getBooleanPreference(Preferences.PREFERNECE_PURSUIT)) {
            return new Intent(ApplicationContextProvider.getContext(), PursuitActivity.class);
        }
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_load_climbs:
                doLoad("climbs");
                return true;
            case R.id.action_load_attempts:
                doLoad("attempts");
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void doLoad(String type) {
        NetworkRequest.fetchGPXFiles(getApplicationContext(), type, ClimbChooserActivity.this, new NetworkRequest.NetworkCallback() {
            @Override
            public void onRequestCompleted(Object callbackData) {
            }

            @Override
            public void onError(Exception e) {
                Log.d(TAG, "Error loading remote files: " + e.getMessage());
            }
        });
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case LOCATION_PERMISSION:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission is granted. Continue the action or workflow
                    // in your app.
                }  else {
                    Log.d(TAG, "Location permission not granted");
                }
                return;
        }
        // Other 'case' lines to check for other
        // permissions this app might request.
    }

    private void setUpSwitch(Switch swi, String pref) {
        boolean prefSet = Preferences.getInstance().getBooleanPreference(pref);
        swi.setChecked(prefSet);
        swi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Preferences.getInstance().addPreference(pref, isChecked);
            }
        });
    }
}