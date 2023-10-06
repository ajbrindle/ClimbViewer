package com.sk7software.climbviewer;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SimpleAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.view.MenuCompat;

import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.list.ClimbListActivity;
import com.sk7software.climbviewer.list.RouteListActivity;
import com.sk7software.climbviewer.model.BackupData;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.model.Track;
import com.sk7software.climbviewer.model.TrackFile;
import com.sk7software.climbviewer.model.TrackSegment;
import com.sk7software.climbviewer.network.FileList;
import com.sk7software.climbviewer.network.NetworkRequest;
import com.sk7software.climbviewer.view.SummaryPanel;
import com.sk7software.util.aspectlogger.DebugTrace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class ClimbChooserActivity extends AppCompatActivity implements ActivityUpdateInterface {

    private final ArrayList<HashMap<String,String>> climbList = new ArrayList<>();
    private ListView climbListView;
    private SimpleAdapter climbListAdapter;
    private int currentClimbId;
    private String currentClimb;
    private List<GPXRoute> allClimbs;
    private LocationMonitor monitor;
    RelativeLayout completionPanel;

    private final ArrayList<HashMap<String,String>> routeList = new ArrayList<>();
    private ListView routeListView;
    private SimpleAdapter routeListAdapter;
    private int currentRouteId;
    private String currentRoute;
    private List<GPXRoute> allRoutes;

    private ImageButton showClimbButton;
    private ImageButton showRouteListButton;
    private ImageButton findClimbsButton;
    private ImageButton deleteClimbButton;
    private ImageButton showRouteButton;
    private ImageButton followRouteButton;
    private ImageButton deleteRouteButton;
    private ImageButton showClimbListButton;
    private ImageButton monitorButton;
    private Switch mapSwitch;
    private Switch elevationSwitch;
    private Switch pursuitSwitch;

    private AlertDialog.Builder progressDialogBuilder;
    private Dialog progressDialog;

    private static final String TAG = ClimbChooserActivity.class.getSimpleName();
    private static final int LOCATION_PERMISSION = 1;
    private static final String BTN_FOLLOW = "FOLLOW";
    private static final String BTN_RESUME = "RESUME";

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Override
    @DebugTrace
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
        db.backup();

        int lastClimbId = getIntent().getIntExtra("lastClimbId", 0);

        // Create progress dialog for use later
        progressDialogBuilder = new AlertDialog.Builder(ClimbChooserActivity.this);
        progressDialogBuilder.setView(R.layout.progress);

        climbListView = findViewById(R.id.climbListSel);
        showClimbListButton = findViewById(R.id.showClimbListBtn);
        showClimbButton = findViewById(R.id.viewClimbBtn);
        findClimbsButton = findViewById(R.id.findClimbsBtn);
        deleteClimbButton = findViewById(R.id.deleteClimb);
        showRouteListButton = findViewById(R.id.showRouteListBtn);
        followRouteButton = findViewById(R.id.followRouteBtn);
        showRouteButton = findViewById(R.id.viewRouteBtn);
        deleteRouteButton = findViewById(R.id.deleteRoute);
        monitorButton = findViewById(R.id.monitorClimbBtn);
        completionPanel = findViewById(R.id.climbCompletePanel);

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
            enableClimbButtons(true);
        } else {
            climbMap.put("value", "No climb selected");
            enableClimbButtons(false);
        }
        climbList.add(climbMap);

        climbListAdapter = new SimpleAdapter(this, climbList, R.layout.list_item,
                new String[]{"value"}, new int[]{R.id.firstLine});

        climbListView.setAdapter(climbListAdapter);

        showClimbListButton.setOnClickListener(view -> {
            Intent i = new Intent(ClimbChooserActivity.this, ClimbListActivity.class);
            listResultLauncher.launch(i);
        });

        showClimbButton.setOnClickListener(v -> {
            Intent i = new Intent(ClimbChooserActivity.this, ClimbViewActivity.class);
            showClimb(currentClimbId, i);
        });

        findClimbsButton.setOnClickListener(v -> {
            Intent i = new Intent(ClimbChooserActivity.this, ClimbFinderActivity.class);
            showRoute(i, 0, null);
        });

        deleteClimbButton.setOnClickListener(v -> deleteConfirm("climb", currentClimb, currentClimbId));

        routeListView = findViewById(R.id.routeListSel);
        HashMap<String,String> routeMap = new HashMap<String,String>();
        if (currentRoute != null && currentRoute.length() > 0) {
            routeMap.put("value", currentRoute);
            enableRouteButtons(true);
        } else {
            routeMap.put("value", "No route selected");
            enableRouteButtons(false);
        }

        routeList.add(routeMap);

        routeListAdapter = new SimpleAdapter(this, routeList, R.layout.list_item,
                new String[]{"value"}, new int[]{R.id.firstLine});

        routeListView.setAdapter(routeListAdapter);
        showRouteListButton.setOnClickListener(view -> {
            Intent i = new Intent(ClimbChooserActivity.this, RouteListActivity.class);
            listResultLauncher.launch(i);
        });

        followRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView t = (TextView)findViewById(R.id.labelRoutes);
                if (toggleMonitoring(PositionMonitor.MonitorType.ROUTE)) {
                    // Check if new route, restarting or resuming
                    setRouteFollowPreferences();
                    t.setBackgroundColor(Color.YELLOW);
                    t.setText("Routes - FOLLOWING");
                } else {
                    t.setBackgroundColor(Color.WHITE);
                    t.setText("Routes");
                }
            }
        });

        showRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(ClimbChooserActivity.this, RouteViewActivity.class);
                showRoute(i, -1, null);
            }
        });

        deleteRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteConfirm("route", currentRoute, currentRouteId);
            }
        });

        monitorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView t = (TextView)findViewById(R.id.labelClimbs);
                if (toggleMonitoring(PositionMonitor.MonitorType.CLIMB)) {
                    PositionMonitor.getInstance().setClimbs(allClimbs);
                    t.setBackgroundColor(Color.YELLOW);
                    t.setText("Climbs - MONITORING");
                } else {
                    t.setBackgroundColor(Color.WHITE);
                    t.setText("Climbs");
                }
            }
        });

        mapSwitch = findViewById(R.id.swiMap);
        elevationSwitch = findViewById(R.id.swiClimb);
        pursuitSwitch = findViewById(R.id.swiPursuit);
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

        allClimbs = Arrays.asList(Database.getInstance().getClimbs());
        for (GPXRoute r : allClimbs) {
            Log.d(TAG, "All climbs: " + r.getName() + " [" + r.getPoints().size() + "]" +
                            " (" + r.getPoints().get(0).getEasting() + "," + r.getPoints().get(0).getNorthing() + ") " +
                            " (" + r.getPoints().get(1).getEasting() + "," + r.getPoints().get(1).getNorthing() + ") ");
        }

        allRoutes = Arrays.asList(Database.getInstance().getRoutes());
        for (GPXRoute r : allRoutes) {
            Log.d(TAG, "All routes: " + r.getName() + " [" + r.getPoints().size() + "]" +
                    " (" + r.getPoints().get(0).getEasting() + "," + r.getPoints().get(0).getNorthing() + ") " +
                    " (" + r.getPoints().get(1).getEasting() + "," + r.getPoints().get(1).getNorthing() + ") ");
        }

        if (lastClimbId > 0) {
            // Show summary panel before resuming route
            completionPanel = findViewById(R.id.climbCompletePanel);
            SummaryPanel panel = new SummaryPanel();
            panel.showSummary(completionPanel, lastClimbId, this);
        }

        // See if there is a route that needs resuming
        reselectItems();

        Log.d(TAG, "Stop location listener");
        LocationMonitor.stopListener();
    }

    @Override
    public void onResume() {
        Log.d(TAG, "ClimbChooserActivity onResume");
        // Reset monitoring
        stopAllMonitors();
        PositionMonitor.getInstance().setOnRoute(false);
        PositionMonitor.getInstance().setOnClimbId(-1);
        PositionMonitor.getInstance().resetRejoin();
        ClimbController.getInstance().reset(ClimbController.PointType.ROUTE);
        ClimbController.getInstance().reset(ClimbController.PointType.ATTEMPT);

        ViewGroup.LayoutParams routeParams = deleteRouteButton.getLayoutParams();
        ViewGroup.LayoutParams climbParams = deleteClimbButton.getLayoutParams();
        climbParams.height = routeParams.height;
        climbParams.width = routeParams.width;
        deleteClimbButton.setLayoutParams(climbParams);
        super.onResume();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    private void handleListResult(ActivityResult result) {
        // Check which request we're responding to
        int resultCode = result.getResultCode();
        Intent data = result.getData();

            // Make sure the request was successful
        if (resultCode == ClimbListActivity.CLIMB_LIST_OK && data.hasExtra("climb")) {
            currentClimb = data.getStringExtra("climb");
            currentClimbId = data.getIntExtra("id", 0);
            HashMap<String, String> h = new HashMap<String, String>();
            h = climbList.get(0);
            h.put("value", currentClimb);
            climbListAdapter.notifyDataSetChanged();
            enableClimbButtons(true);
            Preferences.getInstance().addPreference(Preferences.PREFERENCES_LAST_SELECTED_CLIMB, currentClimbId);
            Log.d(TAG, "Current climb: " + currentClimb + ":" + currentClimbId);
        } else if (resultCode == RouteListActivity.ROUTE_LIST_OK && data.hasExtra("route")) {
            currentRoute = data.getStringExtra("route");
            currentRouteId = data.getIntExtra("id", 0);
            HashMap<String, String> h = new HashMap<String, String>();
            h = routeList.get(0);
            h.put("value", currentRoute);
            routeListAdapter.notifyDataSetChanged();
            enableRouteButtons(true);
            Preferences.getInstance().addPreference(Preferences.PREFERENCES_LAST_SELECTED_ROUTE, currentRouteId);
            Log.d(TAG, "Current route: " + currentRoute + ":" + currentRouteId);
            followRouteButton.setImageResource(R.drawable.follow_route);
            followRouteButton.setTag(BTN_FOLLOW);
        }
    }

    @Override
    public void locationChanged(RoutePoint point) {
        PositionMonitor.getInstance().locationChanged(point);

        if (PositionMonitor.getInstance().getMonitoring().contains(PositionMonitor.MonitorType.ROUTE) && PositionMonitor.getInstance().isOnRoute()) {
            // Stop monitoring route, as this will now be managed from the route view
            PositionMonitor.getInstance().stopMonitor(PositionMonitor.MonitorType.ROUTE);
            PositionMonitor.getInstance().resetRejoin();
            Intent intent = new Intent(ClimbChooserActivity.this, RouteViewActivity.class);
            intent.putExtra("follow", 1);
            ClimbController.getInstance().startRoute();
            showRoute(intent, PositionMonitor.getInstance().getRouteStartIdx(), point);
        }

        if (PositionMonitor.getInstance().getMonitoring().contains(PositionMonitor.MonitorType.CLIMB) && PositionMonitor.getInstance().getOnClimbId() > 0) {
            showClimb(PositionMonitor.getInstance().getOnClimbId(), null);
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

    @DebugTrace
    private void showRoute(Intent nextIntent, int startIdx, RoutePoint point) {
        int routeId = currentRouteId;
        GPXRoute rt = Database.getInstance().getRoute(routeId);
        if (startIdx >= 0) {
            rt.adjustRoute(startIdx);

            // Amend matching section index
            int matchingIndex = PositionMonitor.getInstance().getMatchingSectionIdx();
            matchingIndex -= startIdx;
            if (matchingIndex < 0) {
                matchingIndex += rt.getPoints().size();
            }

            PositionMonitor.getInstance().setMatchingSectionIdx(matchingIndex);
            ClimbController.getInstance().loadRoute(rt);

            if (point != null) {
                ClimbController.getInstance().rejoinRoute(matchingIndex);
            }
        }

        if (nextIntent != null) {
            nextIntent.putExtra("id", currentRouteId);
            nextIntent.putExtra("startIdx", startIdx);
            startActivity(nextIntent);
        }
    }

    private Intent getNextScreen() {
        return new Intent(ClimbChooserActivity.this, SectionViewActivity.class);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
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
            case R.id.action_load_routes:
                Intent i = new Intent(ClimbChooserActivity.this, MainActivity.class);
                startActivity(i);
//                doLoad("routes");
                return true;
            case R.id.action_backup_db:
                Toast.makeText(getApplicationContext(), "Backing up data...", Toast.LENGTH_SHORT).show();
                BackupData data = new BackupData();
                data.setId(1);
                data.setTableData(Database.getInstance().backup());
                NetworkRequest.backupDB(getApplicationContext(), data, this, new NetworkRequest.NetworkCallback() {
                    @Override
                    public void onRequestCompleted(Object callbackData) {
                        Toast.makeText(getApplicationContext(), "Data backed up successfully", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Database backed up successfully");
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(getApplicationContext(), "Unable to backup data", Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Error backing up database: " + e);
                    }
                });
                return true;
            case R.id.action_restore_db:
                Toast.makeText(getApplicationContext(), "Fetching data...", Toast.LENGTH_SHORT).show();
                setProgress(true, "Resstoring databaase");
                NetworkRequest.restoreDB(getApplicationContext(), 1, new NetworkRequest.NetworkCallback() {
                    @Override
                    public void onRequestCompleted(Object callbackData) {
                        Toast.makeText(getApplicationContext(), "Restoring data...", Toast.LENGTH_SHORT).show();
                        String[] rows = callbackData.toString().split("~");
                        Database.getInstance().restore(rows);
                        setProgress(false, null);
                        Toast.makeText(getApplicationContext(), "Database restore complete", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Database restore completed");
                    }

                    @Override
                    public void onError(Exception e) {
                        setProgress(false, null);
                        Toast.makeText(getApplicationContext(), "Unable to restore data", Toast.LENGTH_LONG).show();
                    }
                });
                return true;
            case R.id.action_settings:
                Intent it = new Intent(ClimbChooserActivity.this, SettingsActivity.class);
                startActivity(it);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        ActivityCompat.finishAffinity(ClimbChooserActivity.this);
    }

    private void doLoad(String type) {
        NetworkRequest.fetchGPXFiles(getApplicationContext(), type, ClimbChooserActivity.this, new NetworkRequest.NetworkCallback() {
            @Override
            public void onRequestCompleted(Object callbackData) {
                if (callbackData == null) return;

                FileList files = (FileList)callbackData;
                if (files.getFiles() != null && !files.getFiles().isEmpty()) {
                    // Load files via GPX loader
                    Intent i = new Intent(ClimbChooserActivity.this, GPXLoadActivity.class);
                    Bundle fileBundle = new Bundle();
                    fileBundle.putParcelable("files", files);
                    i.putExtra("fileList", fileBundle);
                    i.putExtra("gpxType", type);
                    startActivity(i);
                }
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

    private boolean toggleMonitoring(PositionMonitor.MonitorType type) {
        if (!PositionMonitor.getInstance().getMonitoring().contains(type)) {
            PositionMonitor.getInstance().doMonitor(type);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            if (monitor == null) {
                monitor = LocationMonitor.getInstance(ClimbChooserActivity.this);
            }
            return true;
        } else {
            stopMonitoring(type);
            boolean autoMonitorClimbs = Preferences.getInstance().getBooleanPreference(Preferences.PREFERENCES_AUTO_MONITOR_CLIMBS, true);
            if (autoMonitorClimbs) {
                // Stop monitoring climbs as well
                stopMonitoring(PositionMonitor.MonitorType.CLIMB);
            }
        }
        return false;
    }

    private void stopAllMonitors() {
        PositionMonitor.getInstance().stopAllMonitors();
        TextView routesLabel = (TextView) findViewById(R.id.labelRoutes);
        routesLabel.setBackgroundColor(Color.WHITE);
        routesLabel.setText("Routes");
        TextView climbsLabel = (TextView) findViewById(R.id.labelClimbs);
        climbsLabel.setBackgroundColor(Color.WHITE);
        climbsLabel.setText("Climbs");
        LocationMonitor.stopListener();
        monitor = null;
    }

    private void stopMonitoring(PositionMonitor.MonitorType type) {
        PositionMonitor.getInstance().stopMonitor(type);

        if (PositionMonitor.getInstance().getMonitoring().isEmpty()) {
            // Not monitoring anything else, so clear listener and screen flags
            if (monitor != null) {
                LocationMonitor.stopListener();
                monitor = null;
            }
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void enableClimbButtons(boolean enable) {
        showClimbButton.setEnabled(enable);
        monitorButton.setEnabled(true); // Always available
        deleteClimbButton.setEnabled(enable);
    }

    private void enableRouteButtons(boolean enable) {
        findClimbsButton.setEnabled(enable);
        showRouteButton.setEnabled(enable);
        followRouteButton.setEnabled(enable);
        deleteRouteButton.setEnabled(enable);

        if (followRouteButton.getTag() == BTN_RESUME) {
            followRouteButton.setEnabled(true);
        }
    }

    private List<GPXRoute> findClimbsOnCurrentRoute() {
        // Create a track from the current route
        GPXRoute route = Database.getInstance().getRoute(currentRouteId);
        if (route != null) {
            TrackFile tf = new TrackFile();
            Track track = new Track();
            TrackSegment ts = new TrackSegment();
            ts.setPoints(route.getPoints());
            track.setTrackSegment(ts);
            tf.setRoute(track);
            return tf.matchToClimbs();
        }

        return Collections.emptyList();
    }

    private void setRouteFollowPreferences() {
        int savedRouteId = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_ROUTE_ID, -1);
        int startIdx = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_ROUTE_START_IDX, 0);

        if (savedRouteId != currentRouteId) {
            // Different route, so must be restarting
            Preferences.getInstance().clearIntPreference(Preferences.PREFERENCES_ROUTE_ID);
            Preferences.getInstance().clearIntPreference(Preferences.PREFERENCES_ROUTE_START_IDX);
            startFollowing(0);
            return;
        }

        // Pop-up alert to determine whether restarting or resuming
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Follow Route");
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup messageView = (ViewGroup)inflater.inflate(R.layout.alert_message, null);

        TextView message = messageView.findViewById(R.id.txtAlertMessage);
        message.setText("You are already following this route. Restart or resume from current position?");
        builder.setView(messageView);

        builder.setPositiveButton("RESUME", (dialog, which) -> {
            PositionMonitor.getInstance().setRouteStartIdx(startIdx);
            PositionMonitor.getInstance().setTryingToResume(true);
            startFollowing(startIdx);
        });
        builder.setNegativeButton("RESTART", (dialog, which) -> {
            // Clear preferences to allow restart
            Preferences.getInstance().clearIntPreference(Preferences.PREFERENCES_ROUTE_ID);
            Preferences.getInstance().clearIntPreference(Preferences.PREFERENCES_ROUTE_START_IDX);
            PositionMonitor.getInstance().setTryingToResume(false);
            startFollowing(0);
        });
        builder.show();
    }

    private void startFollowing(int startIdx) {
        // Load route
        GPXRoute rt = Database.getInstance().getRoute(currentRouteId);
        if (startIdx >= 0) {
            // Adjust if already following
            rt.adjustRoute(startIdx);
        }
        ClimbController.getInstance().loadRoute(rt);

        PositionMonitor.getInstance().setRouteId(currentRouteId);
        PositionMonitor.getInstance().resetRejoin();
        boolean autoMonitorClimbs = Preferences.getInstance().getBooleanPreference(Preferences.PREFERENCES_AUTO_MONITOR_CLIMBS, true);
        if (autoMonitorClimbs) {
            PositionMonitor.getInstance().doMonitor(PositionMonitor.MonitorType.CLIMB);
            PositionMonitor.getInstance().setClimbs(findClimbsOnCurrentRoute());
        }
    }
    @DebugTrace
    private void setUpSwitch(Switch swi, String pref) {
        boolean prefSet = Preferences.getInstance().getBooleanPreference(pref);
        swi.setChecked(prefSet);
        swi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Preferences.getInstance().addPreference(pref, isChecked);
            }
        });
    }

    private void reselectItems() {
        int savedRouteId = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_LAST_SELECTED_ROUTE, 0);
        int savedClimbId = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_LAST_SELECTED_CLIMB, 0);

        if (savedRouteId > 0) {
            // There is a route to resume so set route id and select in list
            GPXRoute savedRoute = Database.getInstance().getRoute(savedRouteId);
            currentRoute = savedRoute.getName();
            currentRouteId = savedRouteId;
            HashMap<String, String> h = routeList.get(0);
            h.put("value", currentRoute);
            routeListAdapter.notifyDataSetChanged();
            enableRouteButtons(true);
        }

        if (savedClimbId > 0) {
            // There is a route to resume so set route id and select in list
            GPXRoute savedClimb = Database.getInstance().getClimb(savedClimbId);
            currentClimb = savedClimb.getName();
            currentClimbId = savedRouteId;
            HashMap<String, String> h = climbList.get(0);
            h.put("value", currentClimb);
            climbListAdapter.notifyDataSetChanged();
            enableClimbButtons(true);
        }
    }

    private void deleteConfirm(String type, String name, int id) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Confirm");
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup messageView = (ViewGroup)inflater.inflate(R.layout.alert_message, null);

        TextView message = messageView.findViewById(R.id.txtAlertMessage);
        message.setText("Confirm delete of: " + name);
        builder.setView(messageView);

        builder.setPositiveButton("OK", (dialog, which) -> {
            if ("route".equals(type)) {
                Database.getInstance().deleteRoute(id);
            } else {
                Database.getInstance().deleteClimb(id);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @Override
    public void clearCompletionPanel() {
        completionPanel.setVisibility(View.GONE);
    }
}