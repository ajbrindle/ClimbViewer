package com.sk7software.climbviewer.network;

import android.content.Context;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sk7software.climbviewer.ActivityUpdateInterface;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.model.BackupData;
import com.sk7software.climbviewer.model.GPXFile;
import com.sk7software.climbviewer.model.GPXMetadata;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.Track;
import com.sk7software.climbviewer.model.TrackFile;
import com.sk7software.climbviewer.model.TrackMetadata;
import com.sk7software.climbviewer.model.TrackSegment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.util.Arrays;

public class NetworkRequest {

    private static RequestQueue queue;

    private static final String GPX_LOAD_URL = "http://www.sk7software.co.uk/gpxloader/gpxload.php";
    private static final String GPX_LIST_URL = "http://www.sk7software.co.uk/gpxloader/gpxlist.php?dir=";
    private static final String BACKUP_URL = "http://www.sk7software.co.uk/climbviewer/backup/backup.php";
    private static final String RESTORE_URL = "http://www.sk7software.co.uk/climbviewer/backup/restore.php?id=";
    private static final String TAG = NetworkRequest.class.getSimpleName();

    public interface NetworkCallback {
        public void onRequestCompleted(Object callbackData);
        public void onError(Exception e);
    }

    private synchronized static RequestQueue getQueue(Context context) {
        if (queue == null) {
            queue = Volley.newRequestQueue(context);
        }
        return queue;
    }

    public static void fetchGPX(final Context context, String dir, String gpxFile, final NetworkCallback callback) {
        Log.d(TAG, "Fetching: " + gpxFile);
        try {
            JsonObjectRequest jsObjRequest = new JsonObjectRequest
                    (Request.Method.GET, GPX_LOAD_URL + "?dir=" + dir + "&name=" + gpxFile,
                            null,
                            new Response.Listener<JSONObject>() {
                                @Override
                                public void onResponse(JSONObject response) {
                                    try {
                                        ObjectMapper mapper = new ObjectMapper();
                                        GPXRoute route = mapper.readValue(response.toString(), GPXRoute.class);

                                        updateDatabase(dir, route);
                                        Log.d(TAG, route.getName());
                                        callback.onRequestCompleted(route);
                                    } catch (JsonProcessingException e) {
                                        Log.d(TAG, "Error getting GPX data: " + e.getMessage());
                                        callback.onRequestCompleted(null);
                                    }
                                }
                            },
                            new Response.ErrorListener() {
                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    Log.d(TAG, "Error => " + error.toString());
                                    callback.onError(error);
                                }
                            }
                    );
            jsObjRequest.setRetryPolicy(new DefaultRetryPolicy(5000, 1, 1));
            getQueue(context).add(jsObjRequest);
        } catch (Exception e) {
            Log.d(TAG, "Error fetching GPX route: " + e.getMessage());
        }
    }

    public static void fetchGPXFiles(final Context context, String dir, ActivityUpdateInterface uiUpdate, final NetworkCallback callback) {
        Log.d(TAG, "Fetching GPX file list");
        try {
            uiUpdate.setProgress(true, "Fetching file list");
            JsonArrayRequest jsObjRequest = new JsonArrayRequest
                    (Request.Method.GET, GPX_LIST_URL + dir,
                            null,
                            new Response.Listener<JSONArray>() {
                                @Override
                                public void onResponse(JSONArray response) {
                                    try {
                                        ObjectMapper mapper = new ObjectMapper();
                                        FileList files = new FileList();
                                        files.setFiles(Arrays.asList(mapper.readValue(response.toString(), FileDescription[].class)));

                                        for (FileDescription desc : files.getFiles()) {
                                            Log.d(TAG, "File: " + desc.getName());
                                            // Fetch climb if it isn't already in the database
                                            if (!exists(dir, desc)) {
                                                fetchGPX(context, dir, desc.getName(), new NetworkCallback() {
                                                    @Override
                                                    public void onRequestCompleted(Object callbackData) {
                                                        Log.d(TAG, "Loaded file: " + desc.getName());
                                                    }

                                                    @Override
                                                    public void onError(Exception e) {
                                                        Log.d(TAG, "Error reading file: " + desc.getName());
                                                    }
                                                });
                                            }
                                        }

                                        uiUpdate.setProgress(false, null);
                                        callback.onRequestCompleted(files);
                                    } catch (JsonProcessingException e) {
                                        Log.d(TAG, "Error getting dev messages: " + e.getMessage());
                                    }
                                }
                            },
                            new Response.ErrorListener() {
                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    Log.d(TAG, "Error => " + error.toString());
                                    uiUpdate.setProgress(false, null);
                                    callback.onError(error);
                                }
                            }
                    );
            jsObjRequest.setRetryPolicy(new DefaultRetryPolicy(5000, 1, 1));
            getQueue(context).add(jsObjRequest);
        } catch (Exception e) {
            Log.d(TAG, "Error fetching GPX route: " + e.getMessage());
        }
    }

    private static void updateDatabase(String dir, GPXRoute route) {
        if ("climbs".equals(dir)) {
            GPXFile f = new GPXFile();
            GPXMetadata m = new GPXMetadata();
            m.setName(route.getName());
            f.setMetadata(m);
            f.setRoute(route);
            Database.getInstance().addClimb(f);
        } else if ("attempts".equals(dir)) {
            TrackFile f = new TrackFile();
            TrackMetadata m = new TrackMetadata();
            Track t = new Track();
            TrackSegment s = new TrackSegment();

            m.setTime(route.getTime());
            s.setPoints(route.getPoints());
            t.setName(route.getName());
            t.setTrackSegment(s);
            f.setRoute(t);
            f.setMetadata(m);
            TrackFile.processTrackFile(f);
        } else if ("routes".equals(dir)) {
            GPXFile f = new GPXFile();
            GPXMetadata m = new GPXMetadata();
            m.setName(route.getName());
            f.setMetadata(m);
            f.setRoute(route);
            Database.getInstance().addRoute(f);
        }
    }

    public static void backupDB(final Context context, BackupData backupData, ActivityUpdateInterface uiUpdate, final NetworkCallback callback) {
        try {
            Gson gson = new GsonBuilder()
                    .create();
            String json = gson.toJson(backupData);
            JSONObject backup = new JSONObject(json);

            JsonObjectRequest jsObjRequest = new JsonObjectRequest
                    (Request.Method.POST, BACKUP_URL, backup,
                            new Response.Listener<JSONObject>() {
                                @Override
                                public void onResponse(JSONObject response) {
                                    Log.d(TAG, "Response: " + response.toString());
                                    uiUpdate.setProgress(false, null);
                                    callback.onRequestCompleted(null);
                                }
                            },
                            new Response.ErrorListener() {
                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    Log.d(TAG, "Error => " + error.toString());
                                    uiUpdate.setProgress(false, null);
                                    callback.onError(error);
                                }
                            }
                    );
            jsObjRequest.setRetryPolicy(new DefaultRetryPolicy(5000, 4, 1));
            getQueue(context).add(jsObjRequest);
        } catch (JSONException e) {
            Log.d(TAG, "Error uploading backup: " + e.getMessage());
            uiUpdate.setProgress(false, null);
        }
    }

    public static void restoreDB(final Context context, int id, final NetworkCallback callback) {
        Log.d(TAG, "Restoring: " + id);
        try {
            JsonObjectRequest jsObjRequest = new JsonObjectRequest
                    (Request.Method.GET, RESTORE_URL + id,
                            null,
                            new Response.Listener<JSONObject>() {
                                @Override
                                public void onResponse(JSONObject response) {
                                    try {
                                        ObjectMapper mapper = new ObjectMapper();
                                        String data = response.getString("data");
                                        Log.d(TAG, "Restore data fetched");
                                        callback.onRequestCompleted(data);
                                    } catch (JSONException e) {
                                        Log.d(TAG, "Error getting restore data: " + e.getMessage());
                                        callback.onRequestCompleted(null);
                                    }
                                }
                            },
                            new Response.ErrorListener() {
                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    Log.d(TAG, "Error => " + error.toString());
                                    callback.onError(error);
                                }
                            }
                    );
            jsObjRequest.setRetryPolicy(new DefaultRetryPolicy(5000, 1, 1));
            getQueue(context).add(jsObjRequest);
        } catch (Exception e) {
            Log.d(TAG, "Error fetching restore data: " + e.getMessage());
        }
    }

    private static boolean exists(String dir, FileDescription desc) {
        if ("climbs".equals(dir)) {
            return Database.getInstance().findClimb(desc.getDescription()) < 0;
        } else if ("attempts".equals(dir)) {
            LocalDateTime trackTime = TrackFile.convertToDate(desc.getTime());
            return Database.getInstance().attemptExists(trackTime);
        } else if ("routes".equals(dir)) {
            return Database.getInstance().findRoute(desc.getDescription()) < 0;
        }
        return false;
    }
}
