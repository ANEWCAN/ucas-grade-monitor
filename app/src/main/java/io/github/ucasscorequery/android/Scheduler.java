/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright 2026 UCAS Score Query contributors
 */

package io.github.ucasscorequery.android;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PersistableBundle;

/**
 * Redundant automatic-query scheduler.
 *
 * <p>The query is guarded by four independent paths: an idle-aware one-shot alarm,
 * a persisted one-shot JobScheduler job, a periodic watchdog job, and a watchdog
 * alarm. The watchdog never advances an overdue timestamp until a query actually
 * starts, so a failed foreground-service launch cannot permanently consume the run.</p>
 */
final class Scheduler {
    static final String JOB_MODE_KEY = "mode";
    static final String JOB_MODE_QUERY = "query";
    static final String JOB_MODE_WATCHDOG = "watchdog";

    private static final int QUERY_JOB_ID = 196811;
    private static final int WATCHDOG_JOB_ID = 196814;
    private static final int QUERY_ALARM_REQUEST = 196812;
    private static final int RECOVERY_ALARM_REQUEST = 196813;
    private static final int WATCHDOG_ALARM_REQUEST = 196815;

    private static final long QUERY_DEADLINE_GRACE_MS = 5L * 60_000L;
    private static final long WATCHDOG_ALARM_INTERVAL_MS = 10L * 60_000L;
    private static final long HEARTBEAT_STALE_MS = 100_000L;
    private static final long OVERDUE_TOLERANCE_MS = 15_000L;

    private Scheduler() {}

    static void apply(Context context, AppSettings settings) {
        Context app = context.getApplicationContext();
        cancelAll(app);
        if (!settings.autoEnabled) {
            AppPrefs.clearNextRunAt(app);
            AutoQueryService.stop(app);
            return;
        }
        long now = System.currentTimeMillis();
        scheduleAt(app, settings, now + intervalMillis(settings));
        scheduleWatchdogs(app);
        boolean started = AutoQueryService.start(app, AutoQueryService.ACTION_START);
        if (!started) scheduleServiceRecovery(app, 20_000L);
        AppPrefs.setLastSchedulerEvent(app, "已保存设置并重建全部自动查询任务");
    }

    static void restore(Context context) {
        Context app = context.getApplicationContext();
        AppSettings settings = AppPrefs.loadSettings(app);
        if (!settings.autoEnabled) {
            cancelAll(app);
            AppPrefs.clearNextRunAt(app);
            AutoQueryService.stop(app);
            return;
        }

        long now = System.currentTimeMillis();
        long next = AppPrefs.getNextRunAt(app);
        if (next <= 0L) {
            next = now + intervalMillis(settings);
        } else if (next < now - OVERDUE_TOLERANCE_MS) {
            // Preserve the overdue state. The watchdog will run immediately rather
            // than silently moving the missed query to a later interval.
            next = Math.min(next, now);
        }
        scheduleAt(app, settings, next);
        scheduleWatchdogs(app);
        boolean started = AutoQueryService.start(app,
                next <= now + OVERDUE_TOLERANCE_MS
                        ? AutoQueryService.ACTION_RUN_QUERY
                        : AutoQueryService.ACTION_START);
        if (!started) {
            scheduleImmediateQueryJob(app);
            scheduleServiceRecovery(app, 20_000L);
        }
        AppPrefs.setLastSchedulerEvent(app, "已恢复自动查询调度");
    }

