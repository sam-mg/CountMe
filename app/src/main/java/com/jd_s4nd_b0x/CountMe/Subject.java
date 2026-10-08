package com.jd_s4nd_b0x.CountMe.model;

import java.io.Serializable;

public class Subject implements Serializable {
    private String id;
    private String name;
    private String code;
    private String category;
    private int presentCount;
    private int totalClasses;
    private int targetPercentage; // Default e.g. 75
    private long lastUpdated;

    public Subject() {
        this.targetPercentage = 75;
    }

    public Subject(String id, String name, String code, String category, int presentCount, int totalClasses, int targetPercentage) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.category = category;
        this.presentCount = presentCount;
        this.totalClasses = totalClasses;
        this.targetPercentage = targetPercentage <= 0 ? 75 : targetPercentage;
        this.lastUpdated = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getPresentCount() { return presentCount; }
    public void setPresentCount(int presentCount) { this.presentCount = presentCount; }

    public int getTotalClasses() { return totalClasses; }
    public void setTotalClasses(int totalClasses) { this.totalClasses = totalClasses; }

    public int getTargetPercentage() { return targetPercentage; }
    public void setTargetPercentage(int targetPercentage) { this.targetPercentage = targetPercentage; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    public int getAbsentCount() {
        return Math.max(0, totalClasses - presentCount);
    }

    public double getAttendancePercentage() {
        if (totalClasses == 0) return 100.0;
        return (presentCount * 100.0) / totalClasses;
    }

    public void markPresent() {
        this.presentCount++;
        this.totalClasses++;
        this.lastUpdated = System.currentTimeMillis();
    }

    public void markAbsent() {
        this.totalClasses++;
        this.lastUpdated = System.currentTimeMillis();
    }

    public void undoLastMark(boolean wasPresent) {
        if (totalClasses > 0) {
            totalClasses--;
            if (wasPresent && presentCount > 0) {
                presentCount--;
            }
            this.lastUpdated = System.currentTimeMillis();
        }
    }
}
