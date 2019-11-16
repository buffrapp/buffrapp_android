package com.buffrapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

import util.ServiceNetworkWorker;

public class OrderStatusLooper extends Service {

    private static final String TAG = "OrderStatusLooper";

    private boolean shouldKeepNotification;
    private Timer timer;
    private OrderStatusActivityNetworkWorker orderStatusNetworkWorker;

    private String previousStatus;

    public OrderStatusLooper() {
    } // I'm not implementing this, seriously.

    @Override
    public void onCreate() {
        super.onCreate();

        shouldKeepNotification = false;

        timer = new Timer();
        TimerTask updatingTask = new TimerTask() {
            public void run() {
                orderStatusNetworkWorker = new OrderStatusActivityNetworkWorker(OrderStatusLooper.this);
                orderStatusNetworkWorker.execute();
            }
        };
        timer.schedule(updatingTask, 0, getResources().getInteger(R.integer.update_interval));
    }

    @Override
    public void onDestroy() {
        timer.cancel();
        timer.purge();
        orderStatusNetworkWorker.cancel(true);
        removePushNotification();

        super.onDestroy();
    }

    private void stopPolling() {
        timer.cancel();
        timer.purge();
        orderStatusNetworkWorker.cancel(true);
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
        if (!status.equals(previousStatus)) {
            Log.d(TAG, "doInBackground: sending push notification...");

            final int PROGRESS_MAX = 100;

            String channel_name = getString(R.string.notifications_orders_channel_name);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String description = getString(R.string.notifications_orders_channel_description);
                int importance = NotificationManager.IMPORTANCE_DEFAULT;
                NotificationChannel channel = new NotificationChannel(channel_name, channel_name, importance);
                channel.setDescription(description);
                NotificationManager notificationManager = getSystemService(NotificationManager.class);
                notificationManager.createNotificationChannel(channel);
                channel.enableVibration(true);
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
                    .setVibrate(new long[]{500, 500, 500, 5000, 15000})
                    .setOngoing(true);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.notify(getResources().getInteger(R.integer.orders_notification_id), notificationBuilder.build());

            previousStatus = status;
        } else {
            Log.d(TAG, "sendPushNotification: aborted request, duplicated status detected.");
        }
    }

    private void removePushNotification() {
        final int id = getResources().getInteger(R.integer.orders_notification_id);

        Log.d(TAG, "doInBackground: removing push notification with ID " + id + "...");

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancel(id);
    }

    protected static void removePushNotification(Context context) {
        final int id = context.getResources().getInteger(R.integer.orders_notification_id);

        Log.d(TAG, "doInBackground: removing push notification with ID " + id + "...");

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancel(id);
    }

    private static class OrderStatusActivityNetworkWorker extends ServiceNetworkWorker {
        private static final String ORDER_ERROR = "1";
        private static final String ORDERS_NOT_ALLOWED = "2";
        private static final String ORDERS_NO_ORDERS = "3";
        private WeakReference<OrderStatusLooper> orderStatusLooperSvc;

        OrderStatusActivityNetworkWorker(OrderStatusLooper orderStatusLooperSvc) {
            this.orderStatusLooperSvc = new WeakReference<>(orderStatusLooperSvc);

            setTargetService(orderStatusLooperSvc);


            OrderStatusLooper reference = this.orderStatusLooperSvc.get();

            if (reference != null) {
                setRequest(reference.getString(R.string.request_getUserOrders));
            }
        }

        private void showNoOrders() {
            Log.d(TAG, "doInBackground: no ongoing orders found.");

        }

        private void showDataFields() {
            Log.d(TAG, "doInBackground: an order has been found.");

        }

        @Override
        public void handleOutput(String serverOutput) {
            final OrderStatusLooper reference = orderStatusLooperSvc.get();

            if (orderStatusLooperSvc == null) {
                Log.d(TAG, "doInBackground: failed to get a reference.");
                return;
            }

            switch (serverOutput) {
                case ORDER_ERROR:
                    showInternalError(null, reference);
                    break;
                case ORDERS_NOT_ALLOWED:
                    showInternalError(null, reference);
                    break;
                case ORDERS_NO_ORDERS:
                    reference.stopSelf();
                    showNoOrders();
                    break;
                default:
                    try {
                        final JSONArray jsonArray = new JSONArray(serverOutput);

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
                            showInternalError(null, reference);
                        }
                    } catch (JSONException jsonException) {
                        jsonException.printStackTrace();
                        showInternalError(null, reference);
                    }
            }
        }

        @Override
        public void showInternalError(String message, Service reference) {
            Log.d(TAG, "doInBackground: an internal error has occurred.");
        }
    }
}
