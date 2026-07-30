/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright 2026 UCAS Score Query contributors
 */

package io.github.ucasscorequery.android;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.util.ArrayList;
import java.util.List;

public final class QueryJobService extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean shouldReschedule = runQuery();
                jobFinished(params, shouldReschedule);
            }
        }, "ucas-auto-query").start();
        return true;
    }

    private boolean runQuery() {
        AppSettings settings = AppPrefs.loadSettings(this);
        if (!settings.autoEnabled) return false;
        long started = System.currentTimeMillis();
        if (!settings.credentials.isComplete()) {
            QueryRecord record = new QueryRecord(false, System.currentTimeMillis(), 0,
                    "自动查询失败：请先在应用中完整填写账号、密码和接口密钥。",
                    new ArrayList<Score>(), new ArrayList<Score>());
            AppPrefs.saveRecord(this, "last_auto_result", record);
            if (AppSettings.NOTIFY_ALL.equals(settings.notifyMode)) {
                NotificationHelper.notifyResult(this, "成绩自动查询失败", record.message, null);
            }
            return false;
        }
        try {
            QueryRunner.Result result = QueryRunner.execute(settings.credentials, settings.retryCount);
            List<Score> scores = result.scores;
            List<Score> baseline = AppPrefs.loadBaseline(this);
            List<Score> changes = AppPrefs.calculateChanges(baseline, scores);
            boolean first = baseline == null;
            AppPrefs.saveBaseline(this, scores);
            String retrySummary = result.attempts > 1
                    ? "第 " + result.attempts + " 次整轮尝试成功（已重试 " + (result.attempts - 1) + " 次）。"
                    : "";
            String message = first
                    ? "自动查询成功，已建立成绩基线，共 " + scores.size() + " 条记录。" + retrySummary
                    : (changes.isEmpty()
                        ? "自动查询成功，未发现新成绩或成绩变化，共 " + scores.size() + " 条记录。" + retrySummary
                        : "自动查询成功，发现 " + changes.size() + " 条新成绩或成绩变化。" + retrySummary);
            QueryRecord record = new QueryRecord(true, System.currentTimeMillis(),
                    System.currentTimeMillis() - started, message, scores, changes);
            AppPrefs.saveRecord(this, "last_auto_result", record);
            AppPrefs.saveRecord(this, "last_success_result", record);
            if (AppSettings.NOTIFY_ALL.equals(settings.notifyMode)) {
                NotificationHelper.notifyResult(this, "成绩自动查询完成", message, changes);
            } else if (!first && !changes.isEmpty()) {
                NotificationHelper.notifyResult(this, "发现新成绩或成绩变化", message, changes);
            }
            return false;
        } catch (Exception error) {
            boolean transientFailure = QueryRunner.shouldSystemReschedule(error);
            String message = "自动查询失败：" + cleanError(error);
            if (transientFailure) message += " 系统将按退避策略自动补跑。";
            QueryRecord record = new QueryRecord(false, System.currentTimeMillis(),
                    System.currentTimeMillis() - started, message,
                    new ArrayList<Score>(), new ArrayList<Score>());
            AppPrefs.saveRecord(this, "last_auto_result", record);
            if (AppSettings.NOTIFY_ALL.equals(settings.notifyMode)) {
                NotificationHelper.notifyResult(this, "成绩自动查询失败", message, null);
            }
            return transientFailure;
        }
    }

    private static String cleanError(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message.trim();
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
