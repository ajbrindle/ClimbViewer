package com.sk7software.climbviewer.list;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.sk7software.climbviewer.ActivityUpdateInterface;
import com.sk7software.climbviewer.R;
import com.sk7software.climbviewer.model.MapBoxMap;
import com.sk7software.climbviewer.network.NetworkRequest;

import java.util.List;

public class MapListActivity extends Activity implements ActivityUpdateInterface {
    public static final int MAP_LIST_OK = 201;
    public static final int MAP_LIST_ERROR = 500;
    private AlertDialog.Builder progressDialogBuilder;
    private AlertDialog progressDialog;
    private static MapBoxMap[] maps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_list);
        // Show the Up button in the action bar.
        setupActionBar();

        progressDialogBuilder = new AlertDialog.Builder(MapListActivity.this);
        progressDialogBuilder.setView(R.layout.progress);

        // Populate list
        final ListView lv = findViewById(R.id.mapList);

        if (maps == null || maps.length == 0) {
            // Fetch maps from mapbox
            NetworkRequest.fetchMapBoxMaps(MapListActivity.this, MapListActivity.this, new NetworkRequest.NetworkCallback() {
                @Override
                public void onRequestCompleted(Object callbackData) {
                    List<MapBoxMap> mapList = (List<MapBoxMap>)callbackData;
                    maps = new MapBoxMap[mapList.size()];
                    maps = mapList.toArray(maps);
                    populateList(maps, lv);
                }

                @Override
                public void onError(Exception e) {
                    Intent i = new Intent();
                    setResult(MAP_LIST_ERROR, i);
                    finish();
                }
            });
        } else {
            populateList(maps, lv);
        }
    }

    private void populateList(MapBoxMap[] maps, ListView lv) {
        ArrayAdapter<MapBoxMap> adapter = new ArrayAdapter<>(MapListActivity.this, android.R.layout.simple_list_item_1, android.R.id.text1, maps);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent();
                i.putExtra("name", maps[position].getName());
                i.putExtra("id", maps[position].getId());
                i.putExtra("pos", String.valueOf(position));
                setResult(MAP_LIST_OK, i);
                finish();
            }
        });

    }
    /**
     * Set up the {@link android.app.ActionBar}.
     */
    private void setupActionBar() {
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void setProgress(boolean showProgressDialog, String progressMessage){
        if (showProgressDialog && progressDialog == null) {
            progressDialog = progressDialogBuilder
                    .setMessage(progressMessage)
                    .setCancelable(false)
                    .create();
            progressDialog.show();
        } else {
            if (progressDialog != null) {
                progressDialog.dismiss();
                progressDialog = null;
            }
        }
    }

    public static void clearMapsList() {
        maps = null;
    }
}
