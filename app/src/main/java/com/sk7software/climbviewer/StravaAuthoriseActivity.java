package com.sk7software.climbviewer;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.gms.common.util.Strings;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.list.StravaListActivity;
import com.sk7software.climbviewer.model.strava.StravaAuth;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class StravaAuthoriseActivity extends AppCompatActivity {

    private WebView webViewStrava;

    private static final String TAG = StravaAuthoriseActivity.class.getSimpleName();
    private static final String REDIRECT_URI = "https://www.sk7software.co.uk/strava/token.php";
    private static final String STRAVA_AUTHORISATION_URL = "https://www.strava.com/oauth/authorize" +
            "?client_id=" + BuildConfig.STRAVA_CLIENT_ID +
            "&response_type=code" +
            "&scope=activity:read,activity:read_all" +
            "&redirect_uri=" + getEncodedURL(REDIRECT_URI) +
            "&approval_prompt=auto";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_strava_authorise);

        StravaAuth auth = StravaAuth.fromPrefs();
        System.out.println("EXPIRES: " + Preferences.getInstance().getStringPreference(Preferences.PREFERENCES_STRAVA_EXPIRY) + " - " + auth.hasExpired());

        if (Strings.isEmptyOrWhitespace(auth.getAccessToken())) {
            authenticateInWebView();
        } else {
            // Return to list
            loadListActivity();
        }
    }

    private void authenticateInWebView() {
        // Call authorisation API in webview
        class AuthoriseJavaScriptInterface {
            @SuppressWarnings("unused")
            @JavascriptInterface
            public void processContent(String aContent) {
                final String content = aContent;
                authFromResponse(aContent);
            }
        }

        webViewStrava = (WebView) findViewById(R.id.stravaWebView);
        webViewStrava.setVisibility(View.VISIBLE);
        webViewStrava.getSettings().setJavaScriptEnabled(true);
        webViewStrava.getSettings().setLoadWithOverviewMode(true);
        webViewStrava.getSettings().setUserAgentString(System.getProperty("http.agent"));
        webViewStrava.addJavascriptInterface(new AuthoriseJavaScriptInterface(), "INTERFACE");
        webViewStrava.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                view.loadUrl("javascript:window.INTERFACE.processContent(document.getElementById('access').innerText);");
            }
        });
        webViewStrava.loadUrl(STRAVA_AUTHORISATION_URL);
    }

    private void authFromResponse(String response) {
        Log.d(TAG, "STRAVA AUTH: " + response);
        try {
            ObjectMapper mapper = new ObjectMapper();
            StravaAuth auth = mapper.readValue(response, StravaAuth.class);
            setApplicationPreferences(auth);
        } catch (JsonProcessingException e) {
            Log.d(TAG, "Strava Auth error - " + e.getMessage());
        }
        loadListActivity();
    }

    private void setApplicationPreferences(StravaAuth auth) {
        Preferences.getInstance().addPreference(Preferences.PREFERENCES_STRAVA_AUTH_TOKEN, auth.getAccessToken());
        Preferences.getInstance().addPreference(Preferences.PREFERENCES_STRAVA_REFRESH_TOKEN, auth.getRefreshToken());
        Preferences.getInstance().addPreference(Preferences.PREFERENCES_STRAVA_EXPIRY, auth.calcExpiryTime());
    }

    private void loadListActivity() {
        Intent i = new Intent(StravaAuthoriseActivity.this, StravaListActivity.class);
        startActivity(i);
    }

    private static String getEncodedURL(String url) {
        try {
            return URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }
}