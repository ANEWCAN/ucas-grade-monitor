/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright 2026 UCAS Score Query contributors
 */

package io.github.ucasscorequery.android;

import java.util.List;
import java.util.Locale;

/** Executes a complete score query and retries transient failures with a fresh session. */
final class QueryRunner {
    private QueryRunner() {}

    static Result execute(Credentials credentials, int retryCount) throws Exception {
        int retries = Math.max(0, Math.min(5, retryCount));
        int maxAttempts = retries + 1;
        Exception lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                List<Score> scores = new ScoreQueryClient(credentials).queryScores();
                return new Result(scores, attempt);
            } catch (Exception error) {
                lastError = error;
                if (isNonRetryable(error)) {
                    throw new Exception("不可重试错误，已停止：" + clean(error), error);
                }
                if (attempt >= maxAttempts) break;
                sleepBeforeRetry(attempt, error);
            }
        }

        throw new Exception("已尝试 " + maxAttempts + " 次仍失败：" + clean(lastError), lastError);
    }

    static boolean shouldSystemReschedule(Throwable error) {
        if (isNonRetryable(error)) return false;
        String message = allMessages(error).toLowerCase(Locale.ROOT);
        return message.contains("timeout") || message.contains("超时")
                || message.contains("网络") || message.contains("connection")
                || message.contains("http 408") || message.contains("http 409")
                || message.contains("http 422") || message.contains("http 425")
                || message.contains("http 429") || message.contains("http 500")
                || message.contains("http 502") || message.contains("http 503")
                || message.contains("http 504") || message.contains("会话")
                || message.contains("验证码");
    }

    private static boolean isNonRetryable(Throwable error) {
        String message = allMessages(error);
        return message.contains("用户名或密码错误")
                || message.contains("账号或密码错误")
                || message.contains("HTTP 400")
                || message.contains("HTTP 401")
                || message.contains("HTTP 403")
                || message.contains("接口密钥无效")
                || message.contains("Token 无效");
    }

    private static void sleepBeforeRetry(int failedAttempt, Throwable error) {
        String message = allMessages(error).toLowerCase(Locale.ROOT);
        long delayMillis;
        if (message.contains("stream timeout") || message.contains("http 422")) {
            long[] streamDelays = {12_000L, 25_000L, 45_000L, 60_000L, 60_000L};
            delayMillis = streamDelays[Math.min(Math.max(failedAttempt - 1, 0), streamDelays.length - 1)];
        } else {
            delayMillis = Math.min(5_000L * (1L << Math.min(failedAttempt - 1, 3)), 40_000L);
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String allMessages(Throwable error) {
        StringBuilder result = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 8) {
            String message = current.getMessage();
            if (message != null && !message.trim().isEmpty()) {
                if (result.length() > 0) result.append(" | ");
                result.append(message.trim());
            }
            current = current.getCause();
            depth++;
        }
        return result.toString();
    }

    private static String clean(Throwable error) {
        if (error == null) return "未知错误";
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message.trim();
    }

    static final class Result {
        final List<Score> scores;
        final int attempts;

        Result(List<Score> scores, int attempts) {
            this.scores = scores;
            this.attempts = attempts;
        }
    }
}
