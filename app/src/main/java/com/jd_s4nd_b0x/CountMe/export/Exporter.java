package com.jd_s4nd_b0x.CountMe.export;

import com.jd_s4nd_b0x.CountMe.model.AttendanceLog;
import com.jd_s4nd_b0x.CountMe.model.Subject;
import com.jd_s4nd_b0x.CountMe.repository.AttendanceRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Exporter {

    public enum Format {
        JSON("json", "application/json"),
        CSV("csv", "text/csv"),
        PDF("pdf", "application/pdf");

        public final String extension;
        public final String mime;

        Format(String extension, String mime) {
            this.extension = extension;
            this.mime = mime;
        }
    }

    /** Byte-order mark: makes Excel treat the CSV as UTF-8. */
    private static final char UTF8_BOM = (char) 0xFEFF;

    private static final String STAMP_PATTERN = "yyyy-MM-dd HH:mm";

    private Exporter() {}

    private static String format(DateTimeFormatter formatter, long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter);
    }

    public static String fileName(Format format) {
        String stamp =
                LocalDateTime.now(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.US));
        return "countme-" + stamp + "." + format.extension;
    }

    public static byte[] export(AttendanceRepository repo, Format format) throws IOException {
        switch (format) {
            case JSON:
                return repo.exportSnapshot().getBytes(StandardCharsets.UTF_8);
            case CSV:
                return csv(repo).getBytes(StandardCharsets.UTF_8);
            default:
                return PdfReport.render(repo);
        }
    }

    // ---- CSV -----------------------------------------------------------------------------

    private static String csv(AttendanceRepository repo) {
        List<Subject> subjects = repo.getAllSubjects();
        Map<String, Subject> byId = new HashMap<>();
        StringBuilder sb = new StringBuilder().append(UTF8_BOM); // BOM so Excel reads UTF-8
        sb.append("Subject,Code,Category,Attended,Missed,Total,Percentage,Target\r\n");
        for (Subject s : subjects) {
            byId.put(s.getId(), s);
            row(
                    sb,
                    s.getName(),
                    s.getCode(),
                    s.getCategory(),
                    String.valueOf(s.getPresentCount()),
                    String.valueOf(s.getAbsentCount()),
                    String.valueOf(s.getTotalClasses()),
                    String.format(Locale.US, "%.1f", s.getAttendancePercentage()),
                    String.valueOf(s.getTargetPercentage()));
        }
        sb.append("\r\nDate,Subject,Status,Note\r\n");
        DateTimeFormatter df = DateTimeFormatter.ofPattern(STAMP_PATTERN, Locale.US);
        for (AttendanceLog l : repo.getAllLogs()) {
            Subject s = byId.get(l.getSubjectId());
            row(
                    sb,
                    format(df, l.getTimestamp()),
                    s == null ? "(deleted)" : s.getName(),
                    l.getStatus().name(),
                    l.getNote());
        }
        return sb.toString();
    }

    private static void row(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cell(cells[i]));
        }
        sb.append("\r\n");
    }

    static String cell(String raw) {
        if (raw == null) {
            return "";
        }
        // Neutralise spreadsheet formula injection.
        String v = !raw.isEmpty() && "=+-@".indexOf(raw.charAt(0)) >= 0 ? "'" + raw : raw;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
