package com.sk7software.climbviewer;

import static org.junit.Assert.assertEquals;

import com.sk7software.climbviewer.view.Palette;

import org.junit.Test;

public class PaletteTest {
    @Test
    public void testToHex() {
        assertEquals("#33C42A", Palette.getColourRGB(0.0));
        assertEquals("#B50008", Palette.getColourRGB(20.0));
    }
}
