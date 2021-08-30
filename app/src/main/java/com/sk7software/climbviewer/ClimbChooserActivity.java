package com.sk7software.climbviewer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.list.ClimbListActivity;
import com.sk7software.climbviewer.model.GPXFile;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.model.TrackFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

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

    private static final String TAG = ClimbChooserActivity.class.getSimpleName();

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

        // Check location permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        // Set up database
        Database db = Database.getInstance();
        db.getWritableDatabase();
        GPXFile.addLocalFiles();
        TrackFile.addLocalFiles();

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
                showClimb();
            }
        });

        // Start monitoring location
        allClimbs = Arrays.asList(Database.getInstance().getClimbs());
        for (GPXRoute r : allClimbs) {
            Log.d(TAG, "All climbs: " + r.getName() + " [" + r.getPoints().size() + "]" +
                            " (" + r.getPoints().get(0).getEasting() + "," + r.getPoints().get(0).getNorthing() + ") " +
                            " (" + r.getPoints().get(1).getEasting() + "," + r.getPoints().get(1).getNorthing() + ") ");
        }
        monitor = new LocationMonitor(this);
    }

    @Override
    public void onResume() {
        if (!monitor.isListenerRunning()) {
            monitor.resumeListener();
        }
        super.onResume();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        if (monitor.isListenerRunning()) {
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
                    showClimb();
                    break;
                }
            }
        }

        lastPoint = currentPoint;
    }

    private void showClimb() {
        Intent i = new Intent(ApplicationContextProvider.getContext(), ClimbViewActivity.class);
        i.putExtra("id", currentClimbId);
        startActivity(i);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
}