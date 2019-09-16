package com.buffrapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.SSLCertificateSocketFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.buffrapp.ui.login.LoginActivity;

import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.json.JSONArray;
import org.json.JSONException;
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

public class Requests extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "Requests";

    private static boolean shouldHoldDeliveryView;
    private static boolean shouldRunBackgroundWorkerOnStop;

    private static Timer timer;
    private static NetworkWorker networkWorker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_requests);

        Intent intent = new Intent(this, OrderStatusLooper.class);
        stopService(intent);

        final SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        ProgressBar progressBar = findViewById(R.id.requests_order_progress);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Drawable drawable = DrawableCompat.wrap(progressBar.getProgressDrawable());
            DrawableCompat.setTint(drawable, ContextCompat.getColor(this, R.color.colorRed));
            progressBar.setProgressDrawable(DrawableCompat.unwrap(drawable));
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_requests);
        navigationView.bringToFront();

        shouldHoldDeliveryView = false;

        networkWorker = new Requests.NetworkWorker(Requests.this);

        swipeRefreshLayout.setRefreshing(true);
        swipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorAccent));
        swipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {

                    @Override
                    public void onRefresh() {
                        Log.d(TAG, "onRefresh: refreshing data...");

                        ImageView requestsImageView = findViewById(R.id.ic_requests);
                        TextView productNameTextView = findViewById(R.id.requests_order_product_name);
                        TextView statusTextView = findViewById(R.id.requests_order_status);
                        ProgressBar progressBar = findViewById(R.id.requests_order_progress);

                        ImageView errorImageView = findViewById(R.id.ic_error);
                        TextView errorTextView = findViewById(R.id.tv_error);

                        ImageView emptyImageView = findViewById(R.id.ic_empty);
                        TextView emptyTextView = findViewById(R.id.tv_empty);

                        requestsImageView.setVisibility(View.GONE);
                        productNameTextView.setVisibility(View.GONE);
                        statusTextView.setVisibility(View.GONE);
                        progressBar.setVisibility(View.GONE);

                        errorImageView.setVisibility(View.GONE);
                        errorTextView.setVisibility(View.GONE);

                        emptyImageView.setVisibility(View.GONE);
                        emptyTextView.setVisibility(View.GONE);

                        networkWorker = new Requests.NetworkWorker(Requests.this);
                        networkWorker.execute();
                    }
                });

        new Requests.NetworkWorker(Requests.this).execute();

        timer = new Timer();
        TimerTask updatingTask = new TimerTask() {
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        swipeRefreshLayout.setRefreshing(true);
                        new Requests.NetworkWorker(Requests.this).execute();
                    }
                });
            }
        };
        timer.schedule(updatingTask, 0, getResources().getInteger(R.integer.update_interval));
    }

    @Override
    protected void onUserLeaveHint() {
        timer.cancel();
        timer.purge();

        networkWorker.cancel(true);

        if (shouldRunBackgroundWorkerOnStop) {
            Intent intent = new Intent(this, OrderStatusLooper.class);
            startService(intent);
        }

        super.onUserLeaveHint();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_products) {
            Intent intent = new Intent(this, Products.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        } else if (id == R.id.nav_requests) {
            Intent intent = new Intent(this, Requests.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        } else if (id == R.id.nav_history) {
            Intent intent = new Intent(this, History.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        } else if (id == R.id.nav_schedule) {

        } else if (id == R.id.nav_profile) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        } else if (id == R.id.nav_logout) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(getString(R.string.key_session_id));
            editor.apply();

            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);

        Log.d(TAG, "onNavigationItemSelected: selected ID is " + id);
        return true;
    }

    private static class NetworkWorker extends AsyncTask<Void, Void, Void> {
        private static final String ORDER_ERROR = "1";
        private static final String ORDERS_NOT_ALLOWED = "2";
        private static final String ORDERS_NO_ORDERS = "3";
        private WeakReference<Requests> requestsActivity;

        NetworkWorker(Requests requestsActivity) {
            this.requestsActivity = new WeakReference<>(requestsActivity);
        }

        private void showInternalError() {
            Log.d(TAG, "doInBackground: an internal error has occurred.");
            final Requests reference = requestsActivity.get();

            if (requestsActivity == null) {
                Log.d(TAG, "doInBackground: showInternalError: failed to get a reference.");
                return;
            }

            reference.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ImageView requestsImageView = reference.findViewById(R.id.ic_requests);
                    TextView productNameTextView = reference.findViewById(R.id.requests_order_product_name);
                    TextView statusTextView = reference.findViewById(R.id.requests_order_status);
                    ProgressBar progressBar = reference.findViewById(R.id.requests_order_progress);

                    ImageView errorImageView = reference.findViewById(R.id.ic_error);
                    TextView errorTextView = reference.findViewById(R.id.tv_error);

                    ImageView emptyImageView = reference.findViewById(R.id.ic_empty);
                    TextView emptyTextView = reference.findViewById(R.id.tv_empty);

                    requestsImageView.setVisibility(View.GONE);
                    productNameTextView.setVisibility(View.GONE);
                    statusTextView.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);

                    errorImageView.setVisibility(View.VISIBLE);
                    errorTextView.setVisibility(View.VISIBLE);

                    emptyImageView.setVisibility(View.GONE);
                    emptyTextView.setVisibility(View.GONE);
                }
            });
        }

        private void showNoOrders() {
            Log.d(TAG, "doInBackground: no ongoing orders found.");
            final Requests reference = requestsActivity.get();

            if (requestsActivity == null) {
                Log.d(TAG, "doInBackground: showNoOrders: failed to get a reference.");
                return;
            }

            reference.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ImageView requestsImageView = reference.findViewById(R.id.ic_requests);
                    TextView productNameTextView = reference.findViewById(R.id.requests_order_product_name);
                    TextView statusTextView = reference.findViewById(R.id.requests_order_status);
                    ProgressBar progressBar = reference.findViewById(R.id.requests_order_progress);

                    ImageView errorImageView = reference.findViewById(R.id.ic_error);
                    TextView errorTextView = reference.findViewById(R.id.tv_error);

                    ImageView emptyImageView = reference.findViewById(R.id.ic_empty);
                    TextView emptyTextView = reference.findViewById(R.id.tv_empty);

                    requestsImageView.setVisibility(View.GONE);
                    productNameTextView.setVisibility(View.GONE);
                    statusTextView.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);

                    errorImageView.setVisibility(View.GONE);
                    errorTextView.setVisibility(View.GONE);

                    emptyImageView.setVisibility(View.VISIBLE);
                    emptyTextView.setVisibility(View.VISIBLE);
                }
            });
        }

        private void showDataFields() {
            Log.d(TAG, "doInBackground: an order has been found.");
            final Requests reference = requestsActivity.get();

            if (requestsActivity == null) {
                Log.d(TAG, "doInBackground: showNoOrders: failed to get a reference.");
                return;
            }

            reference.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ImageView requestsImageView = reference.findViewById(R.id.ic_requests);
                    TextView productNameTextView = reference.findViewById(R.id.requests_order_product_name);
                    TextView statusTextView = reference.findViewById(R.id.requests_order_status);
                    ProgressBar progressBar = reference.findViewById(R.id.requests_order_progress);

                    ImageView errorImageView = reference.findViewById(R.id.ic_error);
                    TextView errorTextView = reference.findViewById(R.id.tv_error);

                    ImageView emptyImageView = reference.findViewById(R.id.ic_empty);
                    TextView emptyTextView = reference.findViewById(R.id.tv_empty);

                    requestsImageView.setVisibility(View.VISIBLE);
                    productNameTextView.setVisibility(View.VISIBLE);
                    statusTextView.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.VISIBLE);

                    errorImageView.setVisibility(View.GONE);
                    errorTextView.setVisibility(View.GONE);

                    emptyImageView.setVisibility(View.GONE);
                    emptyTextView.setVisibility(View.GONE);
                }
            });
        }

        private void setProgressBarStatus(int progress, int color) {
            Log.d(TAG, "doInBackground: an order has been found.");
            final Requests reference = requestsActivity.get();

            if (requestsActivity == null) {
                Log.d(TAG, "doInBackground: showNoOrders: failed to get a reference.");
                return;
            }

            ProgressBar progressBar = reference.findViewById(R.id.requests_order_progress);

            Drawable drawable = DrawableCompat.wrap(progressBar.getProgressDrawable());
            DrawableCompat.setTint(drawable, ContextCompat.getColor(reference, color));
            progressBar.setProgressDrawable(DrawableCompat.unwrap(drawable));
            progressBar.setProgress(progress);
        }

        private void updateLastOrderId(JSONObject order) {
            Log.d(TAG, "doInBackground: an order has been found.");
            final Requests reference = requestsActivity.get();

            if (requestsActivity == null) {
                Log.d(TAG, "doInBackground: showNoOrders: failed to get a reference.");
                return;
            }

            int orderID = reference.getResources().getInteger(R.integer.order_id_default);

            try {
                orderID = order.getInt("ID_Pedido");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            if (orderID > -1) {
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(reference);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(reference.getString(R.string.key_last_order), orderID);
                editor.apply();

                shouldHoldDeliveryView = true;
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            final Requests reference = requestsActivity.get();

            if (requestsActivity == null) {
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

                                if (last_id == order.getInt("ID_Pedido") && !shouldHoldDeliveryView) {
                                    Log.d(TAG, "doInBackground: last_id matches remote, showing no orders layout.");
                                    showNoOrders();
                                } else {
                                    Log.d(TAG, "doInBackground: last_id doesn\'t match remote, populating layout...");

                                    reference.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            String productName = null;

                                            try {
                                                productName = order.getString("Nombre_Producto");
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }

                                            if (productName != null) {
                                                TextView productNameTextView = reference.findViewById(R.id.requests_order_product_name);
                                                productNameTextView.setText(productName);
                                            }
                                        }
                                    });

                                    final TextView productStatusTextView = reference.findViewById(R.id.requests_order_status);

                                    if (!order.isNull("DNI_Cancelado")) {
                                        Log.d(TAG, "doInBackground: the order has been cancelled.");
                                        setProgressBarStatus(0, R.color.colorRed);
                                        reference.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                productStatusTextView.setText(reference.getString(R.string.requests_order_cancelled));

                                                updateLastOrderId(order);
                                            }
                                        });
                                    } else if (order.isNull("FH_Tomado")) {
                                        setProgressBarStatus(25, R.color.colorRed);
                                        reference.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                productStatusTextView.setText(reference.getString(R.string.requests_order_received));
                                            }
                                        });
                                    } else if (order.isNull("FH_Listo")) {
                                        setProgressBarStatus(50, R.color.colorOngoing);
                                        reference.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                productStatusTextView.setText(reference.getString(R.string.requests_order_taken));
                                            }
                                        });
                                    } else if (order.isNull("FH_Entregado")) {
                                        setProgressBarStatus(75, R.color.colorAccent);
                                        reference.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                productStatusTextView.setText(reference.getString(R.string.requests_order_ready));
                                            }
                                        });
                                    } else {
                                        setProgressBarStatus(100, R.color.colorAccentDark);
                                        reference.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                productStatusTextView.setText(reference.getString(R.string.requests_order_delivered));

                                                updateLastOrderId(order);
                                            }
                                        });
                                    }

                                    shouldRunBackgroundWorkerOnStop = true;
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

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            Requests reference = requestsActivity.get();

            if (requestsActivity != null) {
                SwipeRefreshLayout swipeRefreshLayout = reference.findViewById(R.id.swipeRefreshLayout);
                swipeRefreshLayout.setRefreshing(false);
            }
        }
    }
}
