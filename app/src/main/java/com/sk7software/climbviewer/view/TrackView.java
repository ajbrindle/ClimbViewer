package com.sk7software.climbviewer.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import lombok.Setter;

@Setter
public class TrackView extends View {
    private float climbLength;
    private float distDiff;

    public TrackView(Context context) {
        super(context);
    }

    public TrackView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TrackView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public TrackView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setDistances(float attemptDist, float pbDist) {
        distDiff = attemptDist - pbDist;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float x = 40;
        float scaleFac = 10;

        if (distDiff > 0) {
            x += distDiff * scaleFac;
        }

        Paint p = new Paint();
        p.setStrokeWidth(2.0f);
        p.setColor(Color.BLUE);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        canvas.drawCircle(x, 40, 35, p);

        x = 40;
        if (distDiff < 0) {
            x += Math.abs(distDiff * scaleFac);
        }
        p.setColor(Color.GREEN);
        canvas.drawCircle(x, 80, 35, p);
    }
}
