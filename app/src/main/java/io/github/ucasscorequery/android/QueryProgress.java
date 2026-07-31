/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright 2026 UCAS Score Query contributors
 */

package io.github.ucasscorequery.android;

/** Persisted live progress shared by manual queries, automatic queries and notifications. */
final class QueryProgress {
    final boolean active;
    final boolean automatic;
    final int percent;
    final String stage;
    final String detail;
    final int attempt;
    final int maxAttempts;
    final long startedAt;
    final long updatedAt;

    QueryProgress(boolean active, boolean automatic, int percent,
                  String stage, String detail, int attempt, int maxAttempts,
                  long startedAt, long updatedAt) {
        this.active = active;
        this.automatic = automatic;
        this.percent = Math.max(0, Math.min(100, percent));
        this.stage = stage == null ? "" : stage;
        this.detail = detail == null ? "" : detail;
        this.attempt = Math.max(0, attempt);
        this.maxAttempts = Math.max(0, maxAttempts);
        this.startedAt = startedAt;
        this.updatedAt = updatedAt;
    }

    static QueryProgress idle() {
        return new QueryProgress(false, false, 0, "", "", 0, 0, 0L, 0L);
    }
}

interface QueryProgressListener {
    void onProgress(int percent, String stage, String detail,
                    int attempt, int maxAttempts);
}
