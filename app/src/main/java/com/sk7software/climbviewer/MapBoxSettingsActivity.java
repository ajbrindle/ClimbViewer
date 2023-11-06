package com.sk7software.climbviewer;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.common.util.Strings;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.list.MapListActivity;

import java.util.ArrayList;
import java.util.HashMap;

public class MapBoxSettingsActivity extends AppCompatActivity {

    private ListView listMapSel;
    private TextView txt2dMaps;
    private TextView txtFollowMaps;
    private TextView txt3dMaps;
    private SimpleAdapter mapsListAdapter;
    private int mapType;
    private final ArrayList<HashMap<String,String>> mapsList = new ArrayList<>();

    private static final String TAG = MapBoxSettingsActivity.class.getSimpleName();
    private static final int MAP_TYPE_2D = 1;
    private static final int MAP_TYPE_FOLLOW = 2;
    private static final int MAP_TYPE_3D = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mapbox_settings);

        String map2d = Preferences.getInstance().getStringPreference(Preferences.PREFERENCES_MAPBOX_2D_MAP);
        String mapFollow = Preferences.getInstance().getStringPreference(Preferences.PREFERENCES_MAPBOX_FOLLOW_MAP);
        String map3d = Preferences.getInstance().getStringPreference(Preferences.PREFERENCES_MAPBOX_3D_MAP);

        ActivityResultLauncher<Intent> listResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        handleListResult(result);
                    }
                });

        listMapSel = findViewById(R.id.listMapSel);
        txt2dMaps = findViewById(R.id.txt2d);
        txtFollowMaps = findViewById(R.id.txtFollow);
        txt3dMaps = findViewById(R.id.txt3d);

        HashMap<String,String> maps2d = new HashMap<String,String>();
        HashMap<String,String> mapsFollow = new HashMap<String,String>();
        HashMap<String,String> maps3d = new HashMap<String,String>();

        if (!Strings.isEmptyOrWhitespace(map2d)) {
            maps2d.put("value", map2d);
            txt2dMaps.setText(map2d);
        } else {
            maps2d.put("value", "Not selected");
            txt2dMaps.setText("Not selected");
        }
        if (!Strings.isEmptyOrWhitespace(mapFollow)) {
            mapsFollow.put("value", mapFollow);
            txtFollowMaps.setText(mapFollow);
        } else {
            mapsFollow.put("value", "Not selected");
            txtFollowMaps.setText("Not selected");
        }
        if (!Strings.isEmptyOrWhitespace(map3d)) {
            maps3d.put("value", map3d);
            txt3dMaps.setText(map3d);
        } else {
            maps3d.put("value", "Not selected");
            txt3dMaps.setText("Not selected");
        }

        Button btn2dMaps = findViewById(R.id.btn2d);
        btn2dMaps.setOnClickListener(vw -> {
            mapsList.add(maps2d);
            mapsListAdapter = new SimpleAdapter(this, mapsList, R.layout.list_item,
                    new String[]{"value"}, new int[]{R.id.firstLine});
            listMapSel.setAdapter(mapsListAdapter);
            mapType = MAP_TYPE_2D;
            Intent i = new Intent(this, MapListActivity.class);
            listResultLauncher.launch(i);
        });
        Button btnFollowMaps = findViewById(R.id.btnFollow);
        btnFollowMaps.setOnClickListener(vw -> {
            mapsList.add(mapsFollow);
            mapsListAdapter = new SimpleAdapter(this, mapsList, R.layout.list_item,
                    new String[]{"value"}, new int[]{R.id.firstLine});
            listMapSel.setAdapter(mapsListAdapter);
            mapType = MAP_TYPE_FOLLOW;
            Intent i = new Intent(this, MapListActivity.class);
            listResultLauncher.launch(i);
        });
        Button btn3dMaps = findViewById(R.id.btn3d);
        btn3dMaps.setOnClickListener(vw -> {
            mapsList.add(maps3d);
            mapsListAdapter = new SimpleAdapter(this, mapsList, R.layout.list_item,
                    new String[]{"value"}, new int[]{R.id.firstLine});
            listMapSel.setAdapter(mapsListAdapter);
            mapType = MAP_TYPE_3D;
            Intent i = new Intent(this, MapListActivity.class);
            listResultLauncher.launch(i);
        });

        MapListActivity.clearMapsList();
    }

    private void handleListResult(ActivityResult result) {
        // Check which request we're responding to
        int resultCode = result.getResultCode();
        Intent data = result.getData();

        // Make sure the request was successful
        if (resultCode == MapListActivity.MAP_LIST_OK && data.hasExtra("name")) {
            String name = data.getStringExtra("name");
            String id = data.getStringExtra("id");
            HashMap<String, String> h = new HashMap<String, String>();
            h = mapsList.get(0);
            h.put("value", name);
            mapsListAdapter.notifyDataSetChanged();

            switch(mapType) {
                case MAP_TYPE_2D:
                    txt2dMaps.setText(name);
                    Preferences.getInstance().addPreference(Preferences.PREFERENCES_MAPBOX_2D_MAP, name);
                    Preferences.getInstance().addPreference(Preferences.PREFERENCES_MAPBOX_2D_MAP_ID, id);
                    break;
                case MAP_TYPE_FOLLOW:
                    txtFollowMaps.setText(name);
                    Preferences.getInstance().addPreference(Preferences.PREFERENCES_MAPBOX_FOLLOW_MAP, name);
                    Preferences.getInstance().addPreference(Preferences.PREFERENCES_MAPBOX_FOLLOW_MAP_ID, id);
                    break;
                case MAP_TYPE_3D:
                    txt3dMaps.setText(name);
                    Preferences.getInstance().addPreference(Preferences.PREFERENCES_MAPBOX_3D_MAP, name);
                    Preferences.getInstance().addPreference(Preferences.PREFERENCES_MAPBOX_3D_MAP_ID, id);
            }
            Log.d(TAG, "Selected map: " + name + ":" + id);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        finish();
    }

}