/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright 2026 UCAS Score Query contributors
 */

package io.github.ucasscorequery.android;

import android.app.Application;

public final class ScoreQueryApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannel(this);
        Scheduler.restore(this);
    }
}
