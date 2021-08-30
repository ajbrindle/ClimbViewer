package com.sk7software.climbviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.sk7software.climbviewer.model.TrackFile;

import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;

public class DateConvertTest {
    @Test
    public void testDate() {
        LocalDateTime d = TrackFile.convertToDate("2021-05-17T21:08:33Z");

        assertEquals(2021, d.getYear());
        assertEquals(5, d.getMonthValue());
        assertEquals(17, d.getDayOfMonth());
        assertEquals(21, d.getHour());
        assertEquals(8, d.getMinute());
        assertEquals(33, d.getSecond());
    }
}
