package com.buffrapp;

import android.content.Intent;
import android.content.SharedPreferences;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.buffrapp.ui.login.LoginActivity;
import com.google.android.material.navigation.NavigationView;
import com.takusemba.spotlight.Spotlight;
import com.takusemba.spotlight.shape.Circle;
import com.takusemba.spotlight.target.SimpleTarget;

import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.json.JSONArray;

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

public class History extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, HistoryAdapter.ItemClickListener {

    private static final String TAG = "History";

    private HistoryAdapter historyAdapter;

    private int navCurrentId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        final NavigationView navigationView = findViewById(R.id.nav_view);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_history);
        ;
        navigationView.bringToFront();

        final RecyclerView recyclerView = findViewById(R.id.rvHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        historyAdapter = new HistoryAdapter(this, null);
        historyAdapter.setClickListener(this);
        recyclerView.setAdapter(historyAdapter);

        swipeRefreshLayout.setRefreshing(true);
        swipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorAccent));
        swipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {

                    @Override
                    public void onRefresh() {
                        Log.d(TAG, "onRefresh: refreshing data...");
                        recyclerView.setVisibility(View.GONE);
                        new NetworkWorker(History.this).execute();
                    }
                });

        new NetworkWorker(this).execute();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getBoolean(getString(R.string.key_first_run_history), true)) {
            getWindow().getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    float x;
                    float y;

                    x = 200f;
                    y = toolbar.getY() + 300;

                    SimpleTarget rvItemTarget = new SimpleTarget.Builder(History.this)
                            .setPoint(x, y)
                            .setShape(new Circle(250f))
                            .setTitle(getString(R.string.history_review))
                            .setDescription(getString(R.string.history_review_description))
                            .setOverlayPoint(x + 150f, y + 250f)
                            .build();

                    Spotlight spotlight = Spotlight.with(History.this)
                            .setOverlayColor(R.color.background)
                            .setDuration(100L)
                            .setAnimation(new AccelerateDecelerateInterpolator())
                            .setTargets(rvItemTarget)
                            .setClosedOnTouchedOutside(true);

                    spotlight.start();
                }
            });

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(getString(R.string.key_first_run_history), false);
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
                if (navCurrentId != R.id.nav_history) {
                    // Handle navigation view item clicks here.
                    if (navCurrentId < 0) {
                        Log.d(TAG, "onDrawerClosed: no item selected, skipping action...");
                    } else {
                        if (navCurrentId == R.id.nav_products) {
                            Intent intent = new Intent(History.this, Products.class);
                            startActivity(intent);
                        } else if (navCurrentId == R.id.nav_requests) {
                            Intent intent = new Intent(History.this, Requests.class);
                            startActivity(intent);
                        } else if (navCurrentId == R.id.nav_schedule) {

                        } else if (navCurrentId == R.id.nav_profile) {
                            Intent intent = new Intent(History.this, Profile.class);
                            startActivity(intent);
                        } else if (navCurrentId == R.id.nav_logout) {
                            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(History.this);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.remove(getString(R.string.key_session_id));
                            editor.apply();

                            Intent intent = new Intent(History.this, LoginActivity.class);
                            startActivity(intent);
                        }

                        Log.d(TAG, "onDrawerClosed: selected ID is " + navCurrentId);

                        finish();
                    }
                }
            }
        });
    }

    @Override
    public void onItemClick(View view, int position) {
    } // this method could be implemented later.

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
        private WeakReference<History> historyActivity;

        private static final String HISTORY_ERROR = "1";
        private static final String HISTORY_NOT_ALLOWED = "2";
        private static final String HISTORY_EMPTY_RESULT = "3";

        NetworkWorker(History historyActivity) {
            this.historyActivity = new WeakReference<>(historyActivity);
        }

        private void showInternalError(final String message) {
            Log.d(TAG, "doInBackground: an internal error has occurred.");
            final History reference = historyActivity.get();

            if (historyActivity == null) {
                Log.d(TAG, "doInBackground: showInternalError: failed to get a reference.");
                return;
            }

            reference.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ImageView icNoHistory = reference.findViewById(R.id.icEmptyHistory);
                    TextView tvNoHistory = reference.findViewById(R.id.tvEmptyHistory);
                    RecyclerView recyclerView = reference.findViewById(R.id.rvHistory);
                    ImageView icError = reference.findViewById(R.id.icError);
                    TextView tvError = reference.findViewById(R.id.tvError);
                    TextView tvErrorExtra = reference.findViewById(R.id.tvErrorExtra);

                    icNoHistory.setVisibility(View.GONE);
                    tvNoHistory.setVisibility(View.GONE);
                    icError.setVisibility(View.VISIBLE);
                    tvError.setVisibility(View.VISIBLE);
                    tvErrorExtra.setVisibility(View.VISIBLE);
                    tvErrorExtra.setText(message);
                    recyclerView.setVisibility(View.GONE);
                }
            });
        }

        private void showNoHistory() {
            Log.d(TAG, "doInBackground: no ongoing orders found.");
            final History reference = historyActivity.get();

            if (historyActivity == null) {
                Log.d(TAG, "doInBackground: showNoOrders: failed to get a reference.");
                return;
            }

            reference.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ImageView icNoHistory = reference.findViewById(R.id.icEmptyHistory);
                    TextView tvNoHistory = reference.findViewById(R.id.tvEmptyHistory);
                    RecyclerView recyclerView = reference.findViewById(R.id.rvHistory);
                    ImageView icError = reference.findViewById(R.id.icError);
                    TextView tvError = reference.findViewById(R.id.tvError);
                    TextView tvErrorExtra = reference.findViewById(R.id.tvErrorExtra);

                    icNoHistory.setVisibility(View.VISIBLE);
                    tvNoHistory.setVisibility(View.VISIBLE);
                    icError.setVisibility(View.GONE);
                    tvError.setVisibility(View.GONE);
                    tvErrorExtra.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.GONE);
                }
            });
        }

        @Override
        protected Void doInBackground(Void... voids) {
            final History reference = historyActivity.get();

            if (historyActivity == null) {
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
                            .appendQueryParameter(reference.getString(R.string.server_request_param), reference.getString(R.string.request_getUserHistory));

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
                        case HISTORY_ERROR:
                            showInternalError(reference.getString(R.string.internal_error));
                            break;
                        case HISTORY_NOT_ALLOWED:
                            showInternalError(reference.getString(R.string.not_allowed_error));
                            break;
                        case HISTORY_EMPTY_RESULT:
                            showNoHistory();
                            break;
                        default:
                            final JSONArray jsonArray = new JSONArray(stringBuilder.toString());

                            if (jsonArray.length() > 0) {
                                Log.d(TAG, "doInBackground: jsonArray: " + jsonArray.toString());

                                reference.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        reference.historyAdapter.setNewData(jsonArray);

                                        ImageView icNoProducts = reference.findViewById(R.id.icEmptyHistory);
                                        TextView tvNoProducts = reference.findViewById(R.id.tvEmptyHistory);
                                        RecyclerView recyclerView = reference.findViewById(R.id.rvHistory);
                                        ImageView icError = reference.findViewById(R.id.icError);
                                        TextView tvError = reference.findViewById(R.id.tvError);

                                        icNoProducts.setVisibility(View.GONE);
                                        tvNoProducts.setVisibility(View.GONE);
                                        icError.setVisibility(View.GONE);
                                        tvError.setVisibility(View.GONE);
                                        recyclerView.setVisibility(View.VISIBLE);
                                    }
                                });
                            } else {
                                showNoHistory();
                            }
                    }
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

            History reference = historyActivity.get();

            if (historyActivity != null) {
                SwipeRefreshLayout swipeRefreshLayout = reference.findViewById(R.id.swipeRefreshLayout);
                swipeRefreshLayout.setRefreshing(false);
            }
        }
    }
}
