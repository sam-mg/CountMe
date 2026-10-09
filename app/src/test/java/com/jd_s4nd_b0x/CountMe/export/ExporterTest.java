package com.jd_s4nd_b0x.CountMe.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.jd_s4nd_b0x.CountMe.model.AttendanceLog;
import com.jd_s4nd_b0x.CountMe.model.Subject;
import com.jd_s4nd_b0x.CountMe.repository.AttendanceRepository;
import com.jd_s4nd_b0x.CountMe.repository.LocalAttendanceRepository;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
public class ExporterTest {

    private AttendanceRepository repo;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        repo = LocalAttendanceRepository.getInstance(context);
        repo.setChangeListener(null);
        repo.clearAll();
    }

    private Subject addSubject(String name, int present, int total) {
        Subject s = new Subject(null, name, "C,1", "Core", present, total, 75);
        repo.addSubject(s);
        return s;
    }

    private String export(Exporter.Format format) throws IOException {
        return new String(Exporter.export(repo, format), StandardCharsets.UTF_8);
    }

    @Test
    public void cellEscapesQuotesCommasAndNewlines() {
        assertEquals("plain", Exporter.cell("plain"));
        assertEquals("\"a,b\"", Exporter.cell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", Exporter.cell("say \"hi\""));
        assertEquals("\"l1\nl2\"", Exporter.cell("l1\nl2"));
        assertEquals("", Exporter.cell(null));
    }

    @Test
    public void cellNeutralisesSpreadsheetFormulas() {
        assertEquals("'=SUM(A1)", Exporter.cell("=SUM(A1)"));
        assertEquals("'+1", Exporter.cell("+1"));
        assertEquals("'-1", Exporter.cell("-1"));
        assertEquals("'@cmd", Exporter.cell("@cmd"));
    }

    @Test
    public void csvHasBomSummaryAndHistory() throws Exception {
        Subject s = addSubject("=Evil", 3, 4);
        repo.addLog(new AttendanceLog(null, s.getId(), AttendanceLog.Status.PRESENT, 0L, "ok"));

        String csv = export(Exporter.Format.CSV);

        assertTrue(
                csv.startsWith(
                        (char) 0xFEFF
                                + "Subject,Code,Category,Attended,Missed,Total,Percentage,Target"));
        assertTrue(csv.contains("'=Evil,\"C,1\",Core,3,1,4,75.0,75"));
        assertTrue(csv.contains("Date,Subject,Status,Note"));
        assertTrue(csv.contains(",'=Evil,PRESENT,ok"));
    }

    @Test
    public void csvMarksLogsOfDeletedSubjects() throws Exception {
        repo.addLog(new AttendanceLog(null, "ghost", AttendanceLog.Status.ABSENT, 0L, ""));
        assertTrue(export(Exporter.Format.CSV).contains("(deleted),ABSENT"));
    }

    @Test
    public void jsonIsTheSnapshot() throws Exception {
        addSubject("Maths", 1, 2);
        JSONObject json = new JSONObject(export(Exporter.Format.JSON));
        assertEquals("Maths", json.getJSONArray("subjects").getJSONObject(0).getString("name"));
    }

    @Test
    public void fileNamesCarryTheRightExtension() {
        assertTrue(Exporter.fileName(Exporter.Format.JSON).endsWith(".json"));
        assertTrue(Exporter.fileName(Exporter.Format.CSV).endsWith(".csv"));
        assertTrue(Exporter.fileName(Exporter.Format.PDF).endsWith(".pdf"));
        assertEquals("application/pdf", Exporter.Format.PDF.mime);
    }
}