    static void ensureHealthy(Context context, boolean allowRunOverdue) {
        Context app = context.getApplicationContext();
        AppSettings settings = AppPrefs.loadSettings(app);
        if (!settings.autoEnabled) {
            cancelAll(app);
            return;
        }

        long now = System.currentTimeMillis();
        long next = AppPrefs.getNextRunAt(app);
        if (next <= 0L) {
            next = now + intervalMillis(settings);
            AppPrefs.setNextRunAt(app, next);
        }

        // Re-arm all one-shot mechanisms from the persisted timestamp. This is
        // idempotent and repairs alarms/jobs removed by OEM background managers.
        scheduleQueryAlarm(app, next);
        scheduleQueryFallbackJob(app, next);
        scheduleWatchdogs(app);

        long heartbeat = AppPrefs.getServiceHeartbeat(app);
        boolean serviceStale = heartbeat <= 0L || now - heartbeat > HEARTBEAT_STALE_MS;
        boolean overdue = next <= now + OVERDUE_TOLERANCE_MS;
        String action = allowRunOverdue && overdue
                ? AutoQueryService.ACTION_RUN_QUERY
                : AutoQueryService.ACTION_START;
        if (serviceStale || overdue) {
            boolean started = AutoQueryService.start(app, action);
            if (!started) {
                AppPrefs.setLastSchedulerEvent(app,
                        "前台服务启动受限，已切换系统任务补查");
                if (overdue) scheduleImmediateQueryJob(app);
                scheduleServiceRecovery(app, 30_000L);
            } else if (overdue && allowRunOverdue) {
                AppPrefs.setLastSchedulerEvent(app, "检测到逾期任务，已立即触发查询");
            }
        }
    }

    static void handleQueryDue(Context context) {
        Context app = context.getApplicationContext();
        AppPrefs.setLastSchedulerEvent(app, "唤醒闹钟已到达，正在触发自动查询");
        boolean started = AutoQueryService.start(app, AutoQueryService.ACTION_RUN_QUERY);
        if (!started) {
            scheduleImmediateQueryJob(app);
            scheduleServiceRecovery(app, 20_000L);
        }
        scheduleWatchdogAlarm(app, System.currentTimeMillis() + 60_000L);
    }

    static void handleWatchdog(Context context) {
        AppPrefs.setLastSchedulerEvent(context, "后台守护检查已执行");
        ensureHealthy(context, true);
        scheduleWatchdogAlarm(context.getApplicationContext(),
                System.currentTimeMillis() + WATCHDOG_ALARM_INTERVAL_MS);
    }

    static void handleServiceRecovery(Context context) {
        AppPrefs.setLastSchedulerEvent(context, "正在恢复后台常驻服务");
        ensureHealthy(context, true);
    }

    static void scheduleNext(Context context, AppSettings settings, long fromMillis) {
        if (!settings.autoEnabled) return;
        long next = Math.max(System.currentTimeMillis(), fromMillis) + intervalMillis(settings);
        scheduleAt(context.getApplicationContext(), settings, next);
        scheduleWatchdogs(context.getApplicationContext());
    }

    static void scheduleRetry(Context context, AppSettings settings) {
        if (!settings.autoEnabled) return;
        long regular = intervalMillis(settings);
        long retryDelay = Math.min(10L * 60_000L, regular);
        scheduleAt(context.getApplicationContext(), settings,
                System.currentTimeMillis() + retryDelay);
        scheduleWatchdogs(context.getApplicationContext());
    }

    static long intervalMillis(AppSettings settings) {
        return Math.max(15L, settings.intervalMinutes) * 60_000L;
    }

