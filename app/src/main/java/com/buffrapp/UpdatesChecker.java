package com.buffrapp;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.SSLCertificateSocketFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.pm.PackageInfoCompat;

import org.apache.http.conn.ssl.AllowAllHostnameVerifier;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class UpdatesChecker extends Service {

    private static final String TAG = "UpdatesChecker";

    private NetworkWorker networkWorker;

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

        networkWorker = new NetworkWorker(this, parentActivity);
        networkWorker.execute();
    }

    @Override
    public void onDestroy() {
        networkWorker.cancel(true);

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

    private static class NetworkWorker extends AsyncTask<Void, Void, Void> {
        private static final String UPDATE_PASS = "0"; // but no update found.
        private static final String UPDATE_ERROR = "1";
        private static final String UPDATE_FOUND = "2";

        private static final Character SYMBOL_AMPERSAND = '&';
        private static final Character SYMBOL_EQUALS = '=';
        private static final Character SYMBOL_BRACKET_OPEN = '[';
        private static final Character SYMBOL_BRACKET_CLOSED = ']';

        private WeakReference<UpdatesChecker> updateCheckerSvc;
        private WeakReference<Activity> parentActivity;

        NetworkWorker(UpdatesChecker updateCheckerSvc, Activity parentActivity) {
            this.updateCheckerSvc = new WeakReference<>(updateCheckerSvc);
            this.parentActivity = new WeakReference<>(parentActivity);
        }

        private String getEncodedProfileData(String key, long currentVersionCode) {
            return SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 0 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + currentVersionCode;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            final Activity parentActivityReference = parentActivity.get();

            final UpdatesChecker reference = updateCheckerSvc.get();

            if (updateCheckerSvc == null) {
                Log.d(TAG, "doInBackground: failed to get a reference.");
                return null;
            }

            String preURL = reference.getString(R.string.server_proto) + reference.getString(R.string.server_hostname) + reference.getString(R.string.server_path);
            Log.d(TAG, "populateView: generated URL from resources: \"" + preURL + "\"");

            try {
                URL url = new URL(preURL);
                HttpsURLConnection httpsURLConnection = null;

                try {
                    // Try to open a connection.
                    httpsURLConnection = (HttpsURLConnection) url.openConnection();
                    httpsURLConnection.setConnectTimeout(reference.getResources().getInteger(R.integer.connection_timeout));
                    httpsURLConnection.setRequestMethod(reference.getString(R.string.server_request_method));

                    // Set the cookies.
                    String cookie = PreferenceManager.getDefaultSharedPreferences(reference).getString(reference.getString(R.string.key_session_id), null);
                    Log.d(TAG, "doInBackground: cookie: " + cookie);
                    httpsURLConnection.setRequestProperty(reference.getString(R.string.server_cookie_request_key), cookie);

                    // TODO: DEBUGGING!!! REMOVE THIS FOR PRODUCTION.
                    httpsURLConnection.setSSLSocketFactory(SSLCertificateSocketFactory.getInsecure(0, null));
                    httpsURLConnection.setHostnameVerifier(new AllowAllHostnameVerifier());

                    Uri.Builder builder = new Uri.Builder()
                            .appendQueryParameter(reference.getString(R.string.server_request_param), reference.getString(R.string.request_checkForUpdates));

                    String query = builder.build().getEncodedQuery() + getEncodedProfileData(reference.getString(R.string.server_content_param), PackageInfoCompat.getLongVersionCode(reference.getPackageManager().getPackageInfo(reference.getPackageName(), 0)));
                    Log.d(TAG, "doInBackground: query: " + query);

                    // Write POST data.
                    OutputStream outputStream = new BufferedOutputStream(httpsURLConnection.getOutputStream());
                    BufferedWriter bufferedWriter = new BufferedWriter(
                            new OutputStreamWriter(outputStream, reference.getString(R.string.server_encoding)));
                    bufferedWriter.write(query);
                    bufferedWriter.flush();
                    bufferedWriter.close();

                    // Retrieve the response.
                    InputStream inputStream = new BufferedInputStream(httpsURLConnection.getInputStream());
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line);
                    }

                    Log.d(TAG, "populateView: done fetching data, the result is: \"" + stringBuilder.toString() + "\"");

                    switch (stringBuilder.toString()) {
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
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (httpsURLConnection != null) {
                        httpsURLConnection.disconnect();
                    }
                }
            } catch (final MalformedURLException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
