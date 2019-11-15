package com.buffrapp;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.SSLCertificateSocketFactory;
import android.net.Uri;
import android.os.AsyncTask;
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
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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

import javax.net.ssl.HttpsURLConnection;

public class Products extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, ProductsAdapter.ItemClickListener {

    private static final String TAG = "Products";

    private static final int READ_PERMISSION_ID = 16384;

    private static final Character SYMBOL_AMPERSAND = '&';
    private static final Character SYMBOL_EQUALS = '=';
    private static final Character SYMBOL_BRACKET_OPEN = '[';
    private static final Character SYMBOL_BRACKET_CLOSED = ']';

    private ProductsAdapter productsAdapter;
    private RecyclerView recyclerView;

    private EditText etReportContent;
    private AlertDialog dialog;

    private RelativeLayout rlReportAlert;

    private DrawerHandler drawerHandler;

    private int productId = -1;
    private DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            Log.d(TAG, "onClick: productId: " + productId);
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    if (productId > -1) {
                        OrderRequestNetworkWorker orderRequestNetworkWorker = new OrderRequestNetworkWorker(Products.this);
                        orderRequestNetworkWorker.setProductId(productId);
                        orderRequestNetworkWorker.execute();
                    }
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_products);

        drawerHandler = new DrawerHandler(this);

        Intent orderStatusLooperIntent = new Intent(this, OrderStatusLooper.class);
        stopService(orderStatusLooperIntent);

        try {
            startService(orderStatusLooperIntent);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }

        dialog = null;
        etReportContent = new EditText(this);

        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        final NavigationView navigationView = findViewById(R.id.nav_view);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_products);
        navigationView.bringToFront();

        recyclerView = findViewById(R.id.rvProducts);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        productsAdapter = new ProductsAdapter(this, null);
        productsAdapter.setClickListener(this);
        recyclerView.setAdapter(productsAdapter);

        new NetworkWorker(this).execute();

        swipeRefreshLayout.setRefreshing(true);
        swipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorAccent));
        swipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {

                    @Override
                    public void onRefresh() {
                        Log.d(TAG, "onRefresh: refreshing data...");
                        recyclerView.setVisibility(View.GONE);
                        new NetworkWorker(Products.this).execute();
                    }
                });

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (sharedPreferences.getBoolean(getString(R.string.key_first_run_products), true)) {
            getWindow().getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    showRvTapTarget();
                }
            });

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(getString(R.string.key_first_run_products), false);
            editor.apply();
        }

        drawer.addDrawerListener(drawerHandler);

        Bundle bundle = getIntent().getExtras();

        if (bundle != null
                &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                &&
                bundle.getBoolean(getString(R.string.key_needs_storage_permission), false)) {

            Log.d(TAG, "onCreate: requesting permissions...");

            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_PERMISSION_ID);
        }
    }

    private void showRvTapTarget() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        float x;
        float y;

        x = 50f;
        y = toolbar.getY() + 75f;

        SimpleTarget hamburgerButtonTarget = new SimpleTarget.Builder(this)
                .setPoint(x, y)
                .setShape(new Circle(150f))
                .setTitle(getString(R.string.products_feature_discovery))
                .setDescription(getString(R.string.products_feature_discovery_description))
                .setOverlayPoint(x + 100f, y + 150f)
                .build();

        x += 75f;
        y += 125f;

        SimpleTarget rvItemTarget = new SimpleTarget.Builder(this)
                .setPoint(x, y)
                .setShape(new Circle(200f))
                .setTitle(getString(R.string.products_order))
                .setDescription(getString(R.string.products_order_description))
                .setOverlayPoint(x + 150f, y + 150f)
                .build();

        Spotlight spotlight = Spotlight.with(this)
                .setOverlayColor(R.color.background)
                .setDuration(100L)
                .setAnimation(new AccelerateDecelerateInterpolator())
                .setTargets(rvItemTarget, hamburgerButtonTarget)
                .setClosedOnTouchedOutside(true);

        spotlight.start();
    }

    @Override
    public void onItemClick(View view, int position) {
        JSONObject product = productsAdapter.getProduct(position);

        String productName = getString(R.string.product_unknown);
        try {
            productName = product.getString("Nombre");
            productId = product.getInt("ID_Producto");
        } catch (JSONException e) {
            e.printStackTrace();
        }


        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(String.format(getString(R.string.products_order_confirmation), productName))
                .setPositiveButton(getString(R.string.dialog_yes), dialogClickListener)
                .setNegativeButton(getString(R.string.dialog_no), dialogClickListener)
                .show();
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
                rlReportAlert = new RelativeLayout(Products.this);
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

                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(Products.this)
                        .setTitle(getString(R.string.report_title))
                        .setView(rlReportAlert)
                        .setPositiveButton(getString(R.string.action_send), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ReportWorker reportWorker = new ReportWorker(Products.this);
                                reportWorker.setReportContent(etReportContent.getText().toString());
                                reportWorker.execute();
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

    private static class NetworkWorker extends AsyncTask<Void, Void, Void> {
        private WeakReference<Products> productsActivity;

        NetworkWorker(Products productsActivity) {
            this.productsActivity = new WeakReference<>(productsActivity);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            final Products reference = productsActivity.get();

            if (productsActivity == null) {
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

                    // TODO: DEBUGGING!!! REMOVE THIS FOR PRODUCTION.
                    httpsURLConnection.setSSLSocketFactory(SSLCertificateSocketFactory.getInsecure(0, null));
                    httpsURLConnection.setHostnameVerifier(new AllowAllHostnameVerifier());

                    Uri.Builder builder = new Uri.Builder()
                            .appendQueryParameter(reference.getString(R.string.server_request_param), reference.getString(R.string.request_getUserProducts));

                    String query = builder.build().getEncodedQuery();

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

                    final JSONArray jsonArray = new JSONArray(stringBuilder.toString());

                    if (jsonArray.length() > 0) {
                        Log.d(TAG, "doInBackground: products: " + jsonArray.toString());
                        reference.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                reference.productsAdapter.setNewData(jsonArray);

                                ImageView icNoProducts = reference.findViewById(R.id.icNoProducts);
                                TextView tvNoProducts = reference.findViewById(R.id.tvNoProducts);
                                RecyclerView recyclerView = reference.findViewById(R.id.rvProducts);
                                ImageView icError = reference.findViewById(R.id.icError);
                                TextView tvError = reference.findViewById(R.id.tvError);
                                TextView tvErrorExtra = reference.findViewById(R.id.tvErrorExtra);

                                icNoProducts.setVisibility(View.GONE);
                                tvNoProducts.setVisibility(View.GONE);
                                icError.setVisibility(View.GONE);
                                tvError.setVisibility(View.GONE);
                                tvErrorExtra.setVisibility(View.GONE);
                                recyclerView.setVisibility(View.VISIBLE);
                            }
                        });
                    } else {
                        reference.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ImageView icNoProducts = reference.findViewById(R.id.icNoProducts);
                                TextView tvNoProducts = reference.findViewById(R.id.tvNoProducts);
                                RecyclerView recyclerView = reference.findViewById(R.id.rvProducts);
                                ImageView icError = reference.findViewById(R.id.icError);
                                TextView tvError = reference.findViewById(R.id.tvError);
                                TextView tvErrorExtra = reference.findViewById(R.id.tvErrorExtra);

                                icNoProducts.setVisibility(View.VISIBLE);
                                tvNoProducts.setVisibility(View.VISIBLE);
                                icError.setVisibility(View.GONE);
                                tvError.setVisibility(View.GONE);
                                tvErrorExtra.setVisibility(View.GONE);
                                recyclerView.setVisibility(View.GONE);
                            }
                        });
                    }
                } catch (final Exception e) {
                    reference.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ImageView icError = reference.findViewById(R.id.icError);
                            TextView tvError = reference.findViewById(R.id.tvError);
                            TextView tvErrorExtra = reference.findViewById(R.id.tvErrorExtra);

                            icError.setVisibility(View.VISIBLE);
                            tvError.setVisibility(View.VISIBLE);
                            tvErrorExtra.setText(String.format(reference.getString(R.string.products_error_server_failure), reference.getString(R.string.server_hostname)));
                            tvErrorExtra.setVisibility(View.VISIBLE);
                        }
                    });
                    e.printStackTrace();
                } finally {
                    if (httpsURLConnection != null) {
                        httpsURLConnection.disconnect();
                    }
                }
            } catch (final MalformedURLException e) {
                reference.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ImageView icError = reference.findViewById(R.id.icError);
                        TextView tvError = reference.findViewById(R.id.tvError);
                        TextView tvErrorExtra = reference.findViewById(R.id.tvErrorExtra);

                        icError.setVisibility(View.VISIBLE);
                        tvError.setVisibility(View.VISIBLE);
                        tvErrorExtra.setText(reference.getString(R.string.products_error_malformed_url));
                        tvErrorExtra.setVisibility(View.VISIBLE);
                    }
                });
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            final Products reference = productsActivity.get();

            if (productsActivity != null) {
                SwipeRefreshLayout swipeRefreshLayout = reference.findViewById(R.id.swipeRefreshLayout);
                swipeRefreshLayout.setRefreshing(false);


                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(reference);

                if (sharedPreferences.getBoolean(reference.getString(R.string.key_first_run_products), true)) {

                    reference.productsAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                        @Override
                        public void onChanged() {
                            reference.showRvTapTarget();
                            super.onChanged();
                        }
                    });

                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean(reference.getString(R.string.key_first_run_products), false);
                    editor.apply();
                }
            }
        }
    }

    private static class OrderRequestNetworkWorker extends AsyncTask<Void, Void, Void> {
        private static final String ORDER_PASS = "0";
        private static final String ORDER_ERROR = "1";
        private static final String ORDER_NOT_ALLOWED = "2";
        private static final String ORDER_ALREADY_ORDERED = "3";
        private WeakReference<Products> productsActivity;
        private int productId = -1;

        OrderRequestNetworkWorker(Products productsActivity) {
            this.productsActivity = new WeakReference<>(productsActivity);
        }

        private String getEncodedProductId(String key, int productId) {
            return SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 0 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + productId;
        }

        public void setProductId(int id) {
            productId = id;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            final Products reference = productsActivity.get();

            if (productsActivity == null) {
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
                            .appendQueryParameter(reference.getString(R.string.server_request_param), reference.getString(R.string.request_makeOrder));

                    String query = builder.build().getEncodedQuery() + getEncodedProductId(reference.getString(R.string.server_content_param), productId);
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
                        case ORDER_PASS:
                            reference.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(reference, reference.getString(R.string.order_success), Toast.LENGTH_LONG).show();

                                    Intent intent = new Intent(reference, OrderStatusLooper.class);
                                    reference.stopService(intent);
                                    reference.startService(intent);

                                    Intent orderStatusDisplay = new Intent(reference, Requests.class);
                                    reference.startActivity(orderStatusDisplay);
                                    reference.finish();
                                }
                            });
                            break;
                        case ORDER_ALREADY_ORDERED:
                            reference.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(reference, reference.getString(R.string.order_already_ordered), Toast.LENGTH_LONG).show();
                                }
                            });
                            break;
                        case ORDER_ERROR:
                            reference.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(reference, reference.getString(R.string.products_error), Toast.LENGTH_LONG).show();
                                }
                            });
                            break;
                        case ORDER_NOT_ALLOWED:
                            reference.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(reference, reference.getString(R.string.not_allowed_error), Toast.LENGTH_LONG).show();
                                }
                            });
                            break;
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

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
        }
    }
}
