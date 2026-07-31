/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright 2026 UCAS Score Query contributors
 */

package io.github.ucasscorequery.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Sticky foreground guard for automatic score queries. */
public final class AutoQueryService extends Service {
    static final String ACTION_START =
            "io.github.ucasscorequery.android.action.START_AUTO_SERVICE";
    static final String ACTION_RUN_QUERY =
            "io.github.ucasscorequery.android.action.RUN_AUTO_QUERY";
    static final String ACTION_STOP =
            "io.github.ucasscorequery.android.action.STOP_AUTO_SERVICE";
    static final String ACTION_REFRESH_NOTIFICATION =
            "io.github.ucasscorequery.android.action.REFRESH_AUTO_NOTIFICATION";
    static final String ACTION_HEALTH_CHECK =
            "io.github.ucasscorequery.android.action.HEALTH_CHECK";

    private static final String CHANNEL_ID = "auto_query_guard_v2";
    private static final int NOTIFICATION_ID = 2700;
    private static final long WAKE_LOCK_TIMEOUT_MS = 45L * 60_000L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean foregroundStarted;
    private boolean receiverRegistered;
    private final AtomicBoolean queryQueued = new AtomicBoolean(false);

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!AppPrefs.loadSettings(AutoQueryService.this).autoEnabled) return;
            AppPrefs.setLastSchedulerEvent(AutoQueryService.this,
                    "系统状态变化，正在检查逾期任务");
            Scheduler.ensureHealthy(AutoQueryService.this, true);
            runIfOverdue();
        }
    };

    private final Runnable heartbeatTicker = new Runnable() {
        @Override
        public void run() {
            if (!foregroundStarted) return;
            long now = System.currentTimeMillis();
            AppPrefs.setServiceHeartbeat(AutoQueryService.this, now);
            refreshNotificationInternal();
            Scheduler.ensureHealthy(AutoQueryService.this, false);
            runIfOverdue();
            handler.postDelayed(this, 30_000L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        registerStateReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        AppSettings settings = AppPrefs.loadSettings(this);
        if (ACTION_STOP.equals(action) || !settings.autoEnabled) {
            AppPrefs.setServiceHeartbeat(this, 0L);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        ensureForeground();
        AppPrefs.setServiceStartError(this, "");
        AppPrefs.setServiceHeartbeat(this, System.currentTimeMillis());
        Scheduler.ensureHealthy(this, false);

        if (ACTION_RUN_QUERY.equals(action)) {
            runQueryInForeground();
        } else if (ACTION_REFRESH_NOTIFICATION.equals(action)) {
            refreshNotificationInternal();
        } else if (ACTION_HEALTH_CHECK.equals(action)) {
            runIfOverdue();
        } else {
            runIfOverdue();
        }
        return START_STICKY;
    }

    private void ensureForeground() {
        if (foregroundStarted) return;
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        foregroundStarted = true;
        handler.removeCallbacks(heartbeatTicker);
        handler.post(heartbeatTicker);
    }

    private void runIfOverdue() {
        AppSettings settings = AppPrefs.loadSettings(this);
        if (!settings.autoEnabled || AutoQueryEngine.isRunning()) return;
        long next = AppPrefs.getNextRunAt(this);
        if (next > 0L && next <= System.currentTimeMillis() + 10_000L) {
            runQueryInForeground();
        }
    }

    private void runQueryInForeground() {
        if (AutoQueryEngine.isRunning() || !queryQueued.compareAndSet(false, true)) return;
        refreshNotificationInternal();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                PowerManager.WakeLock wakeLock = null;
                try {
                    PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (power != null) {
                        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                "UCASScoreQuery:auto-query");
                        wakeLock.setReferenceCounted(false);
                        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
                    }
                    AutoQueryEngine.run(AutoQueryService.this);
                } finally {
                    queryQueued.set(false);
                    if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            refreshNotificationInternal();
                            Scheduler.ensureHealthy(AutoQueryService.this, false);
                        }
                    });
                }
            }
        });
    }

    private Notification buildNotification() {
        AppSettings settings = AppPrefs.loadSettings(this);
        QueryProgress progress = AppPrefs.loadQueryProgress(this);
        long next = AppPrefs.getNextRunAt(this);
        String title;
        String text;
        int progressValue = 0;
        boolean indeterminate = false;

        if (progress.active && progress.automatic) {
            title = progress.stage.isEmpty() ? "正在自动查询成绩" : progress.stage;
            text = progress.detail.isEmpty() ? "自动查询正在进行" : progress.detail;
            progressValue = progress.percent;
            indeterminate = progress.percent <= 0 || progress.percent >= 100;
        } else if (!settings.autoEnabled) {
            title = "自动查询未启用";
            text = "请在应用设置中开启自动查询";
        } else if (next > 0L) {
            title = "自动查询保持运行";
            long remain = Math.max(0L, next - System.currentTimeMillis());
            text = (remain <= 0L ? "查询时间已到，正在等待执行" : "距下次查询 " + formatRemaining(remain))
                    + " · " + new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                    .format(new Date(next));
        } else {
            title = "自动查询保持运行";
            text = "正在修复后台调度";
        }

        Intent openIntent = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent runIntent = new Intent(this, AutoQueryService.class)
                .setAction(ACTION_RUN_QUERY);
        PendingIntent runPending = PendingIntent.getService(
                this, 1, runIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setShowWhen(false)
                .addAction(android.R.drawable.ic_popup_sync, "立即查询", runPending);
        if (progress.active && progress.automatic) {
            builder.setProgress(100, progressValue, indeterminate);
            builder.setStyle(new Notification.BigTextStyle().bigText(text));
        }
        return builder.build();
    }

    private void refreshNotificationInternal() {
        if (!foregroundStarted) return;
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "自动查询后台守护", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("维持熄屏定时查询并显示实时查询进度");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private void registerStateReceiver() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }
        receiverRegistered = true;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (AppPrefs.loadSettings(this).autoEnabled) {
            Scheduler.scheduleServiceRecovery(this, 15_000L);
            Scheduler.ensureHealthy(this, true);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onTrimMemory(int level) {
        if (level >= TRIM_MEMORY_UI_HIDDEN
                && AppPrefs.loadSettings(this).autoEnabled) {
            Scheduler.ensureHealthy(this, false);
            Scheduler.scheduleServiceRecovery(this, 60_000L);
        }
        super.onTrimMemory(level);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(heartbeatTicker);
        if (receiverRegistered) {
            try {
                unregisterReceiver(stateReceiver);
            } catch (RuntimeException ignored) {}
            receiverRegistered = false;
        }
        executor.shutdownNow();
        foregroundStarted = false;
        AppPrefs.setServiceHeartbeat(this, 0L);
        if (AppPrefs.loadSettings(this).autoEnabled) {
            Scheduler.scheduleServiceRecovery(this, 30_000L);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    static boolean start(Context context, String action) {
        Intent intent = new Intent(context, AutoQueryService.class).setAction(action);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
            AppPrefs.setServiceStartError(context, "");
            return true;
        } catch (RuntimeException error) {
            String message = error.getClass().getSimpleName();
            if (error.getMessage() != null && !error.getMessage().trim().isEmpty()) {
                message += "：" + error.getMessage().trim();
            }
            AppPrefs.setServiceStartError(context, message);
            AppPrefs.setLastSchedulerEvent(context, "后台服务启动失败，已启用系统任务兜底");
            return false;
        }
    }

    static void stop(Context context) {
        Intent intent = new Intent(context, AutoQueryService.class).setAction(ACTION_STOP);
        try {
            context.startService(intent);
        } catch (RuntimeException ignored) {
            context.stopService(new Intent(context, AutoQueryService.class));
        }
    }

    static void updateNotification(Context context) {
        AppSettings settings = AppPrefs.loadSettings(context);
        if (!settings.autoEnabled) return;
        start(context, ACTION_REFRESH_NOTIFICATION);
    }

    private static String formatRemaining(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (days > 0L) {
            return String.format(Locale.CHINA, "%d天 %02d:%02d:%02d",
                    days, hours, minutes, seconds);
        }
        return String.format(Locale.CHINA, "%02d:%02d:%02d",
                hours, minutes, seconds);
    }
}
