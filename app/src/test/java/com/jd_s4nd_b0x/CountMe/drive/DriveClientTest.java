package com.jd_s4nd_b0x.CountMe.drive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
public class DriveClientTest {

    private MockWebServer server;
    private DriveClient client;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String base = server.url("/").toString();
        client = new DriveClient("tok", base + "drive", base + "upload");
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void getUserParsesNameAndEmailAndSendsBearerToken() throws Exception {
        server.enqueue(
                new MockResponse()
                        .setBody(
                                "{\"user\":{\"displayName\":\"Sam\",\"emailAddress\":\"s@x.com\"}}"));
        String[] user = client.getUser();
        assertEquals("Sam", user[0]);
        assertEquals("s@x.com", user[1]);
        RecordedRequest r = server.takeRequest();
        assertEquals("Bearer tok", r.getHeader("Authorization"));
        assertTrue(r.getPath().startsWith("/drive/about"));
    }

    @Test
    public void findIdReturnsNullWhenNothingMatches() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"files\":[]}"));
        assertNull(client.findId("x.json", "parent", null));
    }

    // URLDecoder.decode(String, Charset) needs API 33; the app targets minSdk 30.
    @SuppressWarnings({"JdkObsolete", "PMD.UseStandardCharsets"})
    @Test
    public void findIdEscapesQuotesInTheName() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"files\":[{\"id\":\"abc\"}]}"));
        assertEquals("abc", client.findId("it's.json", "p", "application/json"));
        String path = URLDecoder.decode(server.takeRequest().getPath(), "UTF-8");
        assertTrue(path, path.contains("name='it\\'s.json'"));
        assertTrue(path, path.contains("'p' in parents"));
        assertTrue(path, path.contains("trashed=false"));
    }

    @Test
    public void ensureRootFolderReusesAnExistingFolder() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"files\":[{\"id\":\"root1\"}]}"));
        assertEquals("root1", client.ensureRootFolder());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void ensureRootFolderCreatesItWhenMissing() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"files\":[]}"));
        server.enqueue(new MockResponse().setBody("{\"id\":\"new1\"}"));
        assertEquals("new1", client.ensureRootFolder());
        server.takeRequest();
        RecordedRequest create = server.takeRequest();
        assertEquals("POST", create.getMethod());
        assertTrue(create.getBody().readUtf8().contains("vnd.google-apps.folder"));
    }

    @Test
    public void exportsFolderLivesInsideTheRootFolder() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"files\":[{\"id\":\"root1\"}]}"));
        server.enqueue(new MockResponse().setBody("{\"files\":[]}"));
        server.enqueue(new MockResponse().setBody("{\"id\":\"exp1\"}"));
        assertEquals("exp1", client.ensureExportsFolder());
        server.takeRequest();
        server.takeRequest();
        assertTrue(server.takeRequest().getBody().readUtf8().contains("\"parents\":[\"root1\"]"));
    }

    @Test
    public void downloadReturnsTheBody() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"hello\":1}"));
        assertEquals("{\"hello\":1}", client.downloadText("f1"));
        assertTrue(server.takeRequest().getPath().contains("/files/f1?alt=media"));
    }

    @Test
    public void upsertCreatesWithMultipartWhenFileIsNew() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"files\":[]}"));
        server.enqueue(new MockResponse().setBody("{\"id\":\"n1\"}"));
        String id =
                client.upsert(
                        "d.json", "application/json", "p", "DATA".getBytes(StandardCharsets.UTF_8));
        assertEquals("n1", id);
        server.takeRequest();
        RecordedRequest up = server.takeRequest();
        assertEquals("POST", up.getMethod());
        assertTrue(up.getPath().contains("uploadType=multipart"));
        assertTrue(up.getHeader("Content-Type").startsWith("multipart/related; boundary="));
        String body = up.getBody().readUtf8();
        assertTrue(body.contains("\"name\":\"d.json\""));
        assertTrue(body.contains("DATA"));
    }

    @Test
    public void upsertOverwritesTheExistingFile() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"files\":[{\"id\":\"e1\"}]}"));
        server.enqueue(new MockResponse().setBody("{}"));
        assertEquals("e1", client.upsert("d.json", "application/json", "p", new byte[] {1}));
        server.takeRequest();
        RecordedRequest patch = server.takeRequest();
        assertEquals("PATCH", patch.getHeader("X-HTTP-Method-Override"));
        assertTrue(patch.getPath().contains("/files/e1?uploadType=media"));
    }

    @Test
    public void errorResponsesBecomeDriveExceptionsWithTheStatusCode() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"expired\"}"));
        try {
            client.getUser();
            fail("expected an exception");
        } catch (DriveClient.DriveException e) {
            assertEquals(401, e.code);
            assertTrue(e.getMessage().contains("expired"));
        } catch (IOException e) {
            fail("wrong exception type: " + e);
        }
    }

    @Test
    public void malformedJsonSurfacesAsIoException() {
        server.enqueue(new MockResponse().setBody("not json"));
        try {
            client.getUser();
            fail("expected an exception");
        } catch (IOException e) {
            assertTrue(e.getCause() instanceof JSONException);
        }
    }
}
