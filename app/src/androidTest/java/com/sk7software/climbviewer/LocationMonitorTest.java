package com.sk7software.climbviewer;

import android.content.Context;
import android.graphics.PointF;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class LocationMonitorTest {
    @Test
    public void testPointWithinLine() {
        PointF a = new PointF(2, 5);
        PointF b = new PointF(7, 8);

        // Line equation is 5y-3x=19
        // Perpendicular is 3y+5x=42
        assertTrue(LocationMonitor.pointWithinLineSegment(new PointF(6, 6), a, b));
        assertTrue(LocationMonitor.pointWithinLineSegment(new PointF(4, 10), a, b));
        assertTrue(LocationMonitor.pointWithinLineSegment(new PointF(9, -1), a, b));
        assertTrue(LocationMonitor.pointWithinLineSegment(new PointF(3, 9), a, b));
        assertFalse(LocationMonitor.pointWithinLineSegment(new PointF(7, 12), a, b));
        assertFalse(LocationMonitor.pointWithinLineSegment(new PointF(1, 4), a, b));
        assertFalse(LocationMonitor.pointWithinLineSegment(new PointF(200, -319.333f), a, b)); // Too far
        assertFalse(LocationMonitor.pointWithinLineSegment(new PointF(20, -19.333f), a, b)); // Too far

        // Long line where point is > MAX_DIST from both ends
        a.x = 1;
        a.y = 1;
        b.x = 101;
        b.y = 101;
        assertTrue(LocationMonitor.pointWithinLineSegment(new PointF(50, 52), a, b));
    }

    @Test
    public void testSpecificPoints() {
        assertTrue(LocationMonitor.pointWithinLineSegment(new PointF(387926.38f, 381079.12f), new PointF(387814.78f, 381169.94f), new PointF(387984.78f, 381078.7f)));
    }

}