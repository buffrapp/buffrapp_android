package com.buffrapp;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.SSLCertificateSocketFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
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

import com.buffrapp.ui.login.LoginActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
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

    private static final Character SYMBOL_AMPERSAND = '&';
    private static final Character SYMBOL_EQUALS = '=';
    private static final Character SYMBOL_BRACKET_OPEN = '[';
    private static final Character SYMBOL_BRACKET_CLOSED = ']';

    private ProductsAdapter productsAdapter;
    private RecyclerView recyclerView;

    private int navCurrentId = -1;

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

        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

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
        final NavigationView navigationView = findViewById(R.id.nav_view);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_products);
        navigationView.bringToFront();

        recyclerView = findViewById(R.id.rv_products);
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

            @SuppressWarnings("ConstantConditions")
            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                if (navCurrentId != R.id.nav_products) {
                    // Handle navigation view item clicks here.
                    if (navCurrentId < 0) {
                        Log.d(TAG, "onDrawerClosed: no item selected, skipping action...");
                    } else {
                        if (navCurrentId == R.id.nav_requests) {
                            Intent intent = new Intent(Products.this, Requests.class);
                            startActivity(intent);
                        } else if (navCurrentId == R.id.nav_history) {
                            Intent intent = new Intent(Products.this, History.class);
                            startActivity(intent);
                        } else if (navCurrentId == R.id.nav_schedule) {

                        } else if (navCurrentId == R.id.nav_profile) {

                        } else if (navCurrentId == R.id.nav_share) {

                        } else if (navCurrentId == R.id.nav_send) {

                        } else if (navCurrentId == R.id.nav_logout) {
                            Log.d(TAG, "onDrawerClosed: clearing session and restarting...");
                            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Products.this);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.remove(getString(R.string.key_session_id));
                            editor.apply();

                            Intent intent = new Intent(Products.this, LoginActivity.class);
                            startActivity(intent);
                        }

                        Log.d(TAG, "onDrawerClosed: selected ID is " + navCurrentId);

                        finish();
                    }
                }
            }
        });
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
        try {
            textViewNavInfo.setText(String.format(getString(R.string.version), String.valueOf(getApplicationContext().getPackageManager().getPackageInfo(getPackageName(), 0).versionName)));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "onCreate: failed to fetch app version code.", e);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        navCurrentId = item.getItemId();
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
                            .appendQueryParameter(reference.getString(R.string.server_request_param), reference.getString(R.string.request_getProducts));

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

                                ImageView icNoProducts = reference.findViewById(R.id.icEmptyHistory);
                                TextView tvNoProducts = reference.findViewById(R.id.tvEmptyHistory);
                                RecyclerView recyclerView = reference.findViewById(R.id.rv_products);
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
                                ImageView icNoProducts = reference.findViewById(R.id.icEmptyHistory);
                                TextView tvNoProducts = reference.findViewById(R.id.tvEmptyHistory);
                                RecyclerView recyclerView = reference.findViewById(R.id.rv_products);
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
                                    reference.startService(intent);
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
