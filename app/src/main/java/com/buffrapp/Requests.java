package com.buffrapp;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.SSLCertificateSocketFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.buffrapp.ui.login.LoginActivity;
import com.google.android.material.navigation.NavigationView;
import com.takusemba.spotlight.Spotlight;
import com.takusemba.spotlight.shape.Circle;
import com.takusemba.spotlight.target.SimpleTarget;

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

import nl.dionsegijn.konfetti.KonfettiView;
import nl.dionsegijn.konfetti.models.Shape;
import nl.dionsegijn.konfetti.models.Size;

public class Requests extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "Requests";

    private static boolean shouldHoldDeliveryView;
    private static boolean shouldRunBackgroundWorkerOnStop;
    private boolean firstDelivery;
    private boolean shouldDisplayConfetti;

    private static Timer timer;
    private static NetworkWorker networkWorker;

    private int navCurrentId = -1;

    private DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    OrderCancelNetworkWorker orderCancelNetworkWorker = new OrderCancelNetworkWorker(Requests.this);
                    orderCancelNetworkWorker.execute();
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    break;
            }
        }
    };

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

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        final NavigationView navigationView = findViewById(R.id.nav_view);

        final TextView productNameTextView = findViewById(R.id.requests_order_product_name);
        Button cancelButton = findViewById(R.id.requests_order_cancel);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_requests);
        navigationView.bringToFront();

        shouldHoldDeliveryView = false;

        networkWorker = new NetworkWorker(Requests.this);

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

                        networkWorker = new NetworkWorker(Requests.this);
                        networkWorker.execute();
                    }
                });

        new NetworkWorker(Requests.this).execute();

        timer = new Timer();
        TimerTask updatingTask = new TimerTask() {
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        swipeRefreshLayout.setRefreshing(true);
                        new NetworkWorker(Requests.this).execute();
                    }
                });
            }
        };
        timer.schedule(updatingTask, 0, getResources().getInteger(R.integer.update_interval));

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        firstDelivery = sharedPreferences.getBoolean(getString(R.string.key_first_delivery), true);
        shouldDisplayConfetti = true;

        if (sharedPreferences.getBoolean(getString(R.string.key_first_run_requests), true)) {
            getWindow().getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    float x;
                    float y;

                    x = (float) navigationView.getWidth() / 2 + 90f;
                    y = (float) navigationView.getHeight() / 2 - 50f;

                    SimpleTarget middleOfViewTarget = new SimpleTarget.Builder(Requests.this)
                            .setPoint(x, y)
                            .setShape(new Circle(350f))
                            .setTitle(getString(R.string.requests_order_status))
                            .setDescription(getString(R.string.requests_order_status_description))
                            .setOverlayPoint(x - 325f, y + 400f)
                            .build();

                    Spotlight spotlight = Spotlight.with(Requests.this)
                            .setOverlayColor(R.color.background)
                            .setDuration(100L)
                            .setAnimation(new AccelerateDecelerateInterpolator())
                            .setTargets(middleOfViewTarget)
                            .setClosedOnTouchedOutside(true);

                    spotlight.start();
                }
            });

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(getString(R.string.key_first_run_requests), false);
            editor.apply();
        }

        drawer.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
            }

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
            }

            @Override
            public void onDrawerStateChanged(int newState) {
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                if (navCurrentId != R.id.nav_requests) {
                    // Handle navigation view item clicks here.
                    if (navCurrentId < 0) {
                        Log.d(TAG, "onDrawerClosed: no item selected, skipping action...");
                    } else {
                        if (navCurrentId == R.id.nav_products) {
                            Intent intent = new Intent(Requests.this, Products.class);
                            startActivity(intent);
                        } else if (navCurrentId == R.id.nav_history) {
                            Intent intent = new Intent(Requests.this, History.class);
                            startActivity(intent);
                        } else if (navCurrentId == R.id.nav_schedule) {

                        } else if (navCurrentId == R.id.nav_profile) {
                            Intent intent = new Intent(Requests.this, Profile.class);
                            startActivity(intent);
                        } else if (navCurrentId == R.id.nav_logout) {
                            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Requests.this);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.remove(getString(R.string.key_session_id));
                            editor.apply();

                            Intent intent = new Intent(Requests.this, LoginActivity.class);
                            startActivity(intent);
                        }

                        Log.d(TAG, "onDrawerClosed: selected ID is " + navCurrentId);

                        finish();
                    }
                }
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(Requests.this);

                builder.setMessage(String.format(getString(R.string.requests_order_cancel_confirmation), productNameTextView.getText()))
                        .setPositiveButton(getString(R.string.dialog_yes), dialogClickListener)
                        .setNegativeButton(getString(R.string.dialog_no), dialogClickListener)
                        .show();
            }
        });
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
        navCurrentId = item.getItemId();
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
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

        private void showInternalError(final String message) {
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
                    Button cancelButton = reference.findViewById(R.id.requests_order_cancel);

                    ImageView errorImageView = reference.findViewById(R.id.ic_error);
                    TextView errorTextView = reference.findViewById(R.id.tv_error);
                    TextView errorExtraTextView = reference.findViewById(R.id.tv_error_extra);

                    ImageView emptyImageView = reference.findViewById(R.id.ic_empty);
                    TextView emptyTextView = reference.findViewById(R.id.tv_empty);

                    requestsImageView.setVisibility(View.GONE);
                    productNameTextView.setVisibility(View.GONE);
                    statusTextView.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                    cancelButton.setVisibility(View.GONE);

                    errorImageView.setVisibility(View.VISIBLE);
                    errorTextView.setVisibility(View.VISIBLE);
                    errorExtraTextView.setText(message);
                    errorExtraTextView.setVisibility(View.VISIBLE);

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
                    Button cancelButton = reference.findViewById(R.id.requests_order_cancel);

                    ImageView errorImageView = reference.findViewById(R.id.ic_error);
                    TextView errorTextView = reference.findViewById(R.id.tv_error);
                    TextView errorExtraTextView = reference.findViewById(R.id.tv_error_extra);

                    ImageView emptyImageView = reference.findViewById(R.id.ic_empty);
                    TextView emptyTextView = reference.findViewById(R.id.tv_empty);

                    requestsImageView.setVisibility(View.GONE);
                    productNameTextView.setVisibility(View.GONE);
                    statusTextView.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                    cancelButton.setVisibility(View.GONE);

                    errorImageView.setVisibility(View.GONE);
                    errorTextView.setVisibility(View.GONE);
                    errorExtraTextView.setVisibility(View.GONE);

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
                    TextView errorExtraTextView = reference.findViewById(R.id.tv_error_extra);

                    ImageView emptyImageView = reference.findViewById(R.id.ic_empty);
                    TextView emptyTextView = reference.findViewById(R.id.tv_empty);

                    requestsImageView.setVisibility(View.VISIBLE);
                    productNameTextView.setVisibility(View.VISIBLE);
                    statusTextView.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.VISIBLE);

                    errorImageView.setVisibility(View.GONE);
                    errorTextView.setVisibility(View.GONE);
                    errorExtraTextView.setVisibility(View.GONE);

                    emptyImageView.setVisibility(View.GONE);
                    emptyTextView.setVisibility(View.GONE);
                }
            });
        }

        private void setProgressBarStatus(final int progress, final int color) {
            Log.d(TAG, "doInBackground: an order has been found.");
            final Requests reference = requestsActivity.get();

            if (requestsActivity == null) {
                Log.d(TAG, "doInBackground: showNoOrders: failed to get a reference.");
                return;
            }

            reference.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ProgressBar progressBar = reference.findViewById(R.id.requests_order_progress);

                    Drawable drawable = DrawableCompat.wrap(progressBar.getProgressDrawable());
                    DrawableCompat.setTint(drawable, ContextCompat.getColor(reference, color));
                    progressBar.setProgressDrawable(DrawableCompat.unwrap(drawable));
                    progressBar.setProgress(progress);
                }
            });
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
                            showInternalError(reference.getString(R.string.internal_error));
                            break;
                        case ORDERS_NOT_ALLOWED:
                            showInternalError(reference.getString(R.string.not_allowed_error));
                            break;
                        case ORDERS_NO_ORDERS:
                            showNoOrders();
                            break;
                        default:
                            final JSONArray jsonArray = new JSONArray(stringBuilder.toString());

                            if (jsonArray.length() > 0) {
                                Log.d(TAG, "doInBackground: jsonArray: " + jsonArray.toString());

                                final JSONObject order = jsonArray.getJSONObject(0);

                                final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(reference);
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

                                    // On most cases it should be gone, so this will be the default behavior.
                                    final Button cancelButton = reference.findViewById(R.id.requests_order_cancel);

                                    reference.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            cancelButton.setVisibility(View.GONE);
                                        }
                                    });

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
                                                cancelButton.setVisibility(View.VISIBLE);
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
                                                if (reference.firstDelivery) {
                                                    productStatusTextView.setText(reference.getString(R.string.requests_order_delivered_first));

                                                    if (reference.shouldDisplayConfetti) {
                                                        final KonfettiView konfettiView = reference.findViewById(R.id.confetti_view);
                                                        konfettiView.post(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                konfettiView.build()
                                                                        .addColors(Color.YELLOW, Color.GREEN, Color.RED, Color.BLUE, Color.MAGENTA)
                                                                        .setDirection(0, 360)
                                                                        .setSpeed(3f, 6f)
                                                                        .setFadeOutEnabled(true)
                                                                        .setTimeToLive(2000L)
                                                                        .addShapes(Shape.RECT, Shape.CIRCLE)
                                                                        .addSizes(new Size(12, 6f), new Size(16, 3f))
                                                                        .setPosition(-50f, (float) konfettiView.getWidth() + 50f, 0, konfettiView.getHeight() + 50f)
                                                                        .burst(250);
                                                            }
                                                        });

                                                        reference.shouldDisplayConfetti = false;
                                                    }

                                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                                    editor.putBoolean(reference.getString(R.string.key_first_delivery), false);
                                                    editor.apply();
                                                } else {
                                                    productStatusTextView.setText(reference.getString(R.string.requests_order_delivered));
                                                }

                                                updateLastOrderId(order);
                                            }
                                        });
                                    }

                                    shouldRunBackgroundWorkerOnStop = true;
                                    showDataFields();
                                }
                            } else {
                                showInternalError(reference.getString(R.string.internal_error));
                            }

                    }

                    Log.d(TAG, "doInBackground: stringBuilder: " + stringBuilder.toString());
                } catch (Exception e) {
                    showInternalError(String.format(reference.getString(R.string.products_error_server_failure), reference.getString(R.string.server_hostname)));
                    e.printStackTrace();
                } finally {
                    if (httpsURLConnection != null) {
                        httpsURLConnection.disconnect();
                    }
                }
            } catch (final MalformedURLException e) {
                showInternalError(reference.getString(R.string.products_error_malformed_url));
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

    private static class OrderCancelNetworkWorker extends AsyncTask<Void, Void, Void> {
        private static final String CANCEL_PASS = "0";
        private static final String CANCEL_ERROR = "1";
        private static final String CANCEL_NOT_ALLOWED = "2";
        private WeakReference<Requests> requestsActivity;

        OrderCancelNetworkWorker(Requests requestsActivity) {
            this.requestsActivity = new WeakReference<>(requestsActivity);
        }

        private void showInternalError(final String message) {
            Log.d(TAG, "doInBackground: an internal error has occurred.");
            final Requests reference = requestsActivity.get();

            if (requestsActivity == null) {
                Log.d(TAG, "doInBackground: showInternalError: failed to get a reference.");
                return;
            }

            reference.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(reference, message, Toast.LENGTH_LONG).show();
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
                    Button cancelButton = reference.findViewById(R.id.requests_order_cancel);

                    ImageView errorImageView = reference.findViewById(R.id.ic_error);
                    TextView errorTextView = reference.findViewById(R.id.tv_error);

                    ImageView emptyImageView = reference.findViewById(R.id.ic_empty);
                    TextView emptyTextView = reference.findViewById(R.id.tv_empty);

                    requestsImageView.setVisibility(View.GONE);
                    productNameTextView.setVisibility(View.GONE);
                    statusTextView.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                    cancelButton.setVisibility(View.GONE);

                    errorImageView.setVisibility(View.GONE);
                    errorTextView.setVisibility(View.GONE);

                    emptyImageView.setVisibility(View.VISIBLE);
                    emptyTextView.setVisibility(View.VISIBLE);
                }
            });
        }

        @Override
        protected Void doInBackground(Void... voids) {
            final Requests reference = requestsActivity.get();

            if (requestsActivity == null) {
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
                            .appendQueryParameter(reference.getString(R.string.server_request_param), reference.getString(R.string.request_cancelOrder));

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
                        case CANCEL_ERROR:
                            showInternalError(reference.getString(R.string.requests_order_cancel_failed));
                            break;
                        case CANCEL_NOT_ALLOWED:
                            showInternalError(reference.getString(R.string.not_allowed_error));
                            break;
                        default:
                            showNoOrders();
                            reference.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(reference, reference.getString(R.string.requests_order_cancel_success), Toast.LENGTH_LONG).show();
                                }
                            });
                    }

                    Log.d(TAG, "doInBackground: stringBuilder: " + stringBuilder.toString());
                } catch (Exception e) {
                    showInternalError(String.format(reference.getString(R.string.products_error_server_failure), reference.getString(R.string.server_hostname)));
                    e.printStackTrace();
                } finally {
                    if (httpsURLConnection != null) {
                        httpsURLConnection.disconnect();
                    }
                }
            } catch (final MalformedURLException e) {
                showInternalError(reference.getString(R.string.products_error_malformed_url));
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
