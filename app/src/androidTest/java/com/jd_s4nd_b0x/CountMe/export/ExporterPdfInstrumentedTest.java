package com.jd_s4nd_b0x.CountMe.export;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.jd_s4nd_b0x.CountMe.model.AttendanceLog;
import com.jd_s4nd_b0x.CountMe.model.Subject;
import com.jd_s4nd_b0x.CountMe.repository.AttendanceRepository;
import com.jd_s4nd_b0x.CountMe.repository.LocalAttendanceRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * PDF generation needs the real Android graphics stack, which JVM unit tests (Robolectric) cannot
 * provide, so it is verified on a device or emulator.
 */
@RunWith(AndroidJUnit4.class)
public class ExporterPdfInstrumentedTest {

    private AttendanceRepository repo;
    private String savedSnapshot;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        repo = LocalAttendanceRepository.getInstance(context);
        repo.setChangeListener(null);
        savedSnapshot = repo.exportSnapshot();
        repo.clearAll();
    }

    @After
    public void tearDown() throws Exception {
        repo.importSnapshot(savedSnapshot); // leave the device's real data untouched
    }

    @Test
    public void emptyReportIsAValidPdf() throws IOException {
        byte[] pdf = Exporter.export(repo, Exporter.Format.PDF);
        assertTrue(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF-"));
    }

    @Test
    public void longHistoryProducesMorePagesThanAShortOne() throws IOException {
        Subject s = new Subject(null, "Maths", "M1", "Core", 1, 100, 75);
        repo.addSubject(s);
        byte[] shortPdf = Exporter.export(repo, Exporter.Format.PDF);
        for (int i = 0; i < 150; i++) {
            repo.addLog(
                    new AttendanceLog(null, s.getId(), AttendanceLog.Status.PRESENT, i, "note"));
        }
        byte[] longPdf = Exporter.export(repo, Exporter.Format.PDF);
        assertTrue(longPdf.length > shortPdf.length);
    }
}
