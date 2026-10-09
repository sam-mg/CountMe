package com.jd_s4nd_b0x.CountMe.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SubjectTest {

    @Test
    public void defaultTargetIs75() {
        assertEquals(75, new Subject().getTargetPercentage());
    }

    @Test
    public void nonPositiveTargetFallsBackTo75() {
        assertEquals(75, new Subject("i", "n", "", "g", 0, 0, 0).getTargetPercentage());
        assertEquals(75, new Subject("i", "n", "", "g", 0, 0, -5).getTargetPercentage());
    }

    @Test
    public void percentageIsHundredWhenNoClasses() {
        assertEquals(100.0, new Subject("i", "n", "", "g", 0, 0, 75).getAttendancePercentage(), 0);
    }

    @Test
    public void markPresentAndAbsentUpdateCounts() {
        Subject s = new Subject("i", "n", "", "g", 0, 0, 75);
        s.markPresent();
        s.markPresent();
        s.markAbsent();
        assertEquals(2, s.getPresentCount());
        assertEquals(3, s.getTotalClasses());
        assertEquals(1, s.getAbsentCount());
        assertEquals(66.666, s.getAttendancePercentage(), 0.01);
        assertTrue(s.getLastUpdated() > 0);
    }

    @Test
    public void undoRemovesTheMarkedClass() {
        Subject s = new Subject("i", "n", "", "g", 2, 3, 75);
        s.undoLastMark(true);
        assertEquals(1, s.getPresentCount());
        assertEquals(2, s.getTotalClasses());
        s.undoLastMark(false);
        assertEquals(1, s.getPresentCount());
        assertEquals(1, s.getTotalClasses());
    }

    @Test
    public void undoNeverGoesNegative() {
        Subject s = new Subject("i", "n", "", "g", 0, 0, 75);
        s.undoLastMark(true);
        assertEquals(0, s.getTotalClasses());
        assertEquals(0, s.getPresentCount());
    }

    @Test
    public void absentCountNeverNegative() {
        assertEquals(0, new Subject("i", "n", "", "g", 5, 3, 75).getAbsentCount());
    }
}
