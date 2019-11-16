package com.buffrapp;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.pm.PackageInfoCompat;

import java.lang.ref.WeakReference;

import util.ServiceNetworkWorker;

public class UpdatesChecker extends Service {

    private static final String TAG = "UpdatesChecker";

    private UpdatesNetworkWorker updatesNetworkWorker;

    private Activity parentActivity;

    public UpdatesChecker() {
        this.parentActivity = null;
    }

    public UpdatesChecker(Activity parentActivity) {
        this.parentActivity = parentActivity;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        updatesNetworkWorker = new UpdatesNetworkWorker(this, parentActivity);
        updatesNetworkWorker.execute();
    }

    @Override
    public void onDestroy() {
        updatesNetworkWorker.cancel(true);

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }

    private void sendPushNotification(String status) {
        Log.d(TAG, "doInBackground: sending push notification...");

        String channel_name = getString(R.string.notifications_updates_channel_name);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String description = getString(R.string.notifications_updates_channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(channel_name, channel_name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            channel.enableVibration(true);
        }

        Intent intent = new Intent(this, UpdateDownloader.class);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, 0);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channel_name)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(status)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(status))
                .setContentIntent(pendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(getResources().getInteger(R.integer.updates_notification_id), notificationBuilder.build());
    }

    private static class UpdatesNetworkWorker extends ServiceNetworkWorker {
        private static final String UPDATE_PASS = "0"; // but no update found.
        private static final String UPDATE_ERROR = "1";
        private static final String UPDATE_FOUND = "2";

        private static final Character SYMBOL_AMPERSAND = '&';
        private static final Character SYMBOL_EQUALS = '=';
        private static final Character SYMBOL_BRACKET_OPEN = '[';
        private static final Character SYMBOL_BRACKET_CLOSED = ']';

        private WeakReference<UpdatesChecker> updateCheckerSvc;
        private WeakReference<Activity> parentActivity;

        UpdatesNetworkWorker(UpdatesChecker updateCheckerSvc, Activity parentActivity) {
            this.updateCheckerSvc = new WeakReference<>(updateCheckerSvc);
            this.parentActivity = new WeakReference<>(parentActivity);

            setTargetService(updateCheckerSvc);

            UpdatesChecker reference = this.updateCheckerSvc.get();

            if (reference != null) {
                setRequest(reference.getString(R.string.request_checkForUpdates));
                String key = reference.getString(R.string.server_content_param);

                try {
                    setEncodedData(SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 0 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + PackageInfoCompat.getLongVersionCode(reference.getPackageManager().getPackageInfo(reference.getPackageName(), 0)));
                } catch (PackageManager.NameNotFoundException nameNotFoundException) {
                    nameNotFoundException.printStackTrace();
                }
            }
        }

        @Override
        public void handleOutput(String serverOutput) {
            final Activity parentActivityReference = parentActivity.get();

            final UpdatesChecker reference = updateCheckerSvc.get();

            if (updateCheckerSvc == null) {
                Log.d(TAG, "doInBackground: failed to get a reference.");
                return;
            }

            switch (serverOutput) {
                case UPDATE_ERROR:
                    if (parentActivityReference != null) {
                        parentActivityReference.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(parentActivityReference, parentActivityReference.getString(R.string.updates_error), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                    break;
                case UPDATE_PASS:
                    if (parentActivityReference != null) {
                        parentActivityReference.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(parentActivityReference, parentActivityReference.getString(R.string.updates_none), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                    break;
                case UPDATE_FOUND:
                    if (parentActivityReference != null) {
                        parentActivityReference.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(parentActivityReference, parentActivityReference.getString(R.string.updates_found), Toast.LENGTH_LONG).show();
                            }
                        });
                    }

                    reference.sendPushNotification(reference.getString(R.string.updates_found));
                    break;
                default:
                    if (parentActivityReference != null) {
                        parentActivityReference.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(parentActivityReference, parentActivityReference.getString(R.string.updates_error), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
            }
        }

        @Override
        public void showInternalError(String message, Service reference) {
        }
    }
}
