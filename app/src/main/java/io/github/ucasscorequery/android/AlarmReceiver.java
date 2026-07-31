/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright 2026 UCAS Score Query contributors
 */

package io.github.ucasscorequery.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/** Receives query, watchdog and recovery alarms. */
public final class AlarmReceiver extends BroadcastReceiver {
    static final String ACTION_QUERY_DUE =
            "io.github.ucasscorequery.android.action.QUERY_DUE";
    static final String ACTION_RECOVER_SERVICE =
            "io.github.ucasscorequery.android.action.RECOVER_SERVICE";
    static final String ACTION_WATCHDOG =
            "io.github.ucasscorequery.android.action.WATCHDOG";

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                PowerManager.WakeLock wakeLock = null;
                try {
                    AppSettings settings = AppPrefs.loadSettings(context);
                    if (!settings.autoEnabled) return;
                    PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                    if (power != null) {
                        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                "UCASScoreQuery:alarm-receiver");
                        wakeLock.setReferenceCounted(false);
                        wakeLock.acquire(90_000L);
                    }
                    String action = intent == null ? "" : intent.getAction();
                    if (ACTION_QUERY_DUE.equals(action)) {
                        Scheduler.handleQueryDue(context);
                    } else if (ACTION_WATCHDOG.equals(action)) {
                        Scheduler.handleWatchdog(context);
                    } else {
                        Scheduler.handleServiceRecovery(context);
                    }
                } finally {
                    if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                    pending.finish();
                }
            }
        }, "ucas-alarm-receiver").start();
    }
}
