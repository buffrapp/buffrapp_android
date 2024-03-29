package com.buffrapp;

import android.app.DownloadManager;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

public class UpdateDownloader extends IntentService {
    private static final String TAG = "UpdateDownloader";

    public UpdateDownloader() {
        super("UpdateDownloader");
    }

    private void removePushNotification() {
        final int id = getResources().getInteger(R.integer.update_ready_notification_id);

        Log.d(TAG, "doInBackground: removing push notification with ID " + id + "...");

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancel(id);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(getString(R.string.server_proto) + getString(R.string.server_hostname) + getString(R.string.server_path_latest)));
        request.setTitle(getString(R.string.app_name));
        request.setDescription(getString(R.string.notification_update_ongoing));
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, getString(R.string.updates_target_filename));

        downloadManager.enqueue(request);

        removePushNotification();

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(UpdateDownloader.this.getApplicationContext(), getString(R.string.notification_update_ongoing), Toast.LENGTH_LONG).show();
            }
        });
    }
}
