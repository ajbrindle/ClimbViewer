package com.sk7software.climbviewer.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.sk7software.climbviewer.ApplicationContextProvider;
import com.sk7software.climbviewer.R;

import java.util.HashMap;
import java.util.Map;

public class PositionMarker {

    public enum Size {
        SMALL(0.5f),
        MEDIUM(1.0f),
        LARGE(1.5f);

        float scale;

        private Size(float scale) {
            this.scale = scale;
        }

        public float getScale() {
            return scale;
        }
    }

    private Map<Integer, Map<Size, Bitmap>> icons;
    private static PositionMarker INSTANCE = null;

    private PositionMarker() {
        icons = new HashMap<>();
    }

    public static PositionMarker getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PositionMarker();
        }
        return INSTANCE;
    }

    public Bitmap getIcon(Size size, int colour) {
        if (!icons.containsKey(Integer.valueOf(colour))) {
            Bitmap b = bitmapFromVector(colour, size);
            Map<Size, Bitmap> bitmaps = new HashMap<>();
            bitmaps.put(size, b);
            icons.put(Integer.valueOf(colour), bitmaps);
        } else {
            Map<Size, Bitmap> bitmaps = icons.get(Integer.valueOf(colour));
            if (!bitmaps.containsKey(size)) {
                Bitmap b = bitmapFromVector(colour, size);
                bitmaps.put(size, b);
            }
        }
        return icons.get(Integer.valueOf(colour)).get(size);
    }

    private Bitmap bitmapFromVector(int colour, Size size) {
        Context context = ApplicationContextProvider.getContext();
        float scaleFac = size.getScale();

        int right = (int) (82 * scaleFac);
        int bottom = (int) (107 * scaleFac);
        Drawable background = ContextCompat.getDrawable(context, R.drawable.ic_map_marker_solid);
        background.setBounds(0, 0, right, bottom);
        background.setTint(colour);
        Drawable innerBackgroundDrawable = ContextCompat.getDrawable(context, R.drawable.ic_map_marker_solid);
        innerBackgroundDrawable.setBounds((int) (5 * scaleFac), (int) (5 * scaleFac), (int) (77 * scaleFac), (int) (102 * scaleFac));
        innerBackgroundDrawable.setTint(Color.BLACK);
        Drawable vectorDrawable = ContextCompat.getDrawable(context, R.drawable.ic_biking_solid);
        vectorDrawable.setBounds((int) (15 * scaleFac), (int) (17 * scaleFac), (int) (65 * scaleFac), (int) (60 * scaleFac));
        vectorDrawable.setTint(colour);
        Bitmap bitmap = Bitmap.createBitmap(right, bottom, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        background.draw(canvas);
        innerBackgroundDrawable.draw(canvas);
        vectorDrawable.draw(canvas);
        return bitmap;
    }
}
