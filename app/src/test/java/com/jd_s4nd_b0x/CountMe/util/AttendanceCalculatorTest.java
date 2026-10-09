package com.jd_s4nd_b0x.CountMe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.jd_s4nd_b0x.CountMe.model.Subject;

import org.junit.Test;

public class AttendanceCalculatorTest {

    private static Subject subject(int present, int total, int target) {
        return new Subject("id", "Name", "C1", "Core", present, total, target);
    }

    @Test
    public void safeMiss_isZeroWhenNothingRecorded() {
        assertEquals(0, AttendanceCalculator.getSafeMissCount(subject(0, 0, 75)));
    }

    @Test
    public void safeMiss_isZeroWhenNeverPresent() {
        assertEquals(0, AttendanceCalculator.getSafeMissCount(subject(0, 10, 75)));
    }

    @Test
    public void safeMiss_isZeroWhenBelowTarget() {
        assertEquals(0, AttendanceCalculator.getSafeMissCount(subject(7, 10, 75)));
    }

    @Test
    public void safeMiss_countsClassesThatKeepTheTarget() {
        // 18/20 = 90%. Missing x more keeps >= 75% while 18 / (20 + x) >= 0.75, so x <= 4.
        assertEquals(4, AttendanceCalculator.getSafeMissCount(subject(18, 20, 75)));
    }

    @Test
    public void safeMiss_isZeroExactlyAtTargetWithNoSlack() {
        assertEquals(0, AttendanceCalculator.getSafeMissCount(subject(3, 4, 75)));
    }

    @Test
    public void required_isZeroWhenAtOrAboveTarget() {
        assertEquals(0, AttendanceCalculator.getRequiredClassesToAttend(subject(15, 20, 75)));
        assertEquals(0, AttendanceCalculator.getRequiredClassesToAttend(subject(0, 0, 75)));
    }

    @Test
    public void required_countsConsecutiveClassesNeeded() {
        // 10/20 = 50%. (10 + x) / (20 + x) >= 0.75 needs x >= 20.
        assertEquals(20, AttendanceCalculator.getRequiredClassesToAttend(subject(10, 20, 75)));
    }

    @Test
    public void required_handlesAHundredPercentTargetWithoutDividingByZero() {
        // 100% is capped at 99.9% internally, so the answer is finite and positive.
        assertTrue(AttendanceCalculator.getRequiredClassesToAttend(subject(9, 10, 100)) > 0);
    }
}
