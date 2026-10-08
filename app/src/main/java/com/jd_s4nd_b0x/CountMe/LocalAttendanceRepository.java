package com.jd_s4nd_b0x.CountMe.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.jd_s4nd_b0x.CountMe.model.AttendanceLog;
import com.jd_s4nd_b0x.CountMe.model.Subject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LocalAttendanceRepository implements AttendanceRepository {

    private static final String PREF_NAME = "AttendancePrefs";
    private static final String KEY_SUBJECTS = "key_subjects";
    private static final String KEY_LOGS = "key_logs";

    private static LocalAttendanceRepository instance;
    private final SharedPreferences prefs;

    private LocalAttendanceRepository(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_SUBJECTS)) {
            seedSampleData();
        }
    }

    public static synchronized LocalAttendanceRepository getInstance(Context context) {
        if (instance == null) {
            instance = new LocalAttendanceRepository(context);
        }
        return instance;
    }

    private void seedSampleData() {
        List<Subject> initial = new ArrayList<>();
        initial.add(new Subject(UUID.randomUUID().toString(), "Data Structures & Algorithms", "CS301", "Core", 18, 20, 75));
        initial.add(new Subject(UUID.randomUUID().toString(), "Operating Systems", "CS302", "Core", 14, 20, 75));
        initial.add(new Subject(UUID.randomUUID().toString(), "Computer Networks", "CS303", "Theory", 16, 18, 75));
        initial.add(new Subject(UUID.randomUUID().toString(), "DBMS Laboratory", "CS304L", "Lab", 10, 10, 80));
        initial.add(new Subject(UUID.randomUUID().toString(), "Software Engineering", "CS305", "Theory", 11, 15, 75));

        saveSubjects(initial);
    }

    @Override
    public List<Subject> getAllSubjects() {
        List<Subject> list = new ArrayList<>();
        String json = prefs.getString(KEY_SUBJECTS, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Subject s = new Subject(
                        obj.getString("id"),
                        obj.getString("name"),
                        obj.optString("code", ""),
                        obj.optString("category", "General"),
                        obj.getInt("presentCount"),
                        obj.getInt("totalClasses"),
                        obj.optInt("targetPercentage", 75)
                );
                s.setLastUpdated(obj.optLong("lastUpdated", System.currentTimeMillis()));
                list.add(s);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public Subject getSubjectById(String id) {
        for (Subject s : getAllSubjects()) {
            if (s.getId().equals(id)) return s;
        }
        return null;
    }

    @Override
    public void addSubject(Subject subject) {
        if (subject.getId() == null || subject.getId().isEmpty()) {
            subject.setId(UUID.randomUUID().toString());
        }
        List<Subject> subjects = getAllSubjects();
        subjects.add(subject);
        saveSubjects(subjects);
    }

    @Override
    public void updateSubject(Subject subject) {
        List<Subject> subjects = getAllSubjects();
        for (int i = 0; i < subjects.size(); i++) {
            if (subjects.get(i).getId().equals(subject.getId())) {
                subjects.set(i, subject);
                break;
            }
        }
        saveSubjects(subjects);
    }

    @Override
    public void deleteSubject(String id) {
        List<Subject> subjects = getAllSubjects();
        List<Subject> updated = new ArrayList<>();
        for (Subject s : subjects) {
            if (!s.getId().equals(id)) {
                updated.add(s);
            }
        }
        saveSubjects(updated);
    }

    @Override
    public List<AttendanceLog> getLogsForSubject(String subjectId) {
        List<AttendanceLog> list = new ArrayList<>();
        for (AttendanceLog log : getAllLogsInternal()) {
            if (log.getSubjectId().equals(subjectId)) {
                list.add(log);
            }
        }
        return list;
    }

    @Override
    public List<AttendanceLog> getAllLogs() {
        return getAllLogsInternal();
    }

    @Override
    public void addLog(AttendanceLog log) {
        if (log.getId() == null || log.getId().isEmpty()) {
            log.setId(UUID.randomUUID().toString());
        }
        List<AttendanceLog> allLogs = getAllLogsInternal();
        allLogs.add(0, log); // Newest first
        saveLogsInternal(allLogs);
    }

    @Override
    public void deleteLog(String logId) {
        List<AttendanceLog> allLogs = getAllLogsInternal();
        List<AttendanceLog> updated = new ArrayList<>();
        for (AttendanceLog l : allLogs) {
            if (!l.getId().equals(logId)) {
                updated.add(l);
            }
        }
        saveLogsInternal(updated);
    }

    private List<AttendanceLog> getAllLogsInternal() {
        List<AttendanceLog> list = new ArrayList<>();
        String json = prefs.getString(KEY_LOGS, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                AttendanceLog log = new AttendanceLog(
                        obj.getString("id"),
                        obj.getString("subjectId"),
                        AttendanceLog.Status.valueOf(obj.getString("status")),
                        obj.getLong("timestamp"),
                        obj.optString("note", "")
                );
                list.add(log);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    private void saveLogsInternal(List<AttendanceLog> logs) {
        JSONArray array = new JSONArray();
        try {
            for (AttendanceLog l : logs) {
                JSONObject obj = new JSONObject();
                obj.put("id", l.getId());
                obj.put("subjectId", l.getSubjectId());
                obj.put("status", l.getStatus().name());
                obj.put("timestamp", l.getTimestamp());
                obj.put("note", l.getNote() == null ? "" : l.getNote());
                array.put(obj);
            }
            prefs.edit().putString(KEY_LOGS, array.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void saveSubjects(List<Subject> list) {
        JSONArray array = new JSONArray();
        try {
            for (Subject s : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", s.getId());
                obj.put("name", s.getName());
                obj.put("code", s.getCode());
                obj.put("category", s.getCategory());
                obj.put("presentCount", s.getPresentCount());
                obj.put("totalClasses", s.getTotalClasses());
                obj.put("targetPercentage", s.getTargetPercentage());
                obj.put("lastUpdated", s.getLastUpdated());
                array.put(obj);
            }
            prefs.edit().putString(KEY_SUBJECTS, array.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
