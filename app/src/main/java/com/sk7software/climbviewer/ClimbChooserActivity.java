package com.sk7software.climbviewer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.view.MenuCompat;

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
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.geo.GeoConvert;
import com.sk7software.climbviewer.list.ClimbListActivity;
import com.sk7software.climbviewer.list.RouteListActivity;
import com.sk7software.climbviewer.model.AttemptStats;
import com.sk7software.climbviewer.model.BackupData;
import com.sk7software.climbviewer.model.ClimbAttempt;
import com.sk7software.climbviewer.model.GPXFile;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.model.TrackFile;
import com.sk7software.climbviewer.network.NetworkRequest;
import com.sk7software.climbviewer.view.DisplayFormatter;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private ArrayList<HashMap<String,String>> routeList = new ArrayList<HashMap<String,String>>();
    private ListView routeListView;
    private SimpleAdapter routeListAdapter;
    private int currentRouteId;
    private String currentRoute;
    private int selectedRoute;
    private List<GPXRoute> allRoutes;

    private Button showClimbButton;
    private ImageButton deleteClimbButton;
    private Button showRouteButton;
    private Button followRouteButton;
    private ImageButton deleteRouteButton;
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
        db.backup();

        // Create progress dialog for use later
        progressDialogBuilder = new AlertDialog.Builder(ClimbChooserActivity.this);
        progressDialogBuilder.setView(R.layout.progress);

        climbListView = (ListView) findViewById(R.id.climbListSel);
        HashMap<String,String> climbMap = new HashMap<String,String>();
        climbMap.put("name", "Selected Climb");
        climbMap.put("value", (currentClimb != null && currentClimb.length() > 0 ? currentClimb : "Tap to choose"));
        climbList.add(climbMap);

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
                showClimb(currentClimbId, i);
            }
        });

        deleteClimbButton = (ImageButton) findViewById(R.id.deleteClimb);
        deleteClimbButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Database.getInstance().deleteClimb(currentClimbId);
            }
        });

        followRouteButton = (Button) findViewById(R.id.followRouteBtn);
        followRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (toggleMonitoring(PositionMonitor.MonitorType.ROUTE, followRouteButton)) {
                    PositionMonitor.getInstance().setRouteId(currentRouteId);
                    boolean resuming = followRouteButton.getText().toString().startsWith("RESUME");
                    PositionMonitor.getInstance().setTryingToResume(resuming);
                }
            }
        });

        routeListView = (ListView) findViewById(R.id.routeListSel);
        HashMap<String,String> routeMap = new HashMap<String,String>();
        routeMap.put("name", "Selected Route");
        routeMap.put("value", (currentRoute != null && currentRoute.length() > 0 ? currentRoute : "Tap to choose"));
        routeList.add(routeMap);

        routeListAdapter = new SimpleAdapter(this, routeList, R.layout.list_item,
                new String[]{"name", "value"}, new int[]{R.id.firstLine, R.id.secondLine});

        routeListView.setAdapter(routeListAdapter);
        routeListView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedRoute = position;
                stopMonitoring(PositionMonitor.MonitorType.ROUTE, followRouteButton);
                Intent i = new Intent(ApplicationContextProvider.getContext(), RouteListActivity.class);
                startActivityForResult(i,1);
            }
        });

        showRouteButton = (Button) findViewById(R.id.showRouteBtn);
        showRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(ApplicationContextProvider.getContext(), RouteViewActivity.class);
                showRoute(i, 0);
            }
        });

        deleteRouteButton = (ImageButton) findViewById(R.id.deleteRoute);
        deleteRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Database.getInstance().deleteRoute(currentRouteId);
            }
        });

        monitorButton = (Button) findViewById(R.id.monitorBtn);
        monitorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (toggleMonitoring(PositionMonitor.MonitorType.CLIMB, monitorButton)) {
                    PositionMonitor.getInstance().setClimbs(allClimbs);
                    monitorButton.setText("MONITORING RIDE");
                } else {
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
        PositionMonitor.getInstance().stopAllMonitors();
        PositionMonitor.getInstance().setOnRoute(false);
        PositionMonitor.getInstance().setOnClimbId(-1);

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
                    h = climbList.get(this.selectedClimb);
                    h.put("value", currentClimb);
                    climbListAdapter.notifyDataSetChanged();
                    Log.d(TAG, "Current climb: " + currentClimb + ":" + currentClimbId);
                } else if (data.hasExtra("route")) {
                    currentRoute = data.getStringExtra("route");
                    currentRouteId = data.getIntExtra("id", 0);
                    HashMap<String, String> h = new HashMap<String, String>();
                    h = routeList.get(this.selectedRoute);
                    h.put("value", currentRoute);
                    routeListAdapter.notifyDataSetChanged();
                    Log.d(TAG, "Current route: " + currentRoute + ":" + currentRouteId);

                    // Clear preferences (will be set when route starts)
                    Preferences.getInstance().clearIntPreference(Preferences.PREFERENCES_ROUTE_ID);
                    Preferences.getInstance().clearIntPreference(Preferences.PREFERENCES_ROUTE_START_IDX);
                    followRouteButton.setText("FOLLOW ROUTE");
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
            showRoute(intent, PositionMonitor.getInstance().getRouteStartIdx());
        }

        if (PositionMonitor.getInstance().getMonitoring().contains(PositionMonitor.MonitorType.CLIMB) && PositionMonitor.getInstance().getOnClimbId() > 0) {
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

    private void showRoute(Intent nextIntent, int startIdx) {
        GPXRoute rt = Database.getInstance().getRoute(currentRouteId);
        rt.adjustRoute(startIdx);

        ClimbController.getInstance().loadRoute(rt);

        if (nextIntent != null) {
            nextIntent.putExtra("id", currentRouteId);
            nextIntent.putExtra("startIdx", startIdx);
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

    private boolean toggleMonitoring(PositionMonitor.MonitorType type, Button button) {
        if (!PositionMonitor.getInstance().getMonitoring().contains(type)) {
            PositionMonitor.getInstance().doMonitor(type);
            button.setBackgroundColor(Color.RED);
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

    private void stopMonitoring(PositionMonitor.MonitorType type, Button button) {
        PositionMonitor.getInstance().stopMonitor(type);
        button.setBackgroundColor(getResources().getColor(R.color.purple_500));

        if (PositionMonitor.getInstance().getMonitoring().isEmpty()) {
            // Not monitoring anything else, so clear listener and screen flags
            if (monitor != null) {
                monitor.stopListener();
                monitor = null;
            }
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
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

        if (savedRouteId > 0) {
            // There is a route to resume so set route id and select in list
            GPXRoute savedRoute = Database.getInstance().getRoute(savedRouteId);
            currentRoute = savedRoute.getName();
            currentRouteId = savedRouteId;
            HashMap<String, String> h = new HashMap<String, String>();
            h = routeList.get(0);
            h.put("value", currentRoute);
            routeListAdapter.notifyDataSetChanged();
            followRouteButton.setText("RESUME ROUTE");
        }
    }
}