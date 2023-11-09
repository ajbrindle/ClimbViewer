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
import com.sk7software.climbviewer.BuildConfig;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.model.BackupData;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.MapBoxMap;
import com.sk7software.climbviewer.model.TrackFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class NetworkRequest {

    private static RequestQueue queue;

    private static final String GPX_LOAD_URL = "http://www.sk7software.co.uk/gpxloader/gpxload.php";
    private static final String GPX_LIST_URL = "http://www.sk7software.co.uk/gpxloader/gpxlist.php?dir=";
    private static final String BACKUP_URL = "http://www.sk7software.co.uk/climbviewer/backup/backup.php";
    private static final String RESTORE_URL = "http://www.sk7software.co.uk/climbviewer/backup/restore.php?id=";
    private static final String MAPBOX_URL = "https://api.mapbox.com/styles/v1/ajbrindle?access_token=";
    private static final String TAG = NetworkRequest.class.getSimpleName();

    public interface NetworkCallback {
        void onRequestCompleted(Object callbackData);
        void onError(Exception e);
    }

    private static synchronized RequestQueue getQueue(Context context) {
        if (queue == null) {
            queue = Volley.newRequestQueue(context);
        }
        return queue;
    }

    public static void fetchGPX(final Context context, String dir, String gpxFile, ActivityUpdateInterface uiUpdate, final NetworkCallback callback) {
        Log.d(TAG, "Fetching: " + gpxFile);
        try {
            uiUpdate.setProgress(true, "Fetching file: " + gpxFile);
            JsonObjectRequest jsObjRequest = new JsonObjectRequest
                    (Request.Method.GET, GPX_LOAD_URL + "?dir=" + dir + "&name=" + gpxFile,
                            null,
                            new Response.Listener<JSONObject>() {
                                @Override
                                public void onResponse(JSONObject response) {
                                    try {
                                        ObjectMapper mapper = new ObjectMapper();
                                        GPXRoute route = mapper.readValue(response.toString(), GPXRoute.class);
                                        Log.d(TAG, route.getName());
                                        callback.onRequestCompleted(route);
                                    } catch (JsonProcessingException e) {
                                        Log.d(TAG, "Error getting GPX data: " + e.getMessage());
                                        callback.onRequestCompleted(null);
                                    } finally {
                                        uiUpdate.setProgress(false, null);
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
                                        List<FileDescription> fileList = new LinkedList<FileDescription>(Arrays.asList(mapper.readValue(response.toString(), FileDescription[].class)));
                                        FileList files = new FileList(fileList);
                                        uiUpdate.setProgress(false, null);
                                        callback.onRequestCompleted(files);
                                    } catch (JsonProcessingException e) {
                                        Log.d(TAG, "Error getting file List: " + e.getMessage());
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

    public static void fetchMapBoxMaps(final Context context, ActivityUpdateInterface uiUpdate, final NetworkCallback callback) {
        Log.d(TAG, "Fetching maps");
        try {
            uiUpdate.setProgress(true, "Fetching map names");
            JsonArrayRequest jsArrayRequest = new JsonArrayRequest
                    (Request.Method.GET, MAPBOX_URL + BuildConfig.MAPBOX_FETCH_MAPS_TOKEN + "&limit=10",
                            null,
                            new Response.Listener<JSONArray>() {
                                @Override
                                public void onResponse(JSONArray response) {
                                    try {
                                        ObjectMapper mapper = new ObjectMapper();
                                        List<MapBoxMap> maps = new ArrayList<>();
                                        for (int i=0; i<response.length(); i++) {
                                            try {
                                                maps.add(mapper.readValue(response.get(i).toString(), MapBoxMap.class));
                                            } catch (JSONException e) {
                                                Log.e(TAG, "Error reading return array: " + e.getMessage());
                                            }
                                        }
                                        callback.onRequestCompleted(maps);
                                    } catch (JsonProcessingException e) {
                                        Log.e(TAG, "Error getting map data: " + e.getMessage());
                                        callback.onRequestCompleted(null);
                                    } finally {
                                        uiUpdate.setProgress(false, null);
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
            jsArrayRequest.setRetryPolicy(new DefaultRetryPolicy(5000, 1, 1));
            getQueue(context).add(jsArrayRequest);
        } catch (Exception e) {
            Log.d(TAG, "Error fetching maps: " + e.getMessage());
        }
    }


    private static void loadFiles(Context context, ActivityUpdateInterface uiUpdate, String dir, List<FileDescription> files) {
        FileDescription desc = files.get(0);
        Log.d(TAG, "File: " + desc.getName());
        files.remove(0);

        // Fetch file contents if it isn't already in the database
        if (!exists(dir, desc)) {
            fetchGPX(context, dir, desc.getName(), uiUpdate, new NetworkCallback() {
                @Override
                public void onRequestCompleted(Object callbackData) {
                    Log.d(TAG, "Loaded file: " + desc.getName());
                    if (!files.isEmpty()) {
                        loadFiles(context, uiUpdate, dir, files);
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.d(TAG, "Error reading file: " + desc.getName());
                    if (!files.isEmpty()) {
                        loadFiles(context, uiUpdate, dir, files);
                    }
                }
            });
        }
    }

    public static void backupDB(final Context context, BackupData backupData, final NetworkCallback callback) {
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
                                    callback.onRequestCompleted(null);
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
            jsObjRequest.setRetryPolicy(new DefaultRetryPolicy(5000, 4, 1));
            getQueue(context).add(jsObjRequest);
        } catch (JSONException e) {
            Log.d(TAG, "Error uploading backup: " + e.getMessage());
            callback.onError(e);
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
