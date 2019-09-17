package com.buffrapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.SSLCertificateSocketFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.json.JSONArray;
import org.json.JSONObject;

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
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.HttpsURLConnection;

public class OrderStatusLooper extends Service {

    private static final String TAG = "OrderStatusLooper";

    private boolean shouldKeepCycling;
    private boolean shouldKeepNotification;
    private Timer timer;
    private NetworkWorker networkWorker;

    private String previousStatus;

    public OrderStatusLooper() {
    } // I'm not implementing this, seriously.

    @Override
    public void onCreate() {
        super.onCreate();

        shouldKeepCycling = true;
        shouldKeepNotification = false;

        timer = new Timer();
        TimerTask updatingTask = new TimerTask() {
            public void run() {
                networkWorker = new OrderStatusLooper.NetworkWorker(OrderStatusLooper.this);
                networkWorker.execute();
            }
        };
        timer.schedule(updatingTask, 0, getResources().getInteger(R.integer.update_interval));
    }

    @Override
    public void onDestroy() {
        timer.cancel();
        timer.purge();
        networkWorker.cancel(true);
        removePushNotification();

        super.onDestroy();
    }

    private void stopPolling() {
        timer.cancel();
        timer.purge();
        networkWorker.cancel(true);
        if (!shouldKeepNotification) {
            removePushNotification();
        }

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

    private void sendPushNotification(int progress, String status) {
        if (status != previousStatus) {
            Log.d(TAG, "doInBackground: sending push notification...");

            final int PROGRESS_MAX = 100;

            String channel_name = getString(R.string.channel_name);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String description = getString(R.string.channel_description);
                int importance = NotificationManager.IMPORTANCE_DEFAULT;
                NotificationChannel channel = new NotificationChannel(channel_name, channel_name, importance);
                channel.setDescription(description);
                NotificationManager notificationManager = getSystemService(NotificationManager.class);
                notificationManager.createNotificationChannel(channel);
            }

            Intent intent = new Intent(this, Requests.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channel_name)
                    .setSmallIcon(R.drawable.logo)
                    .setContentTitle(getString(R.string.app_name))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentText(status)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(status))
                    .setContentIntent(pendingIntent)
                    .setProgress(PROGRESS_MAX, progress, false)
                    .setOngoing(true);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.notify(getResources().getInteger(R.integer.notification_id), notificationBuilder.build());

            previousStatus = status;
        } else {
            Log.d(TAG, "sendPushNotification: aborted request, duplicated status detected.");
        }
    }

    private void removePushNotification() {
        final int id = getResources().getInteger(R.integer.notification_id);

        Log.d(TAG, "doInBackground: removing push notification with ID " + id + "...");

        String channel_name = getString(R.string.channel_name);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(channel_name, channel_name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancel(id);
    }

    private static class NetworkWorker extends AsyncTask<Void, Void, Void> {
        private static final String ORDER_ERROR = "1";
        private static final String ORDERS_NOT_ALLOWED = "2";
        private static final String ORDERS_NO_ORDERS = "3";
        private WeakReference<OrderStatusLooper> orderStatusLooperSvc;

        NetworkWorker(OrderStatusLooper orderStatusLooperSvc) {
            this.orderStatusLooperSvc = new WeakReference<>(orderStatusLooperSvc);
        }

        private void showInternalError() {
            Log.d(TAG, "doInBackground: an internal error has occurred.");

        }

        private void showNoOrders() {
            Log.d(TAG, "doInBackground: no ongoing orders found.");

        }

        private void showDataFields() {
            Log.d(TAG, "doInBackground: an order has been found.");

        }

        @Override
        protected Void doInBackground(Void... voids) {
            final OrderStatusLooper reference = orderStatusLooperSvc.get();

            if (orderStatusLooperSvc == null) {
                Log.d(TAG, "doInBackground: failed to get a reference.");
                return null;
            }

            String preURL = reference.getString(R.string.server_proto) + reference.getString(R.string.server_ip) + reference.getString(R.string.server_path);
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
                            .appendQueryParameter(reference.getString(R.string.server_request_param), reference.getString(R.string.request_getUserOrders));

                    String query = builder.build().getEncodedQuery();
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
                        case ORDER_ERROR:
                            showInternalError();
                            break;
                        case ORDERS_NOT_ALLOWED:
                            showInternalError();
                            break;
                        case ORDERS_NO_ORDERS:
                            reference.stopSelf();
                            showNoOrders();
                            break;
                        default:
                            final JSONArray jsonArray = new JSONArray(stringBuilder.toString());

                            if (jsonArray.length() > 0) {
                                Log.d(TAG, "doInBackground: jsonArray: " + jsonArray.toString());

                                final JSONObject order = jsonArray.getJSONObject(0);

                                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(reference);
                                int last_id = sharedPreferences.getInt(reference.getString(R.string.key_last_order), reference.getResources().getInteger(R.integer.order_id_default));
                                Log.d(TAG, "doInBackground: last_id: " + last_id);

                                int productId = order.getInt("ID_Pedido");
                                Log.d(TAG, "doInBackground: orderID: " + productId);

                                if (last_id == productId) {
                                    Log.d(TAG, "doInBackground: last_id matches remote, stopping service.");
                                    reference.stopPolling();
                                } else {
                                    Log.d(TAG, "doInBackground: last_id doesn\'t match remote, populating layout...");

                                    if (!order.isNull("DNI_Cancelado")) {
                                        Log.d(TAG, "doInBackground: the order has been cancelled.");
                                        reference.sendPushNotification(0, reference.getString(R.string.requests_order_cancelled));

                                        reference.shouldKeepNotification = true;
                                        reference.stopPolling();
                                    } else if (order.isNull("FH_Tomado")) {
                                        reference.sendPushNotification(25, reference.getString(R.string.requests_order_received_short));
                                    } else if (order.isNull("FH_Listo")) {
                                        reference.sendPushNotification(50, reference.getString(R.string.requests_order_taken));
                                    } else if (order.isNull("FH_Entregado")) {
                                        reference.sendPushNotification(75, reference.getString(R.string.requests_order_ready));
                                    } else {
                                        reference.sendPushNotification(100, reference.getString(R.string.requests_order_delivered_short));

                                        reference.shouldKeepNotification = true;
                                        reference.stopPolling();
                                    }

                                    showDataFields();
                                }
                            } else {
                                showInternalError();
                            }

                    }
                } catch (Exception e) {
                    showInternalError();
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
