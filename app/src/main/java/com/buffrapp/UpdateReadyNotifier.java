package com.buffrapp;

import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class UpdateReadyNotifier extends BroadcastReceiver {
    private static final String TAG = "UpdateReadyNotifier";

    private void removePushNotification(Context context) {
        final int id = context.getResources().getInteger(R.integer.update_ready_notification_id);

        Log.d(TAG, "doInBackground: removing push notification with ID " + id + "...");

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancel(id);
    }

    private void sendPushNotification(Context context, String status, long downloadID) {
        Log.d(TAG, "doInBackground: sending push notification...");

        String channel_name = context.getString(R.string.notifications_update_ready_channel_name);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String description = context.getString(R.string.notifications_update_ready_channel_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(channel_name, channel_name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            channel.enableVibration(true);
        }

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = downloadManager.getUriForDownloadedFile(downloadID);

        Log.d(TAG, "sendPushNotification: uri: " + uri.toString());

        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        install.setDataAndType(uri, downloadManager.getMimeTypeForDownloadedFile(downloadID));

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, install, 0);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, channel_name)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(context.getString(R.string.app_name))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentText(status)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(status))
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(context.getResources().getInteger(R.integer.update_ready_notification_id), notificationBuilder.build());
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.d(TAG, "onReceive: download completed.");

        long downloadID = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

        if (intent.getAction().equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {

            removePushNotification(context);
            sendPushNotification(context, context.getString(R.string.notification_update_completed), downloadID);

            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, context.getString(R.string.toast_update_completed), Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
