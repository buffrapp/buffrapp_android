package com.buffrapp;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
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

import com.balysv.materialripple.MaterialRippleLayout;
import com.google.android.material.navigation.NavigationView;
import com.takusemba.spotlight.Spotlight;
import com.takusemba.spotlight.shape.Circle;
import com.takusemba.spotlight.target.SimpleTarget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

import nl.dionsegijn.konfetti.KonfettiView;
import nl.dionsegijn.konfetti.models.Shape;
import nl.dionsegijn.konfetti.models.Size;
import util.ActivityNetworkWorker;

public class Requests extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "Requests";

    private boolean shouldHoldDeliveryView;
    private boolean shouldRunBackgroundWorkerOnStop;
    private boolean shouldTryToUpdate;
    private boolean firstDelivery;
    private boolean shouldDisplayConfetti;
    private boolean isCancelling;

    private static Timer timer;
    private static OrderStatusNetworkWorker orderStatusNetworkWorker;

    private EditText etReportContent;
    private AlertDialog dialog;

    private RelativeLayout rlReportAlert;
    private MaterialRippleLayout cancelButtonLayout;
    private ProgressBar progressBarCancel;

    private DrawerHandler drawerHandler;

    private DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    isCancelling = true;

                    if (isCancelling) {
                        cancelButtonLayout.setVisibility(View.GONE);
                        progressBarCancel.setVisibility(View.VISIBLE);
                    } else {
                        cancelButtonLayout.setVisibility(View.VISIBLE);
                        progressBarCancel.setVisibility(View.GONE);
                    }

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

        isCancelling = false;

        drawerHandler = new DrawerHandler(this);

        dialog = null;
        etReportContent = new EditText(this);

        Intent intent = new Intent(this, OrderStatusLooper.class);
        stopService(intent);

        final SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        ProgressBar progressBar = findViewById(R.id.requests_order_progress);
        Drawable drawable;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            drawable = DrawableCompat.wrap(progressBar.getProgressDrawable());
            DrawableCompat.setTint(drawable, ContextCompat.getColor(this, R.color.colorRed));
            progressBar.setProgressDrawable(DrawableCompat.unwrap(drawable));

            drawable = DrawableCompat.wrap(progressBar.getProgressDrawable());
            DrawableCompat.setTint(drawable, ContextCompat.getColor(this, R.color.colorPrimary));
            progressBarCancel.setProgressDrawable(DrawableCompat.unwrap(drawable));
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        final NavigationView navigationView = findViewById(R.id.nav_view);

        final TextView productNameTextView = findViewById(R.id.requests_order_product_name);

        cancelButtonLayout = findViewById(R.id.requests_order_cancel);
        progressBarCancel = findViewById(R.id.requests_order_progress_cancel);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_requests);
        navigationView.bringToFront();

        shouldHoldDeliveryView = false;

        orderStatusNetworkWorker = new OrderStatusNetworkWorker(Requests.this);

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

                        orderStatusNetworkWorker = new OrderStatusNetworkWorker(Requests.this);
                        orderStatusNetworkWorker.execute();
                    }
                });

        new OrderStatusNetworkWorker(Requests.this).execute();

        timer = new Timer();
        TimerTask updatingTask = new TimerTask() {
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (shouldTryToUpdate) {
                            new OrderStatusNetworkWorker(Requests.this).execute();
                        } else {
                            timer.cancel();
                            timer.purge();
                        }
                    }
                });
            }
        };
        timer.schedule(updatingTask, 0, getResources().getInteger(R.integer.update_interval));

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        firstDelivery = sharedPreferences.getBoolean(getString(R.string.key_first_delivery), true);
        shouldDisplayConfetti = true;
        shouldTryToUpdate = true;

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

        drawer.addDrawerListener(drawerHandler);

        cancelButtonLayout.setOnClickListener(new View.OnClickListener() {
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

        orderStatusNetworkWorker.cancel(true);

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

        TextView textViewNavInfo = findViewById(R.id.textViewNavInfo);
        textViewNavInfo.setText(PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.key_session_user_name), getString(R.string.unknown_user)));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_report:
                rlReportAlert = new RelativeLayout(Requests.this);
                RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT
                );
                rlReportAlert.setPadding(getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin),
                        0,
                        getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin),
                        0);

                TextWatcher afterTextChangedListener = new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        String reportContent = etReportContent.getText().toString();

                        if (reportContent.length() < 1) {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                            etReportContent.setError(getString(R.string.report_invalid_content));
                        } else {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        }
                    }
                };

                etReportContent.setHeight(RelativeLayout.LayoutParams.MATCH_PARENT);
                etReportContent.setHint(getString(R.string.report_hint));
                etReportContent.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                etReportContent.setGravity(Gravity.TOP);
                etReportContent.addTextChangedListener(afterTextChangedListener);

                rlReportAlert.addView(etReportContent, layoutParams);

                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(Requests.this)
                        .setTitle(getString(R.string.report_title))
                        .setView(rlReportAlert)
                        .setPositiveButton(getString(R.string.action_send), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                new ReportWorker(Requests.this, etReportContent.getText().toString()).execute();
                            }
                        })
                        .setNegativeButton(getString(R.string.action_cancel), null)
                        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                rlReportAlert.removeAllViews();
                            }
                        });

                dialog = dialogBuilder.create();
                dialog.show();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

                break;
            default:
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        drawerHandler.setNavCurrentId(item.getItemId());

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private static class OrderStatusNetworkWorker extends ActivityNetworkWorker {
        private static final String ORDER_ERROR = "1";
        private static final String ORDERS_NOT_ALLOWED = "2";
        private static final String ORDERS_NO_ORDERS = "3";
        private WeakReference<Requests> requestsActivity;

        OrderStatusNetworkWorker(Requests requestsActivity) {
            this.requestsActivity = new WeakReference<>(requestsActivity);

            setTargetActivity(requestsActivity);

            Requests reference = this.requestsActivity.get();

            if (reference != null) {
                setRequest(reference.getString(R.string.request_getUserOrders));
            }
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
                    MaterialRippleLayout cancelButtonLayout = reference.findViewById(R.id.requests_order_cancel);
                    ProgressBar progressBarCancel = reference.findViewById(R.id.requests_order_progress_cancel);

                    ImageView errorImageView = reference.findViewById(R.id.ic_error);
                    TextView errorTextView = reference.findViewById(R.id.tv_error);
                    TextView errorExtraTextView = reference.findViewById(R.id.tv_error_extra);

                    ImageView emptyImageView = reference.findViewById(R.id.ic_empty);
                    TextView emptyTextView = reference.findViewById(R.id.tv_empty);

                    requestsImageView.setVisibility(View.GONE);
                    productNameTextView.setVisibility(View.GONE);
                    statusTextView.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                    cancelButtonLayout.setVisibility(View.GONE);
                    progressBarCancel.setVisibility(View.GONE);

                    errorImageView.setVisibility(View.GONE);
                    errorTextView.setVisibility(View.GONE);
                    errorExtraTextView.setVisibility(View.GONE);

                    emptyImageView.setVisibility(View.VISIBLE);
                    emptyTextView.setVisibility(View.VISIBLE);

                    reference.shouldTryToUpdate = false;
                }
            });
        }

        private void showDataFields(final boolean isCancellable) {
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
                    MaterialRippleLayout cancelButtonLayout = reference.findViewById(R.id.requests_order_cancel);
                    ProgressBar progressBarCancel = reference.findViewById(R.id.requests_order_progress_cancel);

                    ImageView errorImageView = reference.findViewById(R.id.ic_error);
                    TextView errorTextView = reference.findViewById(R.id.tv_error);
                    TextView errorExtraTextView = reference.findViewById(R.id.tv_error_extra);

                    ImageView emptyImageView = reference.findViewById(R.id.ic_empty);
                    TextView emptyTextView = reference.findViewById(R.id.tv_empty);

                    requestsImageView.setVisibility(View.VISIBLE);
                    productNameTextView.setVisibility(View.VISIBLE);
                    statusTextView.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.VISIBLE);

                    if (reference.isCancelling) {
                        cancelButtonLayout.setVisibility(View.GONE);
                        progressBarCancel.setVisibility(View.VISIBLE);
                    } else {
                        if (isCancellable) {
                            cancelButtonLayout.setVisibility(View.VISIBLE);
                        } else {
                            cancelButtonLayout.setVisibility(View.GONE);
                        }
                        progressBarCancel.setVisibility(View.GONE);
                    }

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

                reference.shouldHoldDeliveryView = true;
            }
        }

        @Override
        protected void handleOutput(String serverOutput) {
            Log.d(TAG, "doInBackground: an order has been found.");
            final Requests reference = requestsActivity.get();

            if (requestsActivity == null) {
                Log.d(TAG, "doInBackground: showNoOrders: failed to get a reference.");
                return;
            }

            switch (serverOutput) {
                case ORDER_ERROR:
                    showInternalError(reference.getString(R.string.internal_error), reference);
                    break;
                case ORDERS_NOT_ALLOWED:
                    showInternalError(reference.getString(R.string.not_allowed_error), reference);
                    break;
                case ORDERS_NO_ORDERS:
                    showNoOrders();
                    break;
                default:
                    try {
                        final JSONArray jsonArray = new JSONArray(serverOutput);

                        if (jsonArray.length() > 0) {
                            Log.d(TAG, "doInBackground: jsonArray: " + jsonArray.toString());

                            final JSONObject order = jsonArray.getJSONObject(0);

                            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(reference);
                            int last_id = sharedPreferences.getInt(reference.getString(R.string.key_last_order), reference.getResources().getInteger(R.integer.order_id_default));
                            Log.d(TAG, "doInBackground: last_id: " + last_id);

                            if (last_id == order.getInt("ID_Pedido") && !reference.shouldHoldDeliveryView) {
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
                                boolean isCancellable = false;
                                final MaterialRippleLayout cancelButtonLayout = reference.findViewById(R.id.requests_order_cancel);

                                reference.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        cancelButtonLayout.setVisibility(View.GONE);
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
                                        }
                                    });

                                    isCancellable = true;
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

                                reference.shouldRunBackgroundWorkerOnStop = true;
                                showDataFields(isCancellable);
                            }
                        } else {
                            showInternalError(reference.getString(R.string.internal_error), reference);
                        }
                    } catch (JSONException jsonException) {
                        jsonException.printStackTrace();
                        showInternalError(reference.getString(R.string.internal_error), reference);
                    }

            }
        }

        @Override
        protected void showInternalError(final String message, final Activity reference) {
            Log.d(TAG, "doInBackground: an internal error has occurred.");

            reference.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ImageView requestsImageView = reference.findViewById(R.id.ic_requests);
                    TextView productNameTextView = reference.findViewById(R.id.requests_order_product_name);
                    TextView statusTextView = reference.findViewById(R.id.requests_order_status);
                    ProgressBar progressBar = reference.findViewById(R.id.requests_order_progress);
                    MaterialRippleLayout cancelButtonLayout = reference.findViewById(R.id.requests_order_cancel);

                    ImageView errorImageView = reference.findViewById(R.id.ic_error);
                    TextView errorTextView = reference.findViewById(R.id.tv_error);
                    TextView errorExtraTextView = reference.findViewById(R.id.tv_error_extra);

                    ImageView emptyImageView = reference.findViewById(R.id.ic_empty);
                    TextView emptyTextView = reference.findViewById(R.id.tv_empty);

                    requestsImageView.setVisibility(View.GONE);
                    productNameTextView.setVisibility(View.GONE);
                    statusTextView.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                    cancelButtonLayout.setVisibility(View.GONE);

                    errorImageView.setVisibility(View.VISIBLE);
                    errorTextView.setVisibility(View.VISIBLE);
                    errorExtraTextView.setText(message);
                    errorExtraTextView.setVisibility(View.VISIBLE);

                    emptyImageView.setVisibility(View.GONE);
                    emptyTextView.setVisibility(View.GONE);
                }
            });
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

    private static class OrderCancelNetworkWorker extends ActivityNetworkWorker {
        private static final String CANCEL_PASS = "0";
        private static final String CANCEL_ERROR = "1";
        private static final String CANCEL_NOT_ALLOWED = "2";
        private WeakReference<Requests> requestsActivity;

        OrderCancelNetworkWorker(Requests requestsActivity) {
            this.requestsActivity = new WeakReference<>(requestsActivity);

            setTargetActivity(requestsActivity);

            Requests reference = this.requestsActivity.get();

            if (reference != null) {
                setRequest(reference.getString(R.string.request_cancelOrder));
            }
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
                    MaterialRippleLayout cancelButtonLayout = reference.findViewById(R.id.requests_order_cancel);

                    ImageView errorImageView = reference.findViewById(R.id.ic_error);
                    TextView errorTextView = reference.findViewById(R.id.tv_error);

                    ImageView emptyImageView = reference.findViewById(R.id.ic_empty);
                    TextView emptyTextView = reference.findViewById(R.id.tv_empty);

                    requestsImageView.setVisibility(View.GONE);
                    productNameTextView.setVisibility(View.GONE);
                    statusTextView.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                    cancelButtonLayout.setVisibility(View.GONE);

                    errorImageView.setVisibility(View.GONE);
                    errorTextView.setVisibility(View.GONE);

                    emptyImageView.setVisibility(View.VISIBLE);
                    emptyTextView.setVisibility(View.VISIBLE);

                    reference.shouldTryToUpdate = false;
                }
            });
        }

        @Override
        protected void handleOutput(String serverOutput) {
            final Requests reference = requestsActivity.get();

            if (reference != null) {
                switch (serverOutput) {
                    case CANCEL_ERROR:
                        showInternalError(reference.getString(R.string.requests_order_cancel_failed), reference);
                        break;
                    case CANCEL_NOT_ALLOWED:
                        showInternalError(reference.getString(R.string.not_allowed_error), reference);
                        break;
                    case CANCEL_PASS:
                        showNoOrders();
                        reference.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(reference, reference.getString(R.string.requests_order_cancel_success), Toast.LENGTH_LONG).show();
                            }
                        });
                }

                if (reference.isCancelling) {
                    reference.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            reference.cancelButtonLayout.setVisibility(View.GONE);
                            reference.progressBarCancel.setVisibility(View.VISIBLE);
                        }
                    });
                } else {
                    reference.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            reference.cancelButtonLayout.setVisibility(View.VISIBLE);
                            reference.progressBarCancel.setVisibility(View.GONE);
                        }
                    });
                }
            }
        }

        @Override
        protected void showInternalError(final String message, final Activity reference) {
            Log.d(TAG, "doInBackground: an internal error has occurred.");

            reference.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(reference, message, Toast.LENGTH_LONG).show();
                }
            });
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
