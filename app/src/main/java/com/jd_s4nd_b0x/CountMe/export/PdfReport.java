package com.jd_s4nd_b0x.CountMe.export;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import com.jd_s4nd_b0x.CountMe.model.AttendanceLog;
import com.jd_s4nd_b0x.CountMe.model.Subject;
import com.jd_s4nd_b0x.CountMe.repository.AttendanceRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders the attendance report as a paginated PDF. It needs the real Android graphics stack, so it
 * is verified by the instrumented test rather than by JVM unit tests.
 */
final class PdfReport {

    private static final String STAMP_PATTERN = "yyyy-MM-dd HH:mm";

    private PdfReport() {}

    private static String format(DateTimeFormatter formatter, long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter);
    }

    private static final int PAGE_W = 595;
    private static final int PAGE_H = 842;
    private static final int MARGIN = 40;

    static byte[] render(AttendanceRepository repo) throws IOException {
        List<Subject> subjects = repo.getAllSubjects();
        Map<String, String> names = new HashMap<>();
        for (Subject s : subjects) {
            names.put(s.getId(), s.getName());
        }

        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
        head.setTextSize(11);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
        body.setTextSize(10);

        PdfDocument doc = new PdfDocument();
        Pager p = new Pager(doc);

        p.canvas().drawText("CountMe Attendance Report", MARGIN, p.y, title);
        p.y += 18;
        p.canvas()
                .drawText(
                        "Generated "
                                + LocalDateTime.now(ZoneId.systemDefault())
                                        .format(
                                                DateTimeFormatter.ofPattern(
                                                        STAMP_PATTERN, Locale.getDefault())),
                        MARGIN,
                        p.y,
                        body);
        p.y += 28;

        int attended = 0;
        int total = 0;
        for (Subject s : subjects) {
            attended += s.getPresentCount();
            total += s.getTotalClasses();
        }
        double overall = total == 0 ? 100.0 : attended * 100.0 / total;
        p.canvas()
                .drawText(
                        String.format(
                                Locale.getDefault(),
                                "Overall: %.1f%%  (%d of %d classes attended)",
                                overall,
                                attended,
                                total),
                        MARGIN,
                        p.y,
                        head);
        p.y += 26;

        int[] x = {MARGIN, 230, 300, 360, 420, 490};
        p.rowTop(head, x, "Subject", "Attended", "Missed", "Total", "Percent", "Target");
        for (Subject s : subjects) {
            p.row(
                    body,
                    x,
                    ellipsize(s.getName(), 30),
                    String.valueOf(s.getPresentCount()),
                    String.valueOf(s.getAbsentCount()),
                    String.valueOf(s.getTotalClasses()),
                    String.format(Locale.getDefault(), "%.1f%%", s.getAttendancePercentage()),
                    s.getTargetPercentage() + "%");
        }

        p.y += 24;
        p.ensure(40);
        p.canvas().drawText("History", MARGIN, p.y, title);
        p.y += 22;
        int[] lx = {MARGIN, 160, 360, 440};
        p.rowTop(head, lx, "Date", "Subject", "Status", "Note");
        DateTimeFormatter df = DateTimeFormatter.ofPattern(STAMP_PATTERN, Locale.getDefault());
        for (AttendanceLog l : repo.getAllLogs()) {
            String name =
                    names.containsKey(l.getSubjectId()) ? names.get(l.getSubjectId()) : "(deleted)";
            p.row(
                    body,
                    lx,
                    format(df, l.getTimestamp()),
                    ellipsize(name, 32),
                    l.getStatus().name(),
                    ellipsize(l.getNote() == null ? "" : l.getNote(), 20));
        }

        p.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            doc.writeTo(out);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    private static String ellipsize(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** Tracks the current page and the y cursor; opens a new page when the content overflows. */
    private static final class Pager {
        private final PdfDocument doc;
        private PdfDocument.Page page;
        private int number = 1;
        int y;
        private Paint repeatHead;
        private int[] repeatX;
        private String[] repeatCells;

        Pager(PdfDocument doc) {
            this.doc = doc;
            this.page = startPage();
            this.y = MARGIN + 10;
        }

        private PdfDocument.Page startPage() {
            return doc.startPage(new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, number).create());
        }

        android.graphics.Canvas canvas() {
            return page.getCanvas();
        }

        void newPage() {
            doc.finishPage(page);
            number++;
            page = startPage();
            y = MARGIN + 10;
        }

        void ensure(int needed) {
            if (y + needed > PAGE_H - MARGIN) {
                newPage();
                if (repeatCells != null) {
                    drawRow(repeatHead, repeatX, repeatCells);
                }
            }
        }

        void rowTop(Paint paint, int[] x, String... cells) {
            repeatCells = null;
            ensure(30);
            repeatHead = paint;
            repeatX = x.clone();
            repeatCells = cells.clone();
            drawRow(paint, x, cells);
        }

        void row(Paint paint, int[] x, String... cells) {
            ensure(16);
            drawRow(paint, x, cells);
        }

        private void drawRow(Paint paint, int[] x, String... cells) {
            for (int i = 0; i < cells.length; i++) {
                canvas().drawText(cells[i], x[i], y, paint);
            }
            y += 16;
        }

        void finish() {
            doc.finishPage(page);
        }
    }
}
