package com.sk7software.climbviewer.list;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;

import com.sk7software.climbviewer.R;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.Track;
import com.sk7software.climbviewer.model.TrackFile;
import com.sk7software.climbviewer.model.TrackSegment;

import java.util.List;

public class ClimbListActivity extends Activity {

    public static final int CLIMB_LIST_OK = 101;

    private CheckBox chkFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_climb_list);
        // Show the Up button in the action bar.
        setupActionBar();
        populateList(false);
        chkFilter = findViewById(R.id.chkFilter);
        chkFilter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                populateList(chkFilter.isChecked());
            }
        });
    }

    private void populateList(boolean filter) {
        final ListView lv = findViewById(R.id.climbList);
        GPXRoute[] routesToShow = new GPXRoute[1];

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

        final GPXRoute[] items = routesToShow;
        if (items.length == 0 || items[0] == null) {
            chkFilter.setChecked(false);
            return;
        }

        ArrayAdapter<GPXRoute> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1, items);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent();
                i.putExtra("climb", items[position].getName());
                i.putExtra("id", items[position].getId());
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
