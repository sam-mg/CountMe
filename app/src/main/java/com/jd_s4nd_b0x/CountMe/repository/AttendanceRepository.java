package com.jd_s4nd_b0x.CountMe.repository;

import com.jd_s4nd_b0x.CountMe.model.AttendanceLog;
import com.jd_s4nd_b0x.CountMe.model.Subject;

import org.json.JSONException;

import java.util.List;

public interface AttendanceRepository {
    List<Subject> getAllSubjects();

    Subject getSubjectById(String id);

    void addSubject(Subject subject);

    void updateSubject(Subject subject);

    void deleteSubject(String id);

    void deleteAllSubjects();

    List<AttendanceLog> getLogsForSubject(String subjectId);

    List<AttendanceLog> getAllLogs();

    void addLog(AttendanceLog log);

    void deleteLog(String logId);

    /** Whole dataset as one JSON document (the format stored in the user's Drive). */
    String exportSnapshot();

    /** Replaces local data with a snapshot. Does not fire change listeners. */
    void importSnapshot(String json) throws JSONException;

    long getLastModified();

    void clearAll();

    void setChangeListener(Runnable listener);
}
