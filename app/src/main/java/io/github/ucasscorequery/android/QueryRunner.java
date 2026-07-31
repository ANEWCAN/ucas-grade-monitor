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
        return execute(credentials, retryCount, null);
    }

    static Result execute(Credentials credentials, int retryCount,
                          QueryProgressListener listener) throws Exception {
        int retries = Math.max(0, Math.min(5, retryCount));
        int maxAttempts = retries + 1;
        Exception lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            publish(listener, 2, "准备查询",
                    "正在创建第 " + attempt + " 次查询会话", attempt, maxAttempts);
            try {
                final int currentAttempt = attempt;
                List<Score> scores = new ScoreQueryClient(credentials,
                        new QueryProgressListener() {
                            @Override
                            public void onProgress(int percent, String stage, String detail,
                                                   int ignoredAttempt, int ignoredMaxAttempts) {
                                publish(listener, percent, stage, detail,
                                        currentAttempt, maxAttempts);
                            }
                        }).queryScores();
                publish(listener, 100, "查询完成",
                        "已获取 " + scores.size() + " 门课程", attempt, maxAttempts);
                return new Result(scores, attempt);
            } catch (Exception error) {
                lastError = error;
                if (isNonRetryable(error)) {
                    publish(listener, 100, "查询终止", clean(error), attempt, maxAttempts);
                    throw new Exception("不可重试错误，已停止：" + clean(error), error);
                }
                if (attempt >= maxAttempts) break;
                long wait = retryDelayMillis(attempt, error);
                publish(listener, 5, "等待重试",
                        "本次失败：" + clean(error) + "；将在 "
                                + Math.max(1L, wait / 1000L) + " 秒后重试",
                        attempt, maxAttempts);
                sleep(wait);
            }
        }

        publish(listener, 100, "查询失败", clean(lastError), maxAttempts, maxAttempts);
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

    private static long retryDelayMillis(int failedAttempt, Throwable error) {
        String message = allMessages(error).toLowerCase(Locale.ROOT);
        if (message.contains("stream timeout") || message.contains("http 422")) {
            long[] delays = {12_000L, 25_000L, 45_000L, 60_000L, 60_000L};
            return delays[Math.min(Math.max(failedAttempt - 1, 0), delays.length - 1)];
        }
        return Math.min(5_000L * (1L << Math.min(failedAttempt - 1, 3)), 40_000L);
    }

    private static void sleep(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void publish(QueryProgressListener listener, int percent,
                                String stage, String detail,
                                int attempt, int maxAttempts) {
        if (listener != null) {
            listener.onProgress(percent, stage, detail, attempt, maxAttempts);
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
