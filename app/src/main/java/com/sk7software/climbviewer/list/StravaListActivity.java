package com.sk7software.climbviewer.list;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.gms.common.util.Strings;
import com.sk7software.climbviewer.ActivityUpdateInterface;
import com.sk7software.climbviewer.GPXLoadActivity;
import com.sk7software.climbviewer.R;
import com.sk7software.climbviewer.StravaAuthoriseActivity;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.model.strava.StravaActivityStream;
import com.sk7software.climbviewer.model.strava.StravaAuth;
import com.sk7software.climbviewer.model.strava.StravaItem;
import com.sk7software.climbviewer.network.NetworkRequest;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class StravaListActivity extends Activity implements ActivityUpdateInterface {

    private StravaType loadType;
    private AlertDialog.Builder progressDialogBuilder;
    private Dialog progressDialog;
    public static final int STRAVA_LIST_OK = 201;
    private static final String TAG = StravaListActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_strava_list);
        progressDialogBuilder = new AlertDialog.Builder(StravaListActivity.this);
        progressDialogBuilder.setView(R.layout.progress);

        String type = getIntent().getStringExtra("type");
        if ("activity".equals(type)) {
            loadType = StravaType.ACTIVITY;
        } else {
            loadType = StravaType.ROUTE;
        }

        // Show the Up button in the action bar.
        setupActionBar();
        authorise();
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

    private void loadAndShowList() {
        // Populate list
        final ListView lv = findViewById(R.id.stravaList);
        final String accessToken = Preferences.getInstance().getStringPreference(Preferences.PREFERENCES_STRAVA_AUTH_TOKEN);

        if (loadType == StravaType.ROUTE) {
            loadAndShowRouteList(lv, accessToken);
            return;
        }
        NetworkRequest.fetchStravaActivities(StravaListActivity.this, accessToken, null, new NetworkRequest.NetworkCallback() {
            @Override
            public void onRequestCompleted(Object callbackData) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    final StravaItem[] stravaItems = mapper.readValue(callbackData.toString(), StravaItem[].class);
                    StravaAdapter adapter = new StravaAdapter(StravaListActivity.this, R.layout.strava_list_item, stravaItems);
                    lv.setAdapter(adapter);
                    setProgress(false, null);
                    setActivityListClickListener(lv, stravaItems, accessToken, StravaType.ACTIVITY);
                } catch (JsonProcessingException e) {
                    Log.d(TAG, "Error reading response: " + e.getMessage());
                    setProgress(false, null);
                }
            }

            @Override
            public void onError(Exception e) {
                setProgress(false, null);
            }
        });
    }

    private void loadAndShowRouteList(final ListView lv, final String accessToken) {
        // Fetch athlete
        NetworkRequest.fetchStravaAthlete(StravaListActivity.this, accessToken, null, new NetworkRequest.NetworkCallback() {
            @Override
            public void onRequestCompleted(Object callbackData) {
                int athleteId = (Integer)callbackData;
                Log.d(TAG, "Athlete: " + athleteId);
                NetworkRequest.fetchStravaRoutes(StravaListActivity.this, accessToken, athleteId, null, new NetworkRequest.NetworkCallback() {
                    @Override
                    public void onRequestCompleted(Object callbackData) {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            final StravaItem[] stravaItems;
                            stravaItems = mapper.readValue(callbackData.toString(), StravaItem[].class);
                            StravaAdapter adapter = new StravaAdapter(StravaListActivity.this, R.layout.strava_list_item, stravaItems);
                            lv.setAdapter(adapter);
                            setRouteListClickListener(lv, stravaItems, accessToken, StravaType.ROUTE);
                            setProgress(false, null);
                        } catch (JsonProcessingException e) {
                            setProgress(false, null);
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        setProgress(false, null);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                setProgress(false, null);
            }
        });
    }
    private void authorise() {
        StravaAuth auth = StravaAuth.fromPrefs();

        if (Strings.isEmptyOrWhitespace(auth.getAccessToken())) {
            Intent i = new Intent(StravaListActivity.this, StravaAuthoriseActivity.class);
            startActivity(i);
        } else if (!Strings.isEmptyOrWhitespace(auth.getRefreshToken()) && auth.hasExpired()) {
            setProgress(true, "Fetching from strava");
            refresh(auth.getRefreshToken());
        } else {
            // Assume still authenticated
            setProgress(true, "Fetching from strava");
            loadAndShowList();
        }
    }

    private void refresh(String refreshToken) {
        NetworkRequest.refreshStravaAuth(this, refreshToken, null, new NetworkRequest.NetworkCallback() {
            @Override
            public void onRequestCompleted(Object callbackData) {
                StravaAuth auth = (StravaAuth)callbackData;
                Preferences.getInstance().addPreference(Preferences.PREFERENCES_STRAVA_AUTH_TOKEN, auth.getAccessToken());
                Preferences.getInstance().addPreference(Preferences.PREFERENCES_STRAVA_REFRESH_TOKEN, auth.getRefreshToken());
                Preferences.getInstance().addPreference(Preferences.PREFERENCES_STRAVA_EXPIRY, auth.calcExpiryTime());
                loadAndShowList();
            }

            @Override
            public void onError(Exception e) {
                setProgress(false, null);
            }
        });
    }

    private void setActivityListClickListener(ListView lv, StravaItem[] stravaItems, String accessToken, StravaType type) {
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                long activityId = stravaItems[position].getId();
                final String activityDateTime = stravaItems[position].getDateTime();

                setProgress(true, "Fetching activity: " + stravaItems[position].getName());
                NetworkRequest.fetchStravaActivityStream(StravaListActivity.this, accessToken, activityId, null, new NetworkRequest.NetworkCallback() {
                    @Override
                    public void onRequestCompleted(Object callbackData) {
                        if (callbackData != null) {
                            StravaActivityStream activityStream = (StravaActivityStream)callbackData;
                            Intent i = new Intent(StravaListActivity.this, GPXLoadActivity.class);
                            i.putExtra("stream", (Parcelable) activityStream);
                            i.putExtra("dateTime", activityDateTime);
                            i.putExtra("gpxType", "stream");
                            i.putExtra("streamType", type.name());
                            setProgress(false, null);
                            startActivity(i);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        setProgress(false, null);
                        Log.e(TAG, "Error: " + e.getMessage());
                    }
                });
            }
        });
    }

    private void setRouteListClickListener(ListView lv, StravaItem[] stravaItems, String accessToken, StravaType type) {
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                long activityId = stravaItems[position].getId();
                final String routeName = stravaItems[position].getName();
                final String activityDateTime = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")) + "Z";

                setProgress(true, "Fetching route: " + stravaItems[position].getName());
                NetworkRequest.fetchStravaRouteStream(StravaListActivity.this, accessToken, activityId, null, new NetworkRequest.NetworkCallback() {
                    @Override
                    public void onRequestCompleted(Object callbackData) {
                        if (callbackData != null) {
                            StravaActivityStream activityStream = (StravaActivityStream)callbackData;
                            Intent i = new Intent(StravaListActivity.this, GPXLoadActivity.class);
                            i.putExtra("stream", (Parcelable) activityStream);
                            i.putExtra("dateTime", activityDateTime);
                            i.putExtra("gpxType", "stream");
                            i.putExtra("streamType", type.name());
                            i.putExtra("name", routeName);
                            setProgress(false, null);
                            startActivity(i);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        setProgress(false, null);
                        Log.e(TAG, "Error: " + e.getMessage());
                    }
                });
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

    public enum StravaType {
        ACTIVITY,
        ROUTE;
    }
}