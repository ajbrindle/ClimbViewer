package com.sk7software.climbviewer.list;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.sk7software.climbviewer.R;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.model.GPXRoute;

public class ClimbListActivity extends Activity {

    public static final int CLIMB_LIST_OK = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_climb_list);
        // Show the Up button in the action bar.
        setupActionBar();

        // Populate list
        final ListView lv = findViewById(R.id.climbList);
        final GPXRoute[] items = Database.getInstance().getClimbs();

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
    } //getActionBar().setDisplayHomeAsUpEnabled(true);

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

}
