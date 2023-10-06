package com.sk7software.climbviewer;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.view.MenuCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.tabs.TabLayout;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.model.BackupData;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.network.FileList;
import com.sk7software.climbviewer.network.NetworkRequest;
import com.sk7software.climbviewer.view.SummaryPanel;
import com.sk7software.util.aspectlogger.DebugTrace;

public class MainActivity extends AppCompatActivity implements ActivityUpdateInterface {

    private TabLayout tabLayout;
    private ViewPager2 viewPager2;
    private MainViewPager mainViewPager;
    private SwitchMaterial mapSwitch;
    private SwitchMaterial elevationSwitch;
    private SwitchMaterial pursuitSwitch;
    private LocationMonitor monitor;
    RelativeLayout completionPanel;
    private AlertDialog.Builder progressDialogBuilder;
    private Dialog progressDialog;

    private static final int LOCATION_PERMISSION = 1;
    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Preferences.init(ApplicationContextProvider.getContext());

        // Check location permissions
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION);
        }

        // Set up database
        Database db = Database.getInstance();
        db.getWritableDatabase();
        db.backup();

        int lastClimbId = getIntent().getIntExtra("lastClimbId", 0);

        tabLayout = findViewById(R.id.tabs);
        viewPager2 = findViewById(R.id.view_pager);
        mainViewPager = new MainViewPager(this);
        viewPager2.setAdapter(mainViewPager);
        completionPanel = findViewById(R.id.climbCompletePanel);

        // Create progress dialog for use later
        progressDialogBuilder = new AlertDialog.Builder(MainActivity.this);
        progressDialogBuilder.setView(R.layout.progress);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager2.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        viewPager2.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                tabLayout.getTabAt(position).select();
                Preferences.getInstance().addPreference(Preferences.PREFERENCES_SELECTED_TAB, position);
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

        if (lastClimbId > 0) {
            // Show summary panel before resuming route
            SummaryPanel panel = new SummaryPanel();
            panel.showSummary(completionPanel, lastClimbId, this);
        }

        Log.d(TAG, "Stop location listener");
        LocationMonitor.stopListener();
    }

    @Override
    public void onResume() {
        Log.d(TAG, "MainActivity onResume");
        // Reset monitoring
        stopAllMonitors();
        PositionMonitor.getInstance().setOnRoute(false);
        PositionMonitor.getInstance().setOnClimbId(-1);
        PositionMonitor.getInstance().resetRejoin();
        ClimbController.getInstance().reset(ClimbController.PointType.ROUTE);
        tabLayout.getTabAt(Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_SELECTED_TAB, 0)).select();

        if (Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_ON_CLIMB_ID, -1) > 0) {
            resumeClimbConfirm(Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_ON_CLIMB_ID));
        } else {
            ClimbController.getInstance().reset(ClimbController.PointType.ATTEMPT);
        }

        super.onResume();
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
                Intent it = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(it);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        ActivityCompat.finishAffinity(MainActivity.this);
    }

    private void showClimb(int climbId, boolean resuming, Intent nextIntent) {
        ClimbController.getInstance().loadClimb(Database.getInstance().getClimb(climbId));

        if (nextIntent == null) {
            ClimbController.getInstance().startAttempt(resuming ? PositionMonitor.getInstance().getCurrentClimbIdx() : 0);
            ClimbController.getInstance().loadPB();
            nextIntent = new Intent(MainActivity.this, SectionViewActivity.class);
        }

        if (nextIntent != null) {
            nextIntent.putExtra("id", climbId);
            nextIntent.putExtra("resuming", resuming);
            startActivity(nextIntent);
        }
    }

    @DebugTrace
    private void showRoute(Intent nextIntent, int routeId, int startIdx, RoutePoint point) {
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
            nextIntent.putExtra("id", routeId);
            nextIntent.putExtra("startIdx", startIdx);
            startActivity(nextIntent);
        }
    }

    @DebugTrace
    private void setUpSwitch(SwitchMaterial swi, String pref) {
        boolean prefSet = Preferences.getInstance().getBooleanPreference(pref);
        swi.setChecked(prefSet);
        swi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Preferences.getInstance().addPreference(pref, isChecked);
            }
        });
    }

    private void stopAllMonitors() {
        PositionMonitor.getInstance().stopAllMonitors();
        LocationMonitor.stopListener();
        monitor = null;
    }

    public boolean toggleMonitoring(PositionMonitor.MonitorType type) {
        if (!PositionMonitor.getInstance().getMonitoring().contains(type)) {
            PositionMonitor.getInstance().doMonitor(type);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            if (monitor == null) {
                monitor = LocationMonitor.getInstance(MainActivity.this);
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

    public void stopMonitoring(PositionMonitor.MonitorType type) {
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

    public void deleteConfirm(String type, String name, int id) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this);
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

    private void resumeClimbConfirm(int id) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Resume Climb");
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup messageView = (ViewGroup)inflater.inflate(R.layout.alert_message, null);

        TextView message = messageView.findViewById(R.id.txtAlertMessage);
        GPXRoute c = Database.getInstance().getClimb(id);
        message.setText("Resume tracking of climb: " + c.getName());
        builder.setView(messageView);

        builder.setPositiveButton("Resume", (dialog, which) -> {
            toggleMonitoring(PositionMonitor.MonitorType.CURRENT_CLIMB);
            PositionMonitor.getInstance().doMonitor(PositionMonitor.MonitorType.CLIMB);
            ClimbController.getInstance().clearRoute();
            dialog.cancel();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            Preferences.getInstance().clearIntPreference(Preferences.PREFERENCES_ON_CLIMB_ID);
            ClimbController.getInstance().reset(ClimbController.PointType.ATTEMPT);
            dialog.cancel();
        });
        builder.show();
    }

    @Override
    public void locationChanged(RoutePoint point){
        PositionMonitor.getInstance().locationChanged(point);

        if (PositionMonitor.getInstance().getMonitoring().contains(PositionMonitor.MonitorType.ROUTE) && PositionMonitor.getInstance().isOnRoute()) {
            // Stop monitoring route, as this will now be managed from the route view
            PositionMonitor.getInstance().stopMonitor(PositionMonitor.MonitorType.ROUTE);
            PositionMonitor.getInstance().resetRejoin();
            Intent intent = new Intent(MainActivity.this, RouteViewActivity.class);
            intent.putExtra("follow", 1);
            ClimbController.getInstance().startRoute();
            showRoute(intent, PositionMonitor.getInstance().getRouteId(), PositionMonitor.getInstance().getRouteStartIdx(), point);
        }

        if (PositionMonitor.getInstance().getMonitoring().contains(PositionMonitor.MonitorType.CURRENT_CLIMB) && PositionMonitor.getInstance().getOnClimbId() > 0) {
            PositionMonitor.getInstance().stopMonitor(PositionMonitor.MonitorType.CURRENT_CLIMB);
            showClimb(PositionMonitor.getInstance().getOnClimbId(), true, null);
        }

        if (PositionMonitor.getInstance().getMonitoring().contains(PositionMonitor.MonitorType.CLIMB) && PositionMonitor.getInstance().getOnClimbId() > 0) {
            showClimb(PositionMonitor.getInstance().getOnClimbId(), false, null);
        }
    }

    private void doLoad(String type) {
        NetworkRequest.fetchGPXFiles(getApplicationContext(), type, MainActivity.this, new NetworkRequest.NetworkCallback() {
            @Override
            public void onRequestCompleted(Object callbackData) {
                if (callbackData == null) return;

                FileList files = (FileList)callbackData;
                if (files.getFiles() != null && !files.getFiles().isEmpty()) {
                    // Load files via GPX loader
                    Intent i = new Intent(MainActivity.this, GPXLoadActivity.class);
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
    @Override
    public void setProgress(boolean showProgressDialog, String progressMessage){}
    @Override
    public void clearCompletionPanel(){completionPanel.setVisibility(View.GONE);}
}