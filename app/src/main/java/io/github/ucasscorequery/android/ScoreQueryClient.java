/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright 2026 UCAS Score Query contributors
 */

package io.github.ucasscorequery.android;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;

final class ScoreQueryClient {
    private static final String SEP_BASE = "https://sep.ucas.ac.cn";
    private static final String JWXK_BASE = "https://jwxk.ucas.ac.cn";
    private static final String LLM_URL = "https://uni-api.cstcloud.cn/v1/chat/completions";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int LLM_CONNECT_TIMEOUT_MS = 60_000;
    private static final int LLM_READ_TIMEOUT_MS = 300_000;
    private static final int LLM_TRANSIENT_RETRIES = 2;
    private static final byte[] XOR_KEY = new byte[] {
            (byte)0xA8,(byte)0xDA,0x0D,0x67,0x2E,(byte)0xC1,(byte)0xB5,(byte)0x8B,
            (byte)0xE5,(byte)0x88,0x7A,(byte)0xFA,(byte)0xC3,(byte)0xFD,0x5B,(byte)0xE5,
            (byte)0xDB,(byte)0xDE,0x76,(byte)0xBD,(byte)0xC9,(byte)0xCD,(byte)0xD7,0x0B,
            (byte)0x89,0x6F,0x7E,0x13,0x64,0x48,0x62,0x75,
            (byte)0xF5,(byte)0xE2,(byte)0xD1,0x50,0x41,0x0C,(byte)0xB0,(byte)0xAA
    };

    private final Credentials credentials;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

    ScoreQueryClient(Credentials credentials) {
        this.credentials = credentials;
    }

    List<Score> queryScores() throws Exception {
        List<String> candidates = usernameCandidates(credentials.username);
        Exception lastError = null;

        for (int index = 0; index < candidates.size(); index++) {
            String username = candidates.get(index);
            clearCookies();
            try {
                loginSep(username);
                loginJwxk();
                return fetchScores();
            } catch (Exception error) {
                lastError = error;
                boolean hasAlternate = index + 1 < candidates.size();
                if (!hasAlternate || !shouldTryAlternateUsername(error)) {
                    throw error;
                }
            }
        }

        throw new Exception("邮箱格式与原始账号格式均未能完成认证：" + cleanError(lastError), lastError);
    }

    private void loginSep(String username) throws Exception {
        if (validateSepSession()) return;
        String lastError = "SEP 登录失败";
        for (int attempt = 1; attempt <= 5; attempt++) {
            Response loginPage = request("GET", SEP_BASE, null, null, true, null);
            String page = loginPage.text();
            String publicKey = findFirst(page, "jsePubKey\\s*=\\s*[\"']([^\"']+)[\"']");
            if (publicKey == null) throw new Exception("无法从 SEP 登录页找到 RSA 公钥。");
            String loginFrom = findInputValue(page, "loginFrom");
            boolean captchaRequired = page.contains("certCode1") || page.contains("name=\"certCode\"") || page.contains("name='certCode'");
            String captcha = "";
            if (captchaRequired) {
                Response image = request("GET", SEP_BASE + "/changePic?_=" + System.currentTimeMillis(), null, null, true,
                        Collections.singletonMap("Referer", SEP_BASE));
                if (image.body.length == 0) throw new Exception("验证码图片为空。");
                captcha = solveCaptcha(image.body);
            }
            Map<String, String> form = new LinkedHashMap<String, String>();
            form.put("userName", username);
            form.put("pwd", encryptPassword(credentials.password, publicKey));
            form.put("certCode", captcha);
            form.put("loginFrom", loginFrom == null ? "" : loginFrom);
            form.put("sb", "sb");
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Origin", SEP_BASE);
            headers.put("Referer", SEP_BASE);
            Response response = request("POST", SEP_BASE + "/slogin", formEncode(form),
                    "application/x-www-form-urlencoded; charset=UTF-8", true, headers);
            String body = response.text();
            if (containsAny(body, "用户名或密码错误", "用户名或密码不正确", "账号或密码错误", "密码错误")) {
                throw new Exception("SEP 用户名或密码错误。请确认应用中输入的账号和密码完整；密码中的 # 会按普通字符处理。");
            }
            if (body.contains("验证码错误")) {
                lastError = "验证码错误";
                sleep(attempt);
                continue;
            }
            if (validateSepSession() || isAuthenticatedSepResponse(response)) return;
            String error = extractLoginError(body);
            lastError = error == null
                    ? "登录后未能建立有效 SEP 会话（" + sessionDiagnostics(response) + "）"
                    : error;
            if (lastError.contains("验证码") || body.contains("certCode")) {
                sleep(attempt);
                continue;
            }
            throw new Exception(lastError);
        }
        throw new Exception("SEP 登录在 5 次尝试后仍失败：" + lastError);
    }

    private boolean validateSepSession() throws Exception {
        // The reference implementation validates a protected SEP page.  In practice,
        // different accounts can be redirected to different portal pages, so check both
        // businessMenu and the known portal path instead of relying on one endpoint only.
        Response menu = request("GET", SEP_BASE + "/businessMenu", null, null, false, null);
        if (isAuthenticatedSepResponse(menu)) return true;

        Response portal = request("GET", SEP_BASE + "/portal/site/226/821", null, null, false, null);
        return isAuthenticatedSepResponse(portal);
    }

    private void loginJwxk() throws Exception {
        Response menu = request("GET", SEP_BASE + "/businessMenu", null, null, true, null);
        String portalUrl = findPortalLink(menu.text());
        if (portalUrl == null) throw new Exception("未能从 SEP 门户找到选课/我的课程入口。");
        Response portal = request("GET", portalUrl, null, null, false, null);
        String redirect = portal.header("Location");
        if (redirect == null || redirect.isEmpty()) redirect = extractRedirect(portal.text());
        if (redirect == null || redirect.isEmpty()) throw new Exception("未能从 SEP 门户跳转中获取 JWXK Identity。");
        redirect = resolve(portalUrl, redirect);
        String identity = findFirst(redirect, "[?&]Identity=([^&]+)");
        if (identity == null) throw new Exception("JWXK 跳转地址中没有 Identity 参数。");
        String targetPath = "/courseManage/selectedCourse";
        String loginUrl = JWXK_BASE + "/login?Identity=" + identity + "&roleId=xs&fromUrl=1&toUrl=" + encodeToUrl(targetPath);
        request("GET", loginUrl, null, null, true, null);
        Response verify = request("GET", JWXK_BASE + targetPath, null, null, false, null);
        if (verify.code >= 300 && verify.code < 400) {
            String location = verify.header("Location");
            if (location != null && (location.contains("login") || location.contains("Identity"))) {
                throw new Exception("JWXK 会话未建立，系统重新跳回登录页。");
            }
        }
        if (verify.code != 200) throw new Exception("JWXK 会话验证失败，HTTP " + verify.code + "。");
        String body = verify.text();
        if (body.contains("Identity") && body.toLowerCase(Locale.ROOT).contains("login")) {
            throw new Exception("JWXK 返回登录页，会话无效。");
        }
    }

    private List<Score> fetchScores() throws Exception {
        Response response = request("GET", JWXK_BASE + "/score/yjs/all", null, null, true, null);
        if (response.code != 200) throw new Exception("成绩页面请求失败，HTTP " + response.code + "。");
        String body = response.text();
        if (!(body.contains("课程成绩") || body.contains("学分") || (body.contains("成绩") && body.toLowerCase(Locale.ROOT).contains("table")))) {
            throw new Exception("成绩页面内容异常，可能是会话失效或教务页面已改版。");
        }
        List<Score> scores = parseScores(body);
        if (scores.isEmpty()) throw new Exception("已访问成绩页面，但没有解析到成绩。可能当前暂无成绩，或表格结构已变化。");
        return scores;
    }

    private static List<String> usernameCandidates(String input) {
        String username = input == null ? "" : input.trim();
        if (username.contains("@")) return Collections.singletonList(username);

        // The referenced UCAS client normalizes score/JWXK accounts to the mailbox
        // identity. Keep the raw account as a compatibility fallback for older accounts.
        List<String> result = new ArrayList<String>();
        result.add(username + "@mails.ucas.ac.cn");
        result.add(username);
        return result;
    }

    private void clearCookies() {
        cookies.getCookieStore().removeAll();
    }

    private static boolean shouldTryAlternateUsername(Throwable error) {
        String message = cleanError(error);
        return message.contains("用户名或密码错误")
                || message.contains("未能建立有效 SEP 会话")
                || message.contains("SEP 会话")
                || message.contains("Identity")
                || message.contains("JWXK")
                || message.contains("选课/我的课程入口");
    }

    private static String cleanError(Throwable error) {
        if (error == null) return "未知错误";
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message.trim();
    }

    private boolean isAuthenticatedSepResponse(Response response) {
        if (response == null || response.code != 200) return false;
        String body = response.text();
        if (body.trim().isEmpty() || isSepLoginPage(body)) return false;

        String path = "";
        try { path = URI.create(response.finalUrl).getPath(); } catch (Exception ignored) {}
        if (path == null) path = "";

        if (path.contains("businessMenu") || path.startsWith("/portal/")) return true;
        return body.contains("退出")
                || body.contains("选课")
                || body.contains("我的课程")
                || body.contains("businessMenu");
    }

    private static boolean isSepLoginPage(String body) {
        if (body == null) return true;
        String lower = body.toLowerCase(Locale.ROOT);
        boolean hasCredentialsForm = (lower.contains("name=\"username\"")
                || lower.contains("name='username'")
                || lower.contains("name=\"userName\"".toLowerCase(Locale.ROOT))
                || lower.contains("name='username'"))
                && (lower.contains("name=\"pwd\"") || lower.contains("name='pwd'"));
        return body.contains("jsePubKey")
                || lower.contains("action=\"/slogin\"")
                || lower.contains("action='/slogin'")
                || hasCredentialsForm;
    }

    private String sessionDiagnostics(Response response) {
        String path = "未知";
        try {
            String parsed = URI.create(response.finalUrl).getPath();
            if (parsed != null && !parsed.isEmpty()) path = parsed;
        } catch (Exception ignored) {}

        StringBuilder names = new StringBuilder();
        for (HttpCookie cookie : cookies.getCookieStore().getCookies()) {
            if (names.length() > 0) names.append(',');
            names.append(cookie.getName());
        }
        return "HTTP " + response.code + "，最终路径 " + path
                + "，Cookie " + (names.length() == 0 ? "无" : names.toString());
    }

    private String solveCaptcha(byte[] image) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= LLM_TRANSIENT_RETRIES; attempt++) {
            try {
                Response response = requestCaptchaRecognition(image);
                if (response.code < 200 || response.code >= 300) {
                    String body = limit(response.text(), 220);
                    Exception error = new Exception("验证码识别接口请求失败，HTTP "
                            + response.code + "：" + body);
                    if (isTransientLlmFailure(response.code, body) && attempt < LLM_TRANSIENT_RETRIES) {
                        lastError = error;
                        sleepLlmRetry(attempt);
                        continue;
                    }
                    throw error;
                }
                return parseCaptchaResponse(response.text());
            } catch (SocketTimeoutException timeout) {
                lastError = new Exception("验证码识别请求等待超时（最长 300 秒）。", timeout);
            } catch (IOException network) {
                lastError = new Exception("验证码识别网络连接异常：" + cleanError(network), network);
            } catch (Exception error) {
                if (!isTransientLlmException(error) || attempt >= LLM_TRANSIENT_RETRIES) throw error;
                lastError = error;
            }
            if (attempt < LLM_TRANSIENT_RETRIES) sleepLlmRetry(attempt);
        }
        throw new Exception("验证码识别服务连续 " + LLM_TRANSIENT_RETRIES
                + " 次瞬时失败：" + cleanError(lastError), lastError);
    }

    private Response requestCaptchaRecognition(byte[] image) throws Exception {
        String boundary = "----UCAS" + System.nanoTime();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePart(out, boundary, "model", credentials.model);
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", "识别图片中的4位验证码。只输出4位验证码本身，不要解释，不要添加空格、引号或标点。");
        messages.put(message);
        // Keep the multipart fields exactly compatible with the previously working
        // implementation. Some gateways treat string-valued stream/max_tokens fields
        // differently and may return a non-standard choices/message structure.
        writePart(out, boundary, "messages", messages.toString());
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write("Content-Disposition: form-data; name=\"file\"; filename=\"captcha.jpg\"\r\n".getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(image);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + credentials.token);
        return request("POST", LLM_URL, out.toByteArray(),
                "multipart/form-data; boundary=" + boundary, true, headers);
    }

    private static String parseCaptchaResponse(String responseText) throws Exception {
        String raw = responseText == null ? "" : responseText.trim();
        if (raw.isEmpty()) throw new Exception("验证码接口返回空响应。");

        // Be defensive about OpenAI-compatible gateways. A few variants return
        // message/content as strings rather than nested JSON objects, and streamed
        // responses may contain one or more `data:` lines.
        JSONObject payload = parseJsonPayload(raw);
        JSONArray choices = payload.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            String direct = firstNonEmpty(
                    stringValue(payload.opt("content")),
                    stringValue(payload.opt("text")),
                    stringValue(payload.opt("result")),
                    stringValue(payload.opt("answer")));
            if (!direct.isEmpty()) return extractCaptchaCode(direct);
            throw new Exception("验证码接口响应缺少 choices，响应摘要：" + limit(raw, 160));
        }

        Object first = choices.opt(0);
        String content = "";
        if (first instanceof JSONObject) {
            JSONObject choice = (JSONObject) first;
            Object message = choice.opt("message");
            if (message instanceof JSONObject) {
                JSONObject messageObject = (JSONObject) message;
                content = firstNonEmpty(
                        stringValue(messageObject.opt("content")),
                        stringValue(messageObject.opt("text")));
            } else {
                content = stringValue(message);
            }
            if (content.isEmpty()) {
                content = firstNonEmpty(
                        stringValue(choice.opt("content")),
                        stringValue(choice.opt("text")),
                        stringValue(choice.opt("delta")));
            }
        } else {
            content = stringValue(first);
        }

        if (content.isEmpty()) {
            throw new Exception("验证码接口响应格式异常，响应摘要：" + limit(raw, 160));
        }
        return extractCaptchaCode(content);
    }

    private static JSONObject parseJsonPayload(String raw) throws Exception {
        try {
            return new JSONObject(raw);
        } catch (Exception directError) {
            JSONObject last = null;
            String[] lines = raw.split("\r?\n");
            for (String line : lines) {
                String value = line.trim();
                if (value.startsWith("data:")) value = value.substring(5).trim();
                if (value.isEmpty() || "[DONE]".equals(value)) continue;
                try {
                    last = new JSONObject(value);
                } catch (Exception ignored) {
                }
            }
            if (last != null) return last;
            throw new Exception("验证码接口没有返回有效 JSON，响应摘要：" + limit(raw, 160), directError);
        }
    }

    private static String extractCaptchaCode(String content) throws Exception {
        String compact = content.replaceAll("\\s+", "")
                .replaceAll("^[`'\"，。,:：]+|[`'\"，。,:：]+$", "");
        if (compact.matches("[A-Za-z0-9]{4}")) return compact.toUpperCase(Locale.ROOT);
        Matcher matcher = Pattern.compile("(?<![A-Za-z0-9])([A-Za-z0-9]{4})(?![A-Za-z0-9])").matcher(content);
        String found = null;
        while (matcher.find()) {
            String candidate = matcher.group(1).toUpperCase(Locale.ROOT);
            if (found != null && !found.equals(candidate)) throw new Exception("验证码接口返回多个候选结果。");
            found = candidate;
        }
        if (found == null) {
            throw new Exception("无法从验证码接口响应中得到唯一的4位验证码，内容摘要：" + limit(content, 100));
        }
        return found;
    }

    private static String stringValue(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof String) return ((String) value).trim();
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                String part = stringValue(array.opt(i));
                if (!part.isEmpty()) {
                    if (result.length() > 0) result.append(' ');
                    result.append(part);
                }
            }
            return result.toString();
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            return firstNonEmpty(
                    stringValue(object.opt("content")),
                    stringValue(object.opt("text")),
                    stringValue(object.opt("value")));
        }
        return String.valueOf(value).trim();
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static boolean isTransientLlmFailure(int code, String body) {
        String lower = body == null ? "" : body.toLowerCase(Locale.ROOT);
        return code == 408 || code == 409 || code == 425 || code == 429
                || code == 500 || code == 502 || code == 503 || code == 504
                || (code == 422 && (lower.contains("stream timeout")
                    || lower.contains("timeout") || lower.contains("timed out")));
    }

    private static boolean isTransientLlmException(Throwable error) {
        String message = cleanError(error).toLowerCase(Locale.ROOT);
        return message.contains("stream timeout") || message.contains("timed out")
                || message.contains("等待超时") || message.contains("网络连接异常")
                || message.contains("http 408") || message.contains("http 409")
                || message.contains("http 422") || message.contains("http 425")
                || message.contains("http 429") || message.contains("http 500")
                || message.contains("http 502") || message.contains("http 503")
                || message.contains("http 504");
    }

    private static void sleepLlmRetry(int failedAttempt) {
        long[] delays = {6_000L, 15_000L, 30_000L};
        long delay = delays[Math.min(Math.max(failedAttempt - 1, 0), delays.length - 1)];
        try {
            Thread.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void writePart(ByteArrayOutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private Response request(String method, String url, byte[] body, String contentType,
                             boolean followRedirects, Map<String, String> extraHeaders) throws Exception {
        String current = url;
        String currentMethod = method;
        byte[] currentBody = body;
        String currentType = contentType;
        for (int redirects = 0; redirects <= 8; redirects++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            boolean isLlmRequest = current.startsWith(LLM_URL);
            connection.setConnectTimeout(isLlmRequest ? LLM_CONNECT_TIMEOUT_MS : 20_000);
            connection.setReadTimeout(isLlmRequest ? LLM_READ_TIMEOUT_MS : 60_000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(currentMethod);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json,*/*");
            URI uri = URI.create(current);
            Map<String, List<String>> cookieHeaders = cookies.get(uri, Collections.<String, List<String>>emptyMap());
            for (Map.Entry<String, List<String>> entry : cookieHeaders.entrySet()) {
                for (String value : entry.getValue()) connection.addRequestProperty(entry.getKey(), value);
            }
            if (extraHeaders != null) {
                for (Map.Entry<String, String> entry : extraHeaders.entrySet()) connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            if (currentBody != null) {
                connection.setDoOutput(true);
                if (currentType != null) connection.setRequestProperty("Content-Type", currentType);
                connection.setFixedLengthStreamingMode(currentBody.length);
                OutputStream output = connection.getOutputStream();
                output.write(currentBody);
                output.close();
            }
            int code = connection.getResponseCode();
            cookies.put(uri, connection.getHeaderFields());
            byte[] responseBody = readAll(code >= 400 ? connection.getErrorStream() : connection.getInputStream());
            Map<String, List<String>> headers = connection.getHeaderFields();
            Response response = new Response(code, responseBody, headers, connection.getContentType(), current);
            if (followRedirects && code >= 300 && code < 400) {
                String location = response.header("Location");
                if (location == null || location.isEmpty()) return response;
                current = resolve(current, location);
                currentMethod = "GET";
                currentBody = null;
                currentType = null;
                continue;
            }
            return response;
        }
        throw new Exception("重定向次数过多。");
    }

    private static byte[] formEncode(Map<String, String> values) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (builder.length() > 0) builder.append('&');
            builder.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            builder.append('=');
            builder.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String encryptPassword(String password, String keyBase64) throws Exception {
        byte[] encoded = android.util.Base64.decode(keyBase64, android.util.Base64.DEFAULT);
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return android.util.Base64.encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)), android.util.Base64.NO_WRAP);
    }

    private static String encodeToUrl(String path) {
        byte[] bytes = path.getBytes(StandardCharsets.UTF_8);
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            int value = (bytes[i] ^ XOR_KEY[i % XOR_KEY.length]) & 0xFF;
            result.append(String.format(Locale.US, "%02X", value));
        }
        return result.toString();
    }

    private static String findPortalLink(String html) {
        Matcher matcher = Pattern.compile("(?is)<a\\b([^>]*)>(.*?)</a>").matcher(html);
        while (matcher.find()) {
            String text = cleanHtml(matcher.group(2));
            if (!(text.contains("选课") || text.contains("我的课程"))) continue;
            String href = findAttribute(matcher.group(1), "href");
            if (href != null) return resolve(SEP_BASE, htmlDecode(href));
        }
        return null;
    }

    private static String extractRedirect(String html) {
        Matcher metaTags = Pattern.compile("(?is)<meta\\b([^>]*)>").matcher(html);
        while (metaTags.find()) {
            String attrs = metaTags.group(1);
            String httpEquiv = findAttribute(attrs, "http-equiv");
            String content = findAttribute(attrs, "content");
            if (httpEquiv == null || content == null || !"refresh".equalsIgnoreCase(httpEquiv.trim())) continue;
            Matcher url = Pattern.compile("(?i)(?:^|;)\\s*url\\s*=\\s*(.+)$").matcher(content);
            if (url.find()) return htmlDecode(url.group(1).trim().replaceAll("^[\\\"']|[\\\"']$", ""));
        }

        String[] patterns = {
                "location\\.href\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']",
                "window\\.location(?:\\.href)?\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']",
                "url=([^\\\"'>\\s]+)"
        };
        for (String pattern : patterns) {
            String value = findFirst(html, pattern);
            if (value != null) return htmlDecode(value.trim());
        }
        return null;
    }

    private static List<Score> parseScores(String html) {
        List<Score> result = new ArrayList<Score>();
        Matcher tables = Pattern.compile("(?is)<table\\b[^>]*>(.*?)</table>").matcher(html);
        while (tables.find()) {
            List<List<String>> rows = new ArrayList<List<String>>();
            Matcher rowMatcher = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>").matcher(tables.group(1));
            while (rowMatcher.find()) {
                List<String> cells = new ArrayList<String>();
                Matcher cellMatcher = Pattern.compile("(?is)<t[hd]\\b[^>]*>(.*?)</t[hd]>").matcher(rowMatcher.group(1));
                while (cellMatcher.find()) cells.add(cleanHtml(cellMatcher.group(1)));
                if (!cells.isEmpty()) rows.add(cells);
            }
            if (rows.isEmpty()) continue;
            List<String> headers = rows.get(0);
            String joined = join(headers, ",");
            if (!(joined.contains("课程") || joined.contains("成绩") || joined.contains("学分") || joined.contains("课号"))) continue;
            Map<String, Integer> indices = headerIndices(headers);
            List<Score> tableScores = new ArrayList<Score>();
            for (int i = 1; i < rows.size(); i++) {
                List<String> cells = rows.get(i);
                String course = getCell(cells, indices, "course", 0);
                if (course.isEmpty() || course.contains("姓名") || "课程名称".equals(course) || course.contains("博士学位英语（免修考试）的成绩")) continue;
                tableScores.add(new Score(
                        course,
                        getCell(cells, indices, "english", -1),
                        getCell(cells, indices, "score", 2),
                        getCell(cells, indices, "credit", 3),
                        getCell(cells, indices, "degree", -1),
                        getCell(cells, indices, "semester", 5),
                        getCell(cells, indices, "evaluation", -1)));
            }
            if (!tableScores.isEmpty()) {
                result.addAll(tableScores);
                break;
            }
        }
        return result;
    }

    private static Map<String, Integer> headerIndices(List<String> headers) {
        Map<String, Integer> result = new HashMap<String, Integer>();
        for (int i = 0; i < headers.size(); i++) {
            String text = headers.get(i).replaceAll("\\s+", "");
            if (text.contains("英文名称")) result.put("english", i);
            else if (text.contains("课程名称") || "课程".equals(text)) result.put("course", i);
            else if (text.contains("分数") || text.contains("成绩")) result.put("score", i);
            else if (text.contains("学分")) result.put("credit", i);
            else if (text.contains("学位课")) result.put("degree", i);
            else if (text.contains("学期")) result.put("semester", i);
            else if (text.contains("评估")) result.put("evaluation", i);
        }
        return result;
    }

    private static String getCell(List<String> cells, Map<String, Integer> indices, String key, int fallback) {
        Integer index = indices.get(key);
        int actual = index == null ? fallback : index;
        return actual >= 0 && actual < cells.size() ? cells.get(actual).trim() : "";
    }

    private static String findInputValue(String html, String name) {
        Matcher input = Pattern.compile("(?is)<input\\b([^>]*)>").matcher(html);
        while (input.find()) {
            String attrs = input.group(1);
            String foundName = findAttribute(attrs, "name");
            if (name.equals(foundName)) {
                String value = findAttribute(attrs, "value");
                return value == null ? "" : htmlDecode(value);
            }
        }
        return "";
    }

    private static String findAttribute(String attrs, String name) {
        Matcher matcher = Pattern.compile("(?is)(?:^|\\s)" + Pattern.quote(name) + "\\s*=\\s*(?:[\"']([^\"']*)[\"']|([^\\s>]+))").matcher(attrs);
        if (!matcher.find()) return null;
        return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
    }

    private static String extractLoginError(String html) {
        String text = cleanHtml(html);
        String[] words = {"用户名或密码错误", "用户名或密码不正确", "账号或密码错误", "密码错误", "验证码错误", "账号被锁定", "锁定"};
        for (String word : words) if (text.contains(word)) return word;
        return null;
    }

    private static String cleanHtml(String html) {
        if (html == null) return "";
        return htmlDecode(html.replaceAll("(?is)<script\\b.*?</script>", " ")
                .replaceAll("(?is)<style\\b.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replaceAll("\\s+", " ").trim());
    }

    private static String htmlDecode(String text) {
        if (text == null) return "";
        String value = text.replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'");
        Matcher numeric = Pattern.compile("&#(x?[0-9A-Fa-f]+);").matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (numeric.find()) {
            try {
                String raw = numeric.group(1);
                int code = raw.startsWith("x") || raw.startsWith("X")
                        ? Integer.parseInt(raw.substring(1), 16) : Integer.parseInt(raw, 10);
                numeric.appendReplacement(buffer, Matcher.quoteReplacement(new String(Character.toChars(code))));
            } catch (Exception error) {
                numeric.appendReplacement(buffer, Matcher.quoteReplacement(numeric.group(0)));
            }
        }
        numeric.appendTail(buffer);
        return buffer.toString();
    }

    private static String findFirst(String text, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) if (text.contains(candidate)) return true;
        return false;
    }

    private static String resolve(String base, String other) {
        try {
            return new URL(new URL(base), other).toString();
        } catch (Exception ignored) {
            return other;
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        if (input == null) return new byte[0];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        input.close();
        return output.toByteArray();
    }

    private static String join(List<String> values, String delimiter) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(delimiter);
            result.append(value);
        }
        return result.toString();
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private static void sleep(int attempt) {
        try {
            Thread.sleep(Math.min(800L * attempt, 2500L));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class Response {
        final int code;
        final byte[] body;
        final Map<String, List<String>> headers;
        final String contentType;
        final String finalUrl;

        Response(int code, byte[] body, Map<String, List<String>> headers, String contentType, String finalUrl) {
            this.code = code;
            this.body = body == null ? new byte[0] : body;
            this.headers = headers == null ? Collections.<String, List<String>>emptyMap() : headers;
            this.contentType = contentType;
            this.finalUrl = finalUrl == null ? "" : finalUrl;
        }

        String header(String name) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                    return entry.getValue().get(0);
                }
            }
            return null;
        }

        String text() {
            Charset charset = StandardCharsets.UTF_8;
            String type = contentType;
            if (type != null) {
                Matcher matcher = Pattern.compile("(?i)charset=([^;\\s]+)").matcher(type);
                if (matcher.find()) {
                    try { charset = Charset.forName(matcher.group(1).replace("\"", "")); } catch (Exception ignored) {}
                }
            }
            String text = new String(body, charset);
            if (text.indexOf('\uFFFD') >= 0) {
                try { text = new String(body, Charset.forName("GB18030")); } catch (Exception ignored) {}
            }
            return text;
        }
    }
}
