package com.sk7software.climbviewer;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

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
import com.sk7software.climbviewer.network.NetworkRequest;

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
            //selectedClimb = position;
            Intent i = new Intent(ApplicationContextProvider.getContext(), ClimbListActivity.class);
            startActivityForResult(i,1);
        });

        showClimbButton.setOnClickListener(v -> {
            Intent i = new Intent(ApplicationContextProvider.getContext(), ClimbViewActivity.class);
            showClimb(currentClimbId, i);
        });

        findClimbsButton.setOnClickListener(v -> {
            Intent i = new Intent(ApplicationContextProvider.getContext(), ClimbFinderActivity.class);
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
            Intent i = new Intent(ApplicationContextProvider.getContext(), RouteListActivity.class);
            startActivityForResult(i,1);
        });

        followRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView t = (TextView)findViewById(R.id.labelRoutes);
                if (toggleMonitoring(PositionMonitor.MonitorType.ROUTE, followRouteButton)) {
                    PositionMonitor.getInstance().setRouteId(currentRouteId);
                    boolean autoMonitorClimbs = Preferences.getInstance().getBooleanPreference(Preferences.PREFERENCES_AUTO_MONITOR_CLIMBS, true);
                    if (autoMonitorClimbs) {
                        PositionMonitor.getInstance().doMonitor(PositionMonitor.MonitorType.CLIMB);
                        PositionMonitor.getInstance().setClimbs(findClimbsOnCurrentRoute());
                    }
                    boolean resuming = BTN_RESUME.equals(followRouteButton.getTag());
                    PositionMonitor.getInstance().setTryingToResume(resuming);
                    t.setBackgroundColor(Color.YELLOW);
                    t.setText("Routes - " + (resuming ? "RESUMING" : "FOLLOWING"));
                } else {
                    t.setBackgroundColor(Color.WHITE);
                    t.setText("Routes");
                }
            }
        });

        showRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(ApplicationContextProvider.getContext(), RouteViewActivity.class);
                showRoute(i, 0, null);
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
                if (toggleMonitoring(PositionMonitor.MonitorType.CLIMB, monitorButton)) {
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

        // See if there is a route that needs resuming
        checkRouteResume();
    }

    @Override
    public void onResume() {
        if (monitor != null && !monitor.isListenerRunning()) {
            Log.d(TAG, "Resume location listener");
            monitor.resumeListener();
        }

        // Reset monitoring
        stopAllMonitors();
        PositionMonitor.getInstance().setOnRoute(false);
        PositionMonitor.getInstance().setOnClimbId(-1);

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
                // Get extra data
                if (data.hasExtra("climb")) {
                    currentClimb = data.getStringExtra("climb");
                    currentClimbId = data.getIntExtra("id", 0);
                    HashMap<String, String> h = new HashMap<String, String>();
                    h = climbList.get(0);//this.selectedClimb);
                    h.put("value", currentClimb);
                    climbListAdapter.notifyDataSetChanged();
                    enableClimbButtons(true);
                    Log.d(TAG, "Current climb: " + currentClimb + ":" + currentClimbId);
                } else if (data.hasExtra("route")) {
                    currentRoute = data.getStringExtra("route");
                    currentRouteId = data.getIntExtra("id", 0);
                    HashMap<String, String> h = new HashMap<String, String>();
                    h = routeList.get(0);//this.selectedRoute);
                    h.put("value", currentRoute);
                    routeListAdapter.notifyDataSetChanged();
                    enableRouteButtons(true);
                    Log.d(TAG, "Current route: " + currentRoute + ":" + currentRouteId);

                    // Clear preferences (will be set when route starts)
                    Preferences.getInstance().clearIntPreference(Preferences.PREFERENCES_ROUTE_ID);
                    Preferences.getInstance().clearIntPreference(Preferences.PREFERENCES_ROUTE_START_IDX);
                    followRouteButton.setImageResource(R.drawable.follow_route);
                    followRouteButton.setTag(BTN_FOLLOW);
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void locationChanged(RoutePoint point) {
        PositionMonitor.getInstance().locationChanged(point);

        if (PositionMonitor.getInstance().getMonitoring().contains(PositionMonitor.MonitorType.ROUTE) && PositionMonitor.getInstance().isOnRoute()) {
            // Stop monitoring route, as this will now be managed from the route view
            PositionMonitor.getInstance().stopMonitor(PositionMonitor.MonitorType.ROUTE);
            Intent intent = new Intent(ApplicationContextProvider.getContext(), RouteViewActivity.class);
            intent.putExtra("follow", 1);
            ClimbController.getInstance().startRoute();
            showRoute(intent, PositionMonitor.getInstance().getRouteStartIdx(), point);
        }

        if (PositionMonitor.getInstance().getMonitoring().contains(PositionMonitor.MonitorType.CLIMB) && PositionMonitor.getInstance().getOnClimbId() > 0) {
            if (monitor != null && monitor.isListenerRunning()) {
                monitor.stopListener();
            }
            showClimb(PositionMonitor.getInstance().getOnClimbId(), null);
        }
    }

    private void showClimb(int climbId, Intent nextIntent) {
        ClimbController.getInstance().loadClimb(Database.getInstance().getClimb(climbId));

        if (nextIntent == null) {
            ClimbController.getInstance().startAttempt();
            ClimbController.getInstance().loadPB();
            nextIntent = getNextScreen();
        }

        if (nextIntent != null) {
            nextIntent.putExtra("id", climbId);
            startActivity(nextIntent);
        }
    }

    private void showRoute(Intent nextIntent, int startIdx, RoutePoint point) {
        GPXRoute rt = Database.getInstance().getRoute(currentRouteId);
        rt.adjustRoute(startIdx);
        ClimbController.getInstance().loadRoute(rt);

        if (point != null) {
            // Recalculate matching index
            if (PositionMonitor.getInstance().checkOnRoute(rt, point)) {
                int matchingIdx = PositionMonitor.getInstance().getMatchingSectionIdx();
                ClimbController.getInstance().rejoinRoute(matchingIdx);
            }
        }

        if (nextIntent != null) {
            nextIntent.putExtra("id", currentRouteId);
            nextIntent.putExtra("startIdx", startIdx);
            startActivity(nextIntent);
        }
    }

    private Intent getNextScreen() {
        return new Intent(ApplicationContextProvider.getContext(), SectionViewActivity.class);
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
                doLoad("routes");
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
                NetworkRequest.restoreDB(getApplicationContext(), 1, new NetworkRequest.NetworkCallback() {
                    @Override
                    public void onRequestCompleted(Object callbackData) {
                        Toast.makeText(getApplicationContext(), "Restoring data...", Toast.LENGTH_SHORT).show();
                        String[] rows = callbackData.toString().split("~");
                        Database.getInstance().restore(rows);
                        Toast.makeText(getApplicationContext(), "Database restore complete", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Database restore completed");
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(getApplicationContext(), "Unable to restore data", Toast.LENGTH_LONG).show();
                    }
                });
                return true;
            case R.id.action_settings:
                Intent i = new Intent(ApplicationContextProvider.getContext(), SettingsActivity.class);
                startActivity(i);
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

    private boolean toggleMonitoring(PositionMonitor.MonitorType type, ImageButton button) {
        if (!PositionMonitor.getInstance().getMonitoring().contains(type)) {
            PositionMonitor.getInstance().doMonitor(type);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            if (monitor == null) {
                monitor = new LocationMonitor(ClimbChooserActivity.this);
            }
            return true;
        } else {
            stopMonitoring(type, button);
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
    }

    private void stopMonitoring(PositionMonitor.MonitorType type, ImageButton button) {
        PositionMonitor.getInstance().stopMonitor(type);

        if (PositionMonitor.getInstance().getMonitoring().isEmpty()) {
            // Not monitoring anything else, so clear listener and screen flags
            if (monitor != null) {
                monitor.stopListener();
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

    private void setUpSwitch(Switch swi, String pref) {
        boolean prefSet = Preferences.getInstance().getBooleanPreference(pref);
        swi.setChecked(prefSet);
        swi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Preferences.getInstance().addPreference(pref, isChecked);
            }
        });
    }

    private void checkRouteResume() {
        int savedRouteId = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_ROUTE_ID);
        int startIdx = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_ROUTE_START_IDX, 0);

        if (savedRouteId > 0) {
            // There is a route to resume so set route id and select in list
            GPXRoute savedRoute = Database.getInstance().getRoute(savedRouteId);
            currentRoute = savedRoute.getName();
            currentRouteId = savedRouteId;
            HashMap<String, String> h = routeList.get(0);
            h.put("value", currentRoute);
            routeListAdapter.notifyDataSetChanged();
            followRouteButton.setImageResource(R.drawable.resume);
            followRouteButton.setTag(BTN_RESUME);
            enableRouteButtons(true);
            PositionMonitor.getInstance().setRouteStartIdx(startIdx);
        }
    }

    private void deleteConfirm(String type, String name, int id) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Confirm");

        final TextView message = new TextView(this);
        message.setText("Confirm delete of: " + name);
        builder.setView(message);

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
}