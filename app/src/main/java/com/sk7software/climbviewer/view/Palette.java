package com.sk7software.climbviewer.view;

import android.graphics.Color;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Palette {

    private static int MIN_ANGLE = -28;
    private static int MAX_ANGLE = 28;
    private static PaletteColour[] colours = new PaletteColour[] {
            new PaletteColour(0xFFB4B4FF,-28),
            new PaletteColour(0xFF7981FB,-24),
            new PaletteColour(0xFF6F84EF,-20),
            new PaletteColour(0xFF6B86E1,-16),
            new PaletteColour(0xFF608CD6,-13),
            new PaletteColour(0xFF5492BF,-10),
            new PaletteColour(0xFF4A92B5,-8),
            new PaletteColour(0xFF4299A2,-6),
            new PaletteColour(0xFF359C94,-5),
            new PaletteColour(0xFF2BA57D,-4),
            new PaletteColour(0xFF10AB58,-3),
            new PaletteColour(0xFF10B053,-2),
            new PaletteColour(0xFF06B737,-1),
            new PaletteColour(0xFF33C42A,0),
            new PaletteColour(0xFF6DD41D,1),
            new PaletteColour(0xFFA4E710,2),
            new PaletteColour(0xFFC0EB0B,3),
            new PaletteColour(0xFFDCF504,4),
            new PaletteColour(0xFFF9F800,5),
            new PaletteColour(0xFFFFD200,6),
            new PaletteColour(0xFFFBA404,7),
            new PaletteColour(0xFFF78308,8),
            new PaletteColour(0xFFF75E08,9),
            new PaletteColour(0xFFF44410,10),
            new PaletteColour(0xFFEF2810,12),
            new PaletteColour(0xFFEF0810,14),
            new PaletteColour(0xFFD60010,16),
            new PaletteColour(0xFFB50008,20),
            new PaletteColour(0xFF940004,24),
            new PaletteColour(0xFF460002,28)
    };

    public static int getColour(double elevation) {
        if (elevation <= MIN_ANGLE) {
            return colours[0].getRgb();
        } else if (elevation >= MAX_ANGLE) {
            return colours[colours.length-1].getRgb();
        }

        for (PaletteColour colour : colours) {
            if (colour.getElevation() >= elevation) {
                return colour.getRgb();
            }
        }

        // Unreachable
        return 0xFF000000;
    }
}
