package com.sk7software.climbviewer;
import android.content.Context;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;


/**
 * Created by Andrew on 09/04/2018.
 */

public class VoicePromptUtil {
    private static VoicePromptUtil tts = null;
    private TextToSpeech mTTS = null;
    private boolean running;

    private static final String TAG = VoicePromptUtil.class.getSimpleName();

    private VoicePromptUtil() {}

    public static void init(Context context, TextToSpeech.OnInitListener listener) {
        tts = getInstance();
        tts.mTTS = new TextToSpeech(context, listener);
        tts.running = true;
    }

    public int setup() {
        if (mTTS != null) {
            return mTTS.setLanguage(Locale.UK);
        }
        return -1;
    }

    public void saySomething(String text, int qmode) {
        if (mTTS != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mTTS.speak(text, qmode, null, null);
            } else  {
                mTTS.speak(text, qmode, null);
            }
        }
    }

    public void destroy() {
        if (mTTS != null) {
            Log.d(TAG, "Destroying TTS");
            try {
                mTTS.stop();
                mTTS.shutdown();
            } catch (Exception e) {
                Log.d(TAG, "Error destroying TTS: " + e.getMessage());
            } finally {
                running = false;
                mTTS = null;
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = VoicePromptUtil.getInstance().setup();
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.d(TAG, "TTS not supported");
            } else if (result < 0) {
                Log.d(TAG, "Problem setting up TTS");
            } else {
                Log.d(TAG, "Text to speech initialised");
            }
        } else {
            Log.d(TAG, "TTS initialization failed");
        }
    }

    public static synchronized VoicePromptUtil getInstance() {
        if (tts == null) {
            tts = new VoicePromptUtil();
        }
        return tts;
    }
}
