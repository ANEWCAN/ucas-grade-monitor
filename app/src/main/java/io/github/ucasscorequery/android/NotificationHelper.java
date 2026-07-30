/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright 2026 UCAS Score Query contributors
 */

package io.github.ucasscorequery.android;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.List;

final class NotificationHelper {
    private static final String CHANNEL_ID = "score_query_results";

    private NotificationHelper() {}

    static void createChannel(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "成绩查询通知", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("自动查询成绩后的结果通知");
        manager.createNotificationChannel(channel);
    }

    static void notifyResult(Context context, String title, String text, List<Score> changes) {
        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        createChannel(context);
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder builder = new android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pending)
                .setAutoCancel(true);
        if (changes != null && !changes.isEmpty()) {
            StringBuilder detail = new StringBuilder();
            for (Score score : changes) {
                if (detail.length() > 0) detail.append('\n');
                detail.append(score.courseName).append("：").append(score.score);
            }
            builder.setStyle(new android.app.Notification.BigTextStyle().bigText(detail.toString()));
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(1001, builder.build());
    }
}
