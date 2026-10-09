package com.jd_s4nd_b0x.CountMe.drive;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Minimal Drive v3 REST client. All calls are blocking: run off the main thread. */
public class DriveClient {

    public static class DriveException extends IOException {
        private static final long serialVersionUID = 1L;

        public final int code;

        public DriveException(int code, String msg) {
            super("Drive HTTP " + code + ": " + msg);
            this.code = code;
        }
    }

    public static final String ROOT_FOLDER = "CountMe";
    public static final String EXPORTS_FOLDER = "Exports";
    public static final String DATA_FILE = "countme_data.json";
    private static final String FOLDER_MIME = "application/vnd.google-apps.folder";
    private static final String METHOD_PATCH = "PATCH";
    private static final String DEFAULT_API = "https://www.googleapis.com/drive/v3";
    private static final String DEFAULT_UPLOAD = "https://www.googleapis.com/upload/drive/v3";

    private final String token;
    private final String api;
    private final String upload;

    public DriveClient(String accessToken) {
        this(accessToken, DEFAULT_API, DEFAULT_UPLOAD);
    }

    /** Test seam: lets unit tests point the client at a local mock server. */
    DriveClient(String accessToken, String apiBase, String uploadBase) {
        this.token = accessToken;
        this.api = apiBase;
        this.upload = uploadBase;
    }

    /**
     * Fetches the signed-in user.
     *
     * @return {displayName, emailAddress}
     */
    public String[] getUser() throws IOException {
        try {
            JSONObject user =
                    new JSONObject(
                                    request(
                                            "GET",
                                            api + "/about?fields=user(displayName,emailAddress)",
                                            null,
                                            null))
                            .getJSONObject("user");
            return new String[] {user.optString("displayName"), user.optString("emailAddress")};
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
    }

    public String ensureRootFolder() throws IOException {
        return ensureFolder(ROOT_FOLDER, null);
    }

    public String ensureExportsFolder() throws IOException {
        return ensureFolder(EXPORTS_FOLDER, ensureRootFolder());
    }

    private String ensureFolder(String name, String parentId) throws IOException {
        String id = findId(name, parentId, FOLDER_MIME);
        if (id != null) {
            return id;
        }
        try {
            JSONObject meta = new JSONObject().put("name", name).put("mimeType", FOLDER_MIME);
            if (parentId != null) {
                meta.put("parents", new JSONArray().put(parentId));
            }
            return new JSONObject(
                            request(
                                    "POST",
                                    api + "/files?fields=id",
                                    "application/json",
                                    meta.toString().getBytes(StandardCharsets.UTF_8)))
                    .getString("id");
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
    }

    /** Finds a non-trashed file created by this app (drive.file scope hides everything else). */
    public String findId(String name, String parentId, String mimeType) throws IOException {
        StringBuilder q =
                new StringBuilder("name='")
                        .append(name.replace("'", "\\'"))
                        .append("' and trashed=false");
        if (mimeType != null) {
            q.append(" and mimeType='").append(mimeType).append('\'');
        }
        if (parentId != null) {
            q.append(" and '").append(parentId).append("' in parents");
        }
        String url = api + "/files?spaces=drive&fields=files(id)&q=" + enc(q.toString());
        try {
            JSONArray files = new JSONObject(request("GET", url, null, null)).getJSONArray("files");
            return files.length() == 0 ? null : files.getJSONObject(0).getString("id");
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
    }

    public String downloadText(String fileId) throws IOException {
        return request("GET", api + "/files/" + fileId + "?alt=media", null, null);
    }

    /** Creates or overwrites a file by name inside parent. */
    public String upsert(String name, String mimeType, String parentId, byte[] content)
            throws IOException {
        String existing = findId(name, parentId, null);
        if (existing != null) {
            request(
                    METHOD_PATCH,
                    upload + "/files/" + existing + "?uploadType=media",
                    mimeType,
                    content);
            return existing;
        }
        try {
            JSONObject meta =
                    new JSONObject()
                            .put("name", name)
                            .put("mimeType", mimeType)
                            .put("parents", new JSONArray().put(parentId));
            String boundary = "countme_" + System.nanoTime();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(
                    ("--"
                                    + boundary
                                    + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
                                    + meta
                                    + "\r\n--"
                                    + boundary
                                    + "\r\nContent-Type: "
                                    + mimeType
                                    + "\r\n\r\n")
                            .getBytes(StandardCharsets.UTF_8));
            body.write(content);
            body.write(("\r\n--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
            String resp =
                    request(
                            "POST",
                            upload + "/files?uploadType=multipart&fields=id",
                            "multipart/related; boundary=" + boundary,
                            body.toByteArray());
            return new JSONObject(resp).getString("id");
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
    }

    private String request(String method, String url, String contentType, byte[] body)
            throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestMethod(METHOD_PATCH.equals(method) ? "POST" : method);
            if (METHOD_PATCH.equals(method)) {
                c.setRequestProperty("X-HTTP-Method-Override", METHOD_PATCH);
            }
            c.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", contentType);
                c.setFixedLengthStreamingMode(body.length);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body);
                }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "" : readAll(in);
            if (code >= 400) {
                throw new DriveException(code, text);
            }
            return text;
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream is = in;
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    // URLEncoder.encode(String, Charset) needs API 33; minSdk is 30, so the charset-name overload
    // stays.
    @SuppressWarnings({"PMD.UseStandardCharsets", "JdkObsolete"})
    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
