/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright 2026 UCAS Score Query contributors
 */

package io.github.ucasscorequery.android;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.PowerManager;

/** Persisted JobScheduler fallback and periodic watchdog. */
public final class QueryJobService extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                PowerManager.WakeLock wakeLock = null;
                boolean reschedule = false;
                try {
                    AppSettings settings = AppPrefs.loadSettings(QueryJobService.this);
                    if (!settings.autoEnabled) return;
                    String mode = params.getExtras().getString(Scheduler.JOB_MODE_KEY);
                    if (mode == null || mode.isEmpty()) mode = Scheduler.JOB_MODE_QUERY;
                    if (Scheduler.JOB_MODE_WATCHDOG.equals(mode)) {
                        Scheduler.ensureHealthy(QueryJobService.this, true);
                        return;
                    }

                    long next = AppPrefs.getNextRunAt(QueryJobService.this);
                    if (next > System.currentTimeMillis() + 60_000L) {
                        Scheduler.ensureHealthy(QueryJobService.this, false);
                        return;
                    }
                    PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
                    if (power != null) {
                        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                "UCASScoreQuery:job-fallback");
                        wakeLock.setReferenceCounted(false);
                        wakeLock.acquire(45L * 60_000L);
                    }
                    AutoQueryService.start(QueryJobService.this,
                            AutoQueryService.ACTION_START);
                    AutoQueryEngine.Result result = AutoQueryEngine.run(QueryJobService.this);
                    reschedule = result.transientFailure;
                } finally {
                    if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                    jobFinished(params, reschedule);
                }
            }
        }, "ucas-query-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
