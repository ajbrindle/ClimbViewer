package com.sk7software.climbviewer.list;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.sk7software.climbviewer.R;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.view.DisplayFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteListActivity extends Activity {

    public static final int ROUTE_LIST_OK = 201;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_list);
        // Show the Up button in the action bar.
        setupActionBar();

        // Populate list
        final ListView lv = findViewById(R.id.routeList);
        final List<Map<String, String>> items = new ArrayList<>();
        GPXRoute[] routes = Database.getInstance().getRoutes();
        for (GPXRoute rt : routes) {
            GPXRoute route = Database.getInstance().getRoute(rt.getId());
            route.setPointsDist();
            float dist = route.getPoints().get(route.getPoints().size()-1).getDistFromStart();
            Map<String, String> r = new HashMap<>();
            r.put("name", route.getName());
            r.put("value", DisplayFormatter.formatDecimal(dist/1000.0f, 1) + "km");
            r.put("id", String.valueOf(route.getId()));
            items.add(r);
        }

        SimpleAdapter adapter = new SimpleAdapter(this, items, R.layout.list_item,
                new String[]{"name", "value", "id"}, new int[]{R.id.firstLine, R.id.secondLine, R.id.dummyId});
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent();
                i.putExtra("route", items.get(position).get("name"));
                i.putExtra("id", Integer.parseInt(items.get(position).get("id")));
                i.putExtra("pos", String.valueOf(position));
                setResult(ROUTE_LIST_OK, i);
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

}