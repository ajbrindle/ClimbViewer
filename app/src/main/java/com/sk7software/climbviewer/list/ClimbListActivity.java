package com.sk7software.climbviewer.list;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.sk7software.climbviewer.R;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.Track;
import com.sk7software.climbviewer.model.TrackFile;
import com.sk7software.climbviewer.model.TrackSegment;
import com.sk7software.climbviewer.view.DisplayFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClimbListActivity extends Activity {

    public static final int CLIMB_LIST_OK = 101;

    private CheckBox chkFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_climb_list);
        // Show the Up button in the action bar.
        setupActionBar();
        boolean filtered = Preferences.getInstance().getBooleanPreference(Preferences.PREFERENCES_FILTER_LIST, false);

        chkFilter = findViewById(R.id.chkFilter);
        chkFilter.setChecked(filtered);
        populateList(filtered);

        chkFilter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                Preferences.getInstance().addPreference(Preferences.PREFERENCES_FILTER_LIST, chkFilter.isChecked());
                populateList(chkFilter.isChecked());
            }
        });
    }

    private void populateList(boolean filter) {
        final ListView lv = findViewById(R.id.climbList);
        GPXRoute[] routesToShow = new GPXRoute[1];
        final List<Map<String, String>> items = new ArrayList<>();

        if (filter) {
            int routeId = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_LAST_SELECTED_ROUTE, -1);
            if (routeId > 0) {
                GPXRoute route = Database.getInstance().getRoute(routeId);
                TrackFile tf = new TrackFile();
                Track track = new Track();
                TrackSegment ts = new TrackSegment();
                ts.setPoints(route.getPoints());
                track.setTrackSegment(ts);
                tf.setRoute(track);
                List<GPXRoute> matchingClimbs = tf.matchToClimbsAfterIndex(0);
                routesToShow = matchingClimbs.toArray(routesToShow);
            }
        } else {
            routesToShow = Database.getInstance().getClimbs();
        }

        if (routesToShow.length == 0 || routesToShow[0] == null) {
            chkFilter.setChecked(false);
            return;
        }

        for (GPXRoute rt : routesToShow) {
            GPXRoute climb = Database.getInstance().getClimb(rt.getId());
            climb.setPointsDist();
            climb.calcRating();
            float dist = climb.getPoints().get(climb.getPoints().size()-1).getDistFromStart();
            Map<String, String> r = new HashMap<>();
            r.put("name", climb.getName());
            r.put("value", "Rating: " + climb.getRating() + " (" +
                    DisplayFormatter.formatDecimal(dist/1000.0f, 1) + "km/" +
                    DisplayFormatter.formatDecimal((float)(climb.getElevationChange()), 0) + "m)");
            r.put("id", String.valueOf(climb.getId()));
            r.put("rating", String.valueOf(climb.getRating()));
            items.add(r);

            if (Preferences.getInstance().getBooleanPreference(Preferences.PREFERENCES_CLIMB_SORT_RATING, false)) {
                Collections.sort(items, new Comparator<Map<String, String>>() {
                    @Override
                    public int compare(Map<String, String> t1, Map<String, String> t2) {
                        return Integer.parseInt(t2.get("rating")) - Integer.parseInt(t1.get("rating"));
                    }
                });
            }
        }

        SimpleAdapter adapter = new SimpleAdapter(this, items, R.layout.list_item,
                new String[]{"name", "value", "id"}, new int[]{R.id.firstLine, R.id.secondLine, R.id.dummyId});
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent();
                i.putExtra("climb", items.get(position).get("name"));
                i.putExtra("id", Integer.parseInt(items.get(position).get("id")));
                i.putExtra("pos", String.valueOf(position));
                setResult(CLIMB_LIST_OK, i);
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
