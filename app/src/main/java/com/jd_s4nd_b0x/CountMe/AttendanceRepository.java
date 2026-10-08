package com.jd_s4nd_b0x.CountMe.repository;

import com.jd_s4nd_b0x.CountMe.model.AttendanceLog;
import com.jd_s4nd_b0x.CountMe.model.Subject;

import java.util.List;

public interface AttendanceRepository {
    List<Subject> getAllSubjects();
    Subject getSubjectById(String id);
    void addSubject(Subject subject);
    void updateSubject(Subject subject);
    void deleteSubject(String id);

    List<AttendanceLog> getLogsForSubject(String subjectId);
    List<AttendanceLog> getAllLogs();
    void addLog(AttendanceLog log);
    void deleteLog(String logId);
}