    static boolean canScheduleExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return manager != null && manager.canScheduleExactAlarms();
    }

    static void scheduleServiceRecovery(Context context, long delayMillis) {
        AppSettings settings = AppPrefs.loadSettings(context);
        if (!settings.autoEnabled) return;
        scheduleAlarm(context.getApplicationContext(), RECOVERY_ALARM_REQUEST,
                AlarmReceiver.ACTION_RECOVER_SERVICE,
                System.currentTimeMillis() + Math.max(10_000L, delayMillis), true);
    }

    static void cancel(Context context) {
        cancelAll(context.getApplicationContext());
        AppPrefs.clearNextRunAt(context);
        AutoQueryService.stop(context);
    }

    private static void scheduleAt(Context context, AppSettings settings, long nextAtMillis) {
        if (!settings.autoEnabled) return;
        AppPrefs.setNextRunAt(context, nextAtMillis);
        scheduleQueryAlarm(context, nextAtMillis);
        scheduleQueryFallbackJob(context, nextAtMillis);
        AutoQueryService.updateNotification(context);
    }

    private static void scheduleQueryAlarm(Context context, long nextAtMillis) {
        scheduleAlarm(context, QUERY_ALARM_REQUEST,
                AlarmReceiver.ACTION_QUERY_DUE, nextAtMillis, true);
    }

    private static void scheduleWatchdogs(Context context) {
        scheduleWatchdogAlarm(context,
                System.currentTimeMillis() + WATCHDOG_ALARM_INTERVAL_MS);
        schedulePeriodicWatchdogJob(context);
    }

    private static void scheduleWatchdogAlarm(Context context, long when) {
        scheduleAlarm(context, WATCHDOG_ALARM_REQUEST,
                AlarmReceiver.ACTION_WATCHDOG, when, true);
    }

    private static void scheduleAlarm(Context context, int requestCode,
                                      String action, long when, boolean wakeup) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, AlarmReceiver.class).setAction(action);
        PendingIntent pending = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        int type = wakeup ? AlarmManager.RTC_WAKEUP : AlarmManager.RTC;
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                if (canScheduleExactAlarms(context)) {
                    manager.setExactAndAllowWhileIdle(type, when, pending);
                } else {
                    manager.setAndAllowWhileIdle(type, when, pending);
                }
            } else {
                manager.setExact(type, when, pending);
            }
        } catch (SecurityException error) {
            AppPrefs.setServiceStartError(context,
                    "精确闹钟权限不足：" + error.getClass().getSimpleName());
            manager.set(type, when, pending);
        }
    }

    private static void scheduleQueryFallbackJob(Context context, long nextAtMillis) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        long delay = Math.max(0L, nextAtMillis - System.currentTimeMillis());
        PersistableBundle extras = new PersistableBundle();
        extras.putString(JOB_MODE_KEY, JOB_MODE_QUERY);
        JobInfo info = new JobInfo.Builder(QUERY_JOB_ID,
                new ComponentName(context, QueryJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setMinimumLatency(delay)
                .setOverrideDeadline(delay + QUERY_DEADLINE_GRACE_MS)
                .setBackoffCriteria(60_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setExtras(extras)
                .build();
        scheduler.schedule(info);
    }

    private static void scheduleImmediateQueryJob(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        PersistableBundle extras = new PersistableBundle();
        extras.putString(JOB_MODE_KEY, JOB_MODE_QUERY);
        JobInfo info = new JobInfo.Builder(QUERY_JOB_ID,
                new ComponentName(context, QueryJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(1_000L)
                .setOverrideDeadline(90_000L)
                .setBackoffCriteria(60_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setExtras(extras)
                .build();
        scheduler.schedule(info);
    }

    private static void schedulePeriodicWatchdogJob(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        if (Build.VERSION.SDK_INT >= 24 && scheduler.getPendingJob(WATCHDOG_JOB_ID) != null) return;
        PersistableBundle extras = new PersistableBundle();
        extras.putString(JOB_MODE_KEY, JOB_MODE_WATCHDOG);
        JobInfo.Builder builder = new JobInfo.Builder(WATCHDOG_JOB_ID,
                new ComponentName(context, QueryJobService.class))
                .setPersisted(true)
                .setPeriodic(15L * 60_000L)
                .setExtras(extras);
        scheduler.schedule(builder.build());
    }

    private static void cancelAll(Context context) {
        cancelAlarm(context, QUERY_ALARM_REQUEST, AlarmReceiver.ACTION_QUERY_DUE);
        cancelAlarm(context, RECOVERY_ALARM_REQUEST, AlarmReceiver.ACTION_RECOVER_SERVICE);
        cancelAlarm(context, WATCHDOG_ALARM_REQUEST, AlarmReceiver.ACTION_WATCHDOG);
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) {
            scheduler.cancel(QUERY_JOB_ID);
            scheduler.cancel(WATCHDOG_JOB_ID);
        }
    }

    private static void cancelAlarm(Context context, int requestCode, String action) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, AlarmReceiver.class).setAction(action);
        PendingIntent pending = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) {
            manager.cancel(pending);
            pending.cancel();
        }
    }
}
