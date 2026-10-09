package com.jd_s4nd_b0x.CountMe.util;

import com.jd_s4nd_b0x.CountMe.model.Subject;

public final class AttendanceCalculator {

    private AttendanceCalculator() {}

    /**
     * Returns the number of consecutive classes the student can safely miss while staying above or
     * equal to target percentage.
     */
    public static int getSafeMissCount(Subject subject) {
        int present = subject.getPresentCount();
        int total = subject.getTotalClasses();
        double target = subject.getTargetPercentage() / 100.0;

        if (total == 0 || present == 0) {
            return 0;
        }
        double currentPct = (present * 100.0) / total;
        if (currentPct < subject.getTargetPercentage()) {
            return 0;
        }

        // Solve: present / (total + x) >= target => x <= (present / target) - total
        int safeMiss = (int) Math.floor((present / target) - total);
        return Math.max(0, safeMiss);
    }

    /**
     * Returns the number of consecutive classes the student must attend to reach target percentage.
     */
    public static int getRequiredClassesToAttend(Subject subject) {
        int present = subject.getPresentCount();
        int total = subject.getTotalClasses();
        double target = subject.getTargetPercentage() / 100.0;

        if (target >= 1.0) {
            target = 0.999;
        }
        double currentPct = total == 0 ? 100.0 : (present * 100.0) / total;
        if (currentPct >= subject.getTargetPercentage()) {
            return 0;
        }

        // Solve: (present + x) / (total + x) >= target => x >= (target * total - present) / (1 -
        // target)
        double needed = (target * total - present) / (1.0 - target);
        return (int) Math.ceil(needed);
    }
}
