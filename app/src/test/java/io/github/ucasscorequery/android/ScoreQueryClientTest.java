package io.github.ucasscorequery.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class ScoreQueryClientTest {
    @SuppressWarnings("unchecked")
    @Test
    public void usernameCandidatesPreferMailboxIdentity() throws Exception {
        Method method = ScoreQueryClient.class.getDeclaredMethod("usernameCandidates", String.class);
        method.setAccessible(true);
        List<String> candidates = (List<String>) method.invoke(null, "student001");

        assertEquals(2, candidates.size());
        assertEquals("student001@mails.ucas.ac.cn", candidates.get(0));
        assertEquals("student001", candidates.get(1));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void parsesScoreTable() throws Exception {
        Method method = ScoreQueryClient.class.getDeclaredMethod("parseScores", String.class);
        method.setAccessible(true);
        List<Score> scores = (List<Score>) method.invoke(null, readResource("/score_page.html"));

        assertEquals(2, scores.size());
        assertEquals("信息检索", scores.get(0).courseName);
        assertEquals("92", scores.get(0).score);
        assertEquals("是", scores.get(0).degreeCourse);
        assertTrue(scores.get(1).key().contains("学术写作"));
    }

    private static String readResource(String name) throws Exception {
        InputStream input = ScoreQueryClientTest.class.getResourceAsStream(name);
        if (input == null) throw new IllegalStateException("Missing test resource: " + name);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        input.close();
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
