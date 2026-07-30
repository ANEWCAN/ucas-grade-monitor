/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright 2026 UCAS Score Query contributors
 */

package io.github.ucasscorequery.android;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

final class Scheduler {
    private static final int JOB_ID = 196811;

    private Scheduler() {}

    static void apply(Context context, AppSettings settings) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        scheduler.cancel(JOB_ID);
        if (!settings.autoEnabled) return;
        long interval = Math.max(15L, settings.intervalMinutes) * 60_000L;
        JobInfo info = new JobInfo.Builder(JOB_ID,
                new ComponentName(context, QueryJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setBackoffCriteria(5L * 60_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPeriodic(interval)
                .build();
        scheduler.schedule(info);
    }

    static void restore(Context context) {
        AppSettings settings = AppPrefs.loadSettings(context);
        if (settings.autoEnabled) apply(context, settings);
    }
}
