/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright 2026 UCAS Score Query contributors
 */

package io.github.ucasscorequery.android;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class AppPrefs {
    private static final String NAME = "ucas_score_query_preferences";

    private AppPrefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static AppSettings loadSettings(Context context) {
        SharedPreferences p = prefs(context);
        String username = decryptQuietly(p.getString("username", ""));
        String password = decryptQuietly(p.getString("password", ""));
        String token = decryptQuietly(p.getString("token", ""));
        String model = p.getString("model", "qwen3.5");
        boolean enabled = p.getBoolean("auto_enabled", false);
        int interval = p.getInt("interval_minutes", 60);
        String mode = p.getString("notify_mode", AppSettings.NOTIFY_NEW);
        int retryCount = p.getInt("retry_count", 3);
        return new AppSettings(new Credentials(username, password, token, model),
                enabled, interval, mode, retryCount);
    }

    static void saveSettings(Context context, AppSettings settings) throws Exception {
        prefs(context).edit()
                .putString("username", SecureStore.encrypt(settings.credentials.username))
                .putString("password", SecureStore.encrypt(settings.credentials.password))
                .putString("token", SecureStore.encrypt(settings.credentials.token))
                .putString("model", settings.credentials.model)
                .putBoolean("auto_enabled", settings.autoEnabled)
                .putInt("interval_minutes", settings.intervalMinutes)
                .putString("notify_mode", settings.notifyMode)
                .putInt("retry_count", settings.retryCount)
                .apply();
    }

    static void saveRecord(Context context, String key, QueryRecord record) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("success", record.success);
            obj.put("time", record.timeMillis);
            obj.put("duration", record.durationMillis);
            obj.put("message", record.message);
            obj.put("scores", scoresToJson(record.scores));
            obj.put("changes", scoresToJson(record.changes));
            prefs(context).edit().putString(key, obj.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static QueryRecord loadRecord(Context context, String key) {
        String raw = prefs(context).getString(key, "");
        if (raw == null || raw.isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(raw);
            return new QueryRecord(
                    obj.optBoolean("success", false),
                    obj.optLong("time", 0L),
                    obj.optLong("duration", 0L),
                    obj.optString("message", ""),
                    jsonToScores(obj.optJSONArray("scores")),
                    jsonToScores(obj.optJSONArray("changes")));
        } catch (Exception ignored) {
            return null;
        }
    }

    static List<Score> loadBaseline(Context context) {
        String raw = prefs(context).getString("baseline_scores", "");
        if (raw == null || raw.isEmpty()) return null;
        try {
            return jsonToScores(new JSONArray(raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    static void saveBaseline(Context context, List<Score> scores) {
        try {
            prefs(context).edit().putString("baseline_scores", scoresToJson(scores).toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static List<Score> calculateChanges(List<Score> oldScores, List<Score> newScores) {
        List<Score> changes = new ArrayList<Score>();
        if (oldScores == null) return changes;
        Map<String, Score> oldMap = new HashMap<String, Score>();
        for (Score score : oldScores) oldMap.put(score.key(), score);
        for (Score score : newScores) {
            Score previous = oldMap.get(score.key());
            if (previous == null || !previous.score.equals(score.score)) changes.add(score);
        }
        return changes;
    }

    private static JSONArray scoresToJson(List<Score> scores) throws Exception {
        JSONArray arr = new JSONArray();
        if (scores == null) return arr;
        for (Score score : scores) {
            JSONObject obj = new JSONObject();
            obj.put("course", score.courseName);
            obj.put("english", score.englishName);
            obj.put("score", score.score);
            obj.put("credit", score.credit);
            obj.put("degree", score.degreeCourse);
            obj.put("semester", score.semester);
            obj.put("evaluation", score.evaluation);
            arr.put(obj);
        }
        return arr;
    }

    private static List<Score> jsonToScores(JSONArray arr) {
        List<Score> result = new ArrayList<Score>();
        if (arr == null) return result;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            result.add(new Score(
                    obj.optString("course", ""), obj.optString("english", ""),
                    obj.optString("score", ""), obj.optString("credit", ""),
                    obj.optString("degree", ""), obj.optString("semester", ""),
                    obj.optString("evaluation", "")));
        }
        return result;
    }

    private static String decryptQuietly(String value) {
        try {
            return SecureStore.decrypt(value);
        } catch (Exception ignored) {
            return "";
        }
    }
}
