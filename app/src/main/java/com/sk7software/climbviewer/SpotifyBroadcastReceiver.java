package com.sk7software.climbviewer;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

public class SpotifyBroadcastReceiver extends Service {
    static final class BroadcastTypes {
        static final String SPOTIFY_PACKAGE = "com.spotify.music";
        static final String METADATA_CHANGED = SPOTIFY_PACKAGE + ".metadatachanged";
        static final String PLAYBACK_STATE_CHANGED = SPOTIFY_PACKAGE + ".playbackstatechanged";
    }

    private static SpotifyTrackUpdateInterface activity;

    public static void setActivity(SpotifyTrackUpdateInterface activity) {
        SpotifyBroadcastReceiver.activity = activity;
    }

    private final BroadcastReceiver trackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(BroadcastTypes.METADATA_CHANGED)) {
                String trackId = intent.getStringExtra("id");
                String artistName = intent.getStringExtra("artist");
                String trackName = intent.getStringExtra("track");
                activity.trackChanged(artistName, trackName);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BroadcastTypes.METADATA_CHANGED);
        registerReceiver(trackReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(trackReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
