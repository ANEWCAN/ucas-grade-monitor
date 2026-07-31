/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright 2026 UCAS Score Query contributors
 */

package io.github.ucasscorequery.android;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared automatic-query implementation used by the service and JobScheduler fallback. */
final class AutoQueryEngine {
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private AutoQueryEngine() {}

    static Result run(Context context) {
        final Context app = context.getApplicationContext();
        AppSettings settings = AppPrefs.loadSettings(app);
        if (!settings.autoEnabled) return new Result(false, false, true);
        if (!RUNNING.compareAndSet(false, true)) return new Result(false, false, true);

        final long started = System.currentTimeMillis();
        AppPrefs.setAutoQueryRunning(app, true);
        publish(app, started, 1, "自动查询启动", "正在准备后台查询", 0,
                Math.max(1, settings.retryCount + 1));
        boolean transientFailure = false;
        try {
            if (!settings.credentials.isComplete()) {
                QueryRecord record = new QueryRecord(false, System.currentTimeMillis(), 0,
                        "自动查询失败：请先在应用中完整填写账号、密码和接口密钥。",
                        new ArrayList<Score>(), new ArrayList<Score>());
                AppPrefs.saveRecord(app, "last_auto_result", record);
                if (AppSettings.NOTIFY_ALL.equals(settings.notifyMode)) {
                    NotificationHelper.notifyResult(app, "成绩自动查询失败", record.message, null);
                }
                return new Result(true, false, false);
            }

            QueryRunner.Result result = QueryRunner.execute(
                    settings.credentials, settings.retryCount,
                    new QueryProgressListener() {
                        @Override
                        public void onProgress(int percent, String stage, String detail,
                                               int attempt, int maxAttempts) {
                            publish(app, started, percent, stage, detail, attempt, maxAttempts);
                        }
                    });
            List<Score> scores = result.scores;
            List<Score> baseline = AppPrefs.loadBaseline(app);
            List<Score> changes = AppPrefs.calculateChanges(baseline, scores);
            boolean first = baseline == null;
            AppPrefs.saveBaseline(app, scores);
            String retrySummary = result.attempts > 1
                    ? "第 " + result.attempts + " 次整轮尝试成功（已重试 "
                        + (result.attempts - 1) + " 次）。"
                    : "";
            String message = first
                    ? "自动查询成功，已建立成绩基线，共 " + scores.size() + " 条记录。" + retrySummary
                    : (changes.isEmpty()
                        ? "自动查询成功，未发现新成绩或成绩变化，共 " + scores.size()
                            + " 条记录。" + retrySummary
                        : "自动查询成功，发现 " + changes.size()
                            + " 条新成绩或成绩变化。" + retrySummary);
            QueryRecord record = new QueryRecord(true, System.currentTimeMillis(),
                    System.currentTimeMillis() - started, message, scores, changes);
            AppPrefs.saveRecord(app, "last_auto_result", record);
            AppPrefs.saveRecord(app, "last_success_result", record);
            if (AppSettings.NOTIFY_ALL.equals(settings.notifyMode)) {
                NotificationHelper.notifyResult(app, "成绩自动查询完成", message, changes);
            } else if (!first && !changes.isEmpty()) {
                NotificationHelper.notifyResult(app, "发现新成绩或成绩变化", message, changes);
            }
            publish(app, started, 100, "自动查询完成", message,
                    result.attempts, Math.max(1, settings.retryCount + 1));
            return new Result(true, true, false);
        } catch (Exception error) {
            transientFailure = QueryRunner.shouldSystemReschedule(error);
            String message = "自动查询失败：" + cleanError(error);
            if (transientFailure) message += " 将提前安排一次后台补查。";
            QueryRecord record = new QueryRecord(false, System.currentTimeMillis(),
                    System.currentTimeMillis() - started, message,
                    new ArrayList<Score>(), new ArrayList<Score>());
            AppPrefs.saveRecord(app, "last_auto_result", record);
            if (AppSettings.NOTIFY_ALL.equals(settings.notifyMode)) {
                NotificationHelper.notifyResult(app, "成绩自动查询失败", message, null);
            }
            publish(app, started, 100, "自动查询失败", message,
                    0, Math.max(1, settings.retryCount + 1));
            return new Result(true, false, transientFailure);
        } finally {
            AppPrefs.setAutoQueryRunning(app, false);
            RUNNING.set(false);
            AppSettings latest = AppPrefs.loadSettings(app);
            if (latest.autoEnabled) {
                if (transientFailure) Scheduler.scheduleRetry(app, latest);
                else Scheduler.scheduleNext(app, latest, System.currentTimeMillis());
                Scheduler.ensureHealthy(app, false);
            }
            AppPrefs.clearQueryProgress(app);
            AutoQueryService.updateNotification(app);
        }
    }

    private static void publish(Context context, long started, int percent,
                                String stage, String detail,
                                int attempt, int maxAttempts) {
        AppPrefs.saveQueryProgress(context, new QueryProgress(
                true, true, percent, stage, detail, attempt, maxAttempts,
                started, System.currentTimeMillis()));
        AutoQueryService.updateNotification(context);
    }

    static boolean isRunning() {
        return RUNNING.get();
    }

    private static String cleanError(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message.trim();
    }

    static final class Result {
        final boolean executed;
        final boolean success;
        final boolean transientFailure;

        Result(boolean executed, boolean success, boolean transientFailure) {
            this.executed = executed;
            this.success = success;
            this.transientFailure = transientFailure;
        }
    }
}
