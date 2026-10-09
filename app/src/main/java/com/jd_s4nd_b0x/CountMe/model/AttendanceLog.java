package com.jd_s4nd_b0x.CountMe.model;

import java.io.Serializable;

public class AttendanceLog implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status {
        PRESENT,
        ABSENT,
        CANCELLED
    }

    private String id;
    private String subjectId;
    private Status status;
    private long timestamp;
    private String note;

    public AttendanceLog() {}

    public AttendanceLog(String id, String subjectId, Status status, long timestamp, String note) {
        this.id = id;
        this.subjectId = subjectId;
        this.status = status;
        this.timestamp = timestamp;
        this.note = note;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
