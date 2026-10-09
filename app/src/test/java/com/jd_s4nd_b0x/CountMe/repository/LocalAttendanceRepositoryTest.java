package com.jd_s4nd_b0x.CountMe.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.jd_s4nd_b0x.CountMe.model.AttendanceLog;
import com.jd_s4nd_b0x.CountMe.model.Subject;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class LocalAttendanceRepositoryTest {

    private AttendanceRepository repo;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        repo = LocalAttendanceRepository.getInstance(context);
        repo.setChangeListener(null);
        repo.clearAll();
    }

    private Subject newSubject(String name) {
        Subject s = new Subject(null, name, "C1", "Core", 1, 2, 75);
        repo.addSubject(s);
        return s;
    }

    @Test
    public void startsEmptyWithoutSampleData() {
        assertTrue(repo.getAllSubjects().isEmpty());
        assertTrue(repo.getAllLogs().isEmpty());
        assertEquals(0L, repo.getLastModified());
    }

    @Test
    public void addAssignsIdAndPersists() {
        Subject s = newSubject("Maths");
        assertNotNull(s.getId());
        assertEquals(1, repo.getAllSubjects().size());
        assertEquals("Maths", repo.getSubjectById(s.getId()).getName());
        assertEquals(2, repo.getSubjectById(s.getId()).getTotalClasses());
    }

    @Test
    public void getByUnknownIdIsNull() {
        assertNull(repo.getSubjectById("nope"));
    }

    @Test
    public void updateReplacesTheStoredSubject() {
        Subject s = newSubject("Maths");
        s.setName("Physics");
        s.setTargetPercentage(80);
        repo.updateSubject(s);
        assertEquals("Physics", repo.getSubjectById(s.getId()).getName());
        assertEquals(80, repo.getSubjectById(s.getId()).getTargetPercentage());
    }

    @Test
    public void deleteRemovesOnlyThatSubject() {
        Subject a = newSubject("A");
        Subject b = newSubject("B");
        repo.deleteSubject(a.getId());
        assertEquals(1, repo.getAllSubjects().size());
        assertEquals(b.getId(), repo.getAllSubjects().get(0).getId());
    }

    @Test
    public void logsAreNewestFirstAndFilteredBySubject() {
        Subject a = newSubject("A");
        Subject b = newSubject("B");
        repo.addLog(new AttendanceLog(null, a.getId(), AttendanceLog.Status.PRESENT, 1L, "first"));
        repo.addLog(new AttendanceLog(null, b.getId(), AttendanceLog.Status.ABSENT, 2L, null));
        repo.addLog(new AttendanceLog(null, a.getId(), AttendanceLog.Status.ABSENT, 3L, "third"));

        assertEquals(3, repo.getAllLogs().size());
        assertEquals("third", repo.getAllLogs().get(0).getNote());
        assertEquals(2, repo.getLogsForSubject(a.getId()).size());
        assertEquals("", repo.getLogsForSubject(b.getId()).get(0).getNote());
    }

    @Test
    public void deleteLogRemovesIt() {
        Subject a = newSubject("A");
        AttendanceLog log =
                new AttendanceLog(null, a.getId(), AttendanceLog.Status.PRESENT, 1L, "");
        repo.addLog(log);
        repo.deleteLog(log.getId());
        assertTrue(repo.getAllLogs().isEmpty());
    }

    @Test
    public void deleteAllSubjectsClearsSubjectsAndLogs() {
        Subject a = newSubject("A");
        repo.addLog(new AttendanceLog(null, a.getId(), AttendanceLog.Status.PRESENT, 1L, ""));
        repo.deleteAllSubjects();
        assertTrue(repo.getAllSubjects().isEmpty());
        assertTrue(repo.getAllLogs().isEmpty());
        assertTrue(repo.getLastModified() > 0);
    }

    @Test
    public void changeListenerFiresOnEveryMutation() {
        AtomicInteger calls = new AtomicInteger();
        repo.setChangeListener(calls::incrementAndGet);
        Subject s = newSubject("A");
        repo.updateSubject(s);
        repo.addLog(new AttendanceLog(null, s.getId(), AttendanceLog.Status.PRESENT, 1L, ""));
        repo.deleteSubject(s.getId());
        repo.setChangeListener(null);
        assertTrue(calls.get() >= 4);
    }

    @Test
    public void snapshotRoundTripsAndDoesNotFireListener() throws Exception {
        Subject s = newSubject("Maths");
        repo.addLog(new AttendanceLog(null, s.getId(), AttendanceLog.Status.PRESENT, 5L, "n"));
        String snapshot = repo.exportSnapshot();

        JSONObject json = new JSONObject(snapshot);
        assertEquals("CountMe", json.getString("app"));
        assertEquals(1, json.getInt("version"));
        assertEquals(1, json.getJSONArray("subjects").length());

        repo.clearAll();
        assertTrue(repo.getAllSubjects().isEmpty());

        AtomicInteger calls = new AtomicInteger();
        repo.setChangeListener(calls::incrementAndGet);
        repo.importSnapshot(snapshot);
        repo.setChangeListener(null);

        assertEquals(json.getLong("modified"), repo.getLastModified());
        assertEquals(0, calls.get());
        assertEquals("Maths", repo.getAllSubjects().get(0).getName());
        assertEquals("n", repo.getAllLogs().get(0).getNote());
    }

    @Test
    public void importToleratesMissingArrays() throws Exception {
        repo.importSnapshot("{\"app\":\"CountMe\"}");
        assertTrue(repo.getAllSubjects().isEmpty());
        assertFalse(repo.exportSnapshot().isEmpty());
    }

    @Test(expected = org.json.JSONException.class)
    public void importRejectsGarbage() throws Exception {
        repo.importSnapshot("not json");
    }
}
