package com.sk7software.climbviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.sk7software.climbviewer.model.AttemptStats;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class StatsTest {

    private List<AttemptStats> attempts;

    @Before
    public void setup() {
        attempts = new ArrayList<>();
    }
    @Test
    public void testFirstAttempt()  {
        AttemptStats onlyAttempt = new AttemptStats();
        onlyAttempt.setId(1);
        onlyAttempt.setName("Only Attempt");
        onlyAttempt.setDuration(100);
        attempts.add(onlyAttempt);

        AttemptStats stats = new AttemptStats();
        stats.calcStats(attempts);
        assertEquals(1, stats.getTotal());
        assertEquals(1, stats.getPos());
        assertEquals(0, stats.getPb());
        assertEquals(100, stats.getDuration());
        assertFalse(stats.isThisAttemptIsPb());
    }

    @Test
    public void testSecondAttemptFirstIsPB() {
        AttemptStats secondAttempt = new AttemptStats();
        secondAttempt.setId(2);
        secondAttempt.setName("Two Attempts");
        secondAttempt.setDuration(110);
        attempts.add(secondAttempt);

        AttemptStats firstAttempt = new AttemptStats();
        firstAttempt.setId(1);
        firstAttempt.setName("Two Attempts");
        firstAttempt.setDuration(100);
        attempts.add(firstAttempt);

        AttemptStats stats = new AttemptStats();
        stats.calcStats(attempts);
        assertEquals(2, stats.getTotal());
        assertEquals(2, stats.getPos());
        assertEquals(100, stats.getPb());
        assertEquals(110, stats.getDuration());
        assertFalse(stats.isThisAttemptIsPb());
    }

    @Test
    public void testSecondAttemptIsPB() {
        AttemptStats secondAttempt = new AttemptStats();
        secondAttempt.setId(2);
        secondAttempt.setName("Two Attempts");
        secondAttempt.setDuration(90);
        attempts.add(secondAttempt);

        AttemptStats firstAttempt = new AttemptStats();
        firstAttempt.setId(1);
        firstAttempt.setName("Two Attempts");
        firstAttempt.setDuration(100);
        attempts.add(firstAttempt);

        AttemptStats stats = new AttemptStats();
        stats.calcStats(attempts);
        assertEquals(2, stats.getTotal());
        assertEquals(1, stats.getPos());
        assertEquals(100, stats.getPb());
        assertEquals(90, stats.getDuration());
        assertTrue(stats.isThisAttemptIsPb());
    }

    @Test
    public void testTwoAttemptsEqualDuration() {
        AttemptStats secondAttempt = new AttemptStats();
        secondAttempt.setId(2);
        secondAttempt.setName("Two Attempts");
        secondAttempt.setDuration(100);
        attempts.add(secondAttempt);

        AttemptStats firstAttempt = new AttemptStats();
        firstAttempt.setId(1);
        firstAttempt.setName("Two Attempts");
        firstAttempt.setDuration(100);
        attempts.add(firstAttempt);

        AttemptStats stats = new AttemptStats();
        stats.calcStats(attempts);
        assertEquals(2, stats.getTotal());
        assertEquals(1, stats.getPos());
        assertEquals(100, stats.getPb());
        assertEquals(100, stats.getDuration());
        assertFalse(stats.isThisAttemptIsPb());
    }

    @Test
    public void testThreeAttemptsEqualPB() {
        AttemptStats thirdAttempt = new AttemptStats();
        thirdAttempt.setId(3);
        thirdAttempt.setName("Three Attempts");
        thirdAttempt.setDuration(90);
        attempts.add(thirdAttempt);

        AttemptStats secondAttempt = new AttemptStats();
        secondAttempt.setId(2);
        secondAttempt.setName("Three Attempts");
        secondAttempt.setDuration(100);
        attempts.add(secondAttempt);

        AttemptStats firstAttempt = new AttemptStats();
        firstAttempt.setId(1);
        firstAttempt.setName("Three Attempts");
        firstAttempt.setDuration(90);
        attempts.add(firstAttempt);

        AttemptStats stats = new AttemptStats();
        stats.calcStats(attempts);
        assertEquals(3, stats.getTotal());
        assertEquals(1, stats.getPos());
        assertEquals(90, stats.getPb());
        assertEquals(90, stats.getDuration());
        assertFalse(stats.isThisAttemptIsPb());
    }

    @Test
    public void testThreeAttemptsNotPB() {
        AttemptStats thirdAttempt = new AttemptStats();
        thirdAttempt.setId(3);
        thirdAttempt.setName("Three Attempts");
        thirdAttempt.setDuration(100);
        attempts.add(thirdAttempt);

        AttemptStats secondAttempt = new AttemptStats();
        secondAttempt.setId(2);
        secondAttempt.setName("Three Attempts");
        secondAttempt.setDuration(90);
        attempts.add(secondAttempt);

        AttemptStats firstAttempt = new AttemptStats();
        firstAttempt.setId(1);
        firstAttempt.setName("Three Attempts");
        firstAttempt.setDuration(110);
        attempts.add(firstAttempt);

        AttemptStats stats = new AttemptStats();
        stats.calcStats(attempts);
        assertEquals(3, stats.getTotal());
        assertEquals(2, stats.getPos());
        assertEquals(90, stats.getPb());
        assertEquals(100, stats.getDuration());
        assertFalse(stats.isThisAttemptIsPb());
    }

    @Test
    public void testThreeAttemptsNewPB() {
        AttemptStats thirdAttempt = new AttemptStats();
        thirdAttempt.setId(3);
        thirdAttempt.setName("Three Attempts");
        thirdAttempt.setDuration(80);
        attempts.add(thirdAttempt);

        AttemptStats secondAttempt = new AttemptStats();
        secondAttempt.setId(2);
        secondAttempt.setName("Three Attempts");
        secondAttempt.setDuration(100);
        attempts.add(secondAttempt);

        AttemptStats firstAttempt = new AttemptStats();
        firstAttempt.setId(1);
        firstAttempt.setName("Three Attempts");
        firstAttempt.setDuration(90);
        attempts.add(firstAttempt);

        AttemptStats stats = new AttemptStats();
        stats.calcStats(attempts);
        assertEquals(3, stats.getTotal());
        assertEquals(1, stats.getPos());
        assertEquals(90, stats.getPb());
        assertEquals(80, stats.getDuration());
        assertTrue(stats.isThisAttemptIsPb());
    }

    @Test
    public void testThreeAttemptsOthersArePBs() {
        AttemptStats thirdAttempt = new AttemptStats();
        thirdAttempt.setId(3);
        thirdAttempt.setName("Three Attempts");
        thirdAttempt.setDuration(100);
        attempts.add(thirdAttempt);

        AttemptStats secondAttempt = new AttemptStats();
        secondAttempt.setId(2);
        secondAttempt.setName("Three Attempts");
        secondAttempt.setDuration(90);
        attempts.add(secondAttempt);

        AttemptStats firstAttempt = new AttemptStats();
        firstAttempt.setId(1);
        firstAttempt.setName("Three Attempts");
        firstAttempt.setDuration(90);
        attempts.add(firstAttempt);

        AttemptStats stats = new AttemptStats();
        stats.calcStats(attempts);
        assertEquals(3, stats.getTotal());
        assertEquals(3, stats.getPos());
        assertEquals(90, stats.getPb());
        assertEquals(100, stats.getDuration());
        assertFalse(stats.isThisAttemptIsPb());
    }

}
