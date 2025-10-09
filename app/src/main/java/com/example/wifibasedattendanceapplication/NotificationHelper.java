package com.example.wifibasedattendanceapplication;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public final class NotificationHelper {

    public static final String CHANNEL_ID_SESSION = "attendance_session_updates";

    private NotificationHelper() { }

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_SESSION,
                    "Attendance Session Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications when attendance is finalized for your class");
            channel.enableLights(true);
            channel.setLightColor(Color.BLUE);
            channel.enableVibration(true);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static void notifyAttendanceResult(Context context, int notificationId, String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_SESSION)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }
}


