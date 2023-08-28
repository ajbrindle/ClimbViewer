package com.sk7software.climbviewer.view;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import com.sk7software.climbviewer.DrawableUpdateInterface;
import com.sk7software.climbviewer.ResizeableContentInterface;

import lombok.Setter;

@Setter
public class AsyncRender extends AsyncTask<String, Void, String> {
    private LinearLayout container;
    private ResizeableContentInterface parent;

    private static final String TAG = AsyncRender.class.getSimpleName();

    public AsyncRender() {
        super();
    }

    public AsyncRender(LinearLayout container, ResizeableContentInterface parent) {

    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        Log.d(TAG, "ASYNC Pre-Execute");
        container.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onPostExecute(String s) {
        super.onPostExecute(s);
        Log.d(TAG, "ASYNC Post-Execute");
        parent.afterResize();
    }

    @Override
    protected void onProgressUpdate(Void... values) {
        super.onProgressUpdate(values);
    }

    @Override
    protected void onCancelled(String s) {
        super.onCancelled(s);
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
    }

    @Override
    protected String doInBackground(String... strings) {
        return null;
    }
}
