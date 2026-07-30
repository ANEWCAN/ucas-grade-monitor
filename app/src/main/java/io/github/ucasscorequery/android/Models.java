/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright 2026 UCAS Score Query contributors
 */

package io.github.ucasscorequery.android;

import java.util.ArrayList;
import java.util.List;

final class Score {
    final String courseName;
    final String englishName;
    final String score;
    final String credit;
    final String degreeCourse;
    final String semester;
    final String evaluation;

    Score(String courseName, String englishName, String score, String credit,
          String degreeCourse, String semester, String evaluation) {
        this.courseName = safe(courseName);
        this.englishName = safe(englishName);
        this.score = safe(score);
        this.credit = safe(credit);
        this.degreeCourse = safe(degreeCourse);
        this.semester = safe(semester);
        this.evaluation = safe(evaluation);
    }

    String key() {
        return courseName + "\u0001" + semester;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

final class Credentials {
    final String username;
    final String password;
    final String token;
    final String model;

    Credentials(String username, String password, String token, String model) {
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.token = token == null ? "" : token.trim();
        this.model = model == null || model.trim().isEmpty() ? "qwen3.5" : model.trim();
    }

    boolean isComplete() {
        return !username.isEmpty() && !password.isEmpty() && !token.isEmpty();
    }
}

final class AppSettings {
    static final String NOTIFY_NEW = "new";
    static final String NOTIFY_ALL = "all";

    final Credentials credentials;
    final boolean autoEnabled;
    final int intervalMinutes;
    final String notifyMode;
    final int retryCount;

    AppSettings(Credentials credentials, boolean autoEnabled, int intervalMinutes,
                String notifyMode, int retryCount) {
        this.credentials = credentials;
        this.autoEnabled = autoEnabled;
        this.intervalMinutes = Math.max(15, intervalMinutes);
        this.notifyMode = NOTIFY_ALL.equals(notifyMode) ? NOTIFY_ALL : NOTIFY_NEW;
        this.retryCount = Math.max(0, Math.min(5, retryCount));
    }
}

final class QueryRecord {
    final boolean success;
    final long timeMillis;
    final long durationMillis;
    final String message;
    final List<Score> scores;
    final List<Score> changes;

    QueryRecord(boolean success, long timeMillis, long durationMillis, String message,
                List<Score> scores, List<Score> changes) {
        this.success = success;
        this.timeMillis = timeMillis;
        this.durationMillis = durationMillis;
        this.message = message == null ? "" : message;
        this.scores = scores == null ? new ArrayList<Score>() : scores;
        this.changes = changes == null ? new ArrayList<Score>() : changes;
    }
}
