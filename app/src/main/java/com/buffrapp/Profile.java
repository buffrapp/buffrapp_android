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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.buffrapp.ui.login.LoginActivity;
import com.google.android.material.navigation.NavigationView;
import com.takusemba.spotlight.Spotlight;
import com.takusemba.spotlight.shape.Circle;
import com.takusemba.spotlight.target.SimpleTarget;

import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
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

public class Profile extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "Profile";

    private int navCurrentId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        final NavigationView navigationView = findViewById(R.id.nav_view);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_profile);
        navigationView.bringToFront();

        EditText etDNI = findViewById(R.id.etDNI);
        etDNI.setKeyListener(null);
        EditText etFullName = findViewById(R.id.etFullName);
        etFullName.setKeyListener(null);

        new NetworkWorker(this).execute();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getBoolean(getString(R.string.key_first_run_profile), true)) {
            getWindow().getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    float x;
                    float y;

                    x = (float) navigationView.getWidth() / 2 + 90f;
                    y = (float) navigationView.getHeight() / 2 - 50f;

                    SimpleTarget middleOfViewTarget = new SimpleTarget.Builder(Profile.this)
                            .setPoint(x, y)
                            .setShape(new Circle(350f))
                            .setTitle(getString(R.string.profile_review))
                            .setDescription(getString(R.string.profile_review_description))
                            .setOverlayPoint(x - 325f, y + 400f)
                            .build();

                    Spotlight spotlight = Spotlight.with(Profile.this)
                            .setOverlayColor(R.color.background)
                            .setDuration(100L)
                            .setAnimation(new AccelerateDecelerateInterpolator())
                            .setTargets(middleOfViewTarget)
                            .setClosedOnTouchedOutside(true);

                    spotlight.start();
                }
            });

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(getString(R.string.key_first_run_profile), false);
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
                if (navCurrentId != R.id.nav_profile) {
                    // Handle navigation view item clicks here.
                    if (navCurrentId < 0) {
                        Log.d(TAG, "onDrawerClosed: no item selected, skipping action...");
                    } else {
                        if (navCurrentId == R.id.nav_products) {
                            Intent intent = new Intent(Profile.this, Products.class);
                            startActivity(intent);
                        } else if (navCurrentId == R.id.nav_requests) {
                            Intent intent = new Intent(Profile.this, Requests.class);
                            startActivity(intent);
                        } else if (navCurrentId == R.id.nav_history) {
                            Intent intent = new Intent(Profile.this, History.class);
                            startActivity(intent);
                        } else if (navCurrentId == R.id.nav_schedule) {
                        } else if (navCurrentId == R.id.nav_logout) {
                            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Profile.this);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.remove(getString(R.string.key_session_id));
                            editor.apply();

                            Intent intent = new Intent(Profile.this, LoginActivity.class);
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
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        navCurrentId = item.getItemId();
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private static class NetworkWorker extends AsyncTask<Void, Void, Void> {
        private static final String PROFILE_ERROR = "1";
        private static final String PROFILE_NOT_ALLOWED = "2";
        private static final String PROFILE_NOT_ENOUGH_FIELDS = "3";
        private WeakReference<Profile> profileActivity;

        NetworkWorker(Profile profileActivity) {
            this.profileActivity = new WeakReference<>(profileActivity);
        }

        private void showInternalError(final String message) {
            Log.d(TAG, "doInBackground: an internal error has occurred.");
            final Profile reference = profileActivity.get();

            if (profileActivity == null) {
                Log.d(TAG, "doInBackground: showInternalError: failed to get a reference.");
                return;
            }

            reference.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ImageView icError = reference.findViewById(R.id.icError);
                    TextView tvError = reference.findViewById(R.id.tvError);
                    TextView tvErrorExtra = reference.findViewById(R.id.tvErrorExtra);
                    TextView etDNI = reference.findViewById(R.id.etDNI);
                    TextView etMailAddress = reference.findViewById(R.id.etMailAddress);
                    TextView etPassword = reference.findViewById(R.id.etPassword);
                    TextView etFullName = reference.findViewById(R.id.etFullName);
                    LinearLayout llCourseDivision = reference.findViewById(R.id.llCourseDivision);
                    ProgressBar progressBar = reference.findViewById(R.id.progressBar);
                    ImageView icProfile = reference.findViewById(R.id.icProfile);
                    Button btUpdate = reference.findViewById(R.id.btUpdate);

                    progressBar.setVisibility(View.GONE);
                    icProfile.setVisibility(View.GONE);
                    etDNI.setVisibility(View.GONE);
                    etMailAddress.setVisibility(View.GONE);
                    etPassword.setVisibility(View.GONE);
                    etFullName.setVisibility(View.GONE);
                    llCourseDivision.setVisibility(View.GONE);
                    btUpdate.setVisibility(View.GONE);
                    icError.setVisibility(View.VISIBLE);
                    tvError.setVisibility(View.VISIBLE);
                    tvErrorExtra.setVisibility(View.VISIBLE);
                    tvErrorExtra.setText(message);
                }
            });
        }

        @Override
        protected Void doInBackground(Void... voids) {
            final Profile reference = profileActivity.get();

            if (profileActivity == null) {
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
                            .appendQueryParameter(reference.getString(R.string.server_request_param), reference.getString(R.string.request_getUserProfile));

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
                        case PROFILE_ERROR:
                            showInternalError(reference.getString(R.string.internal_error));
                            break;
                        case PROFILE_NOT_ALLOWED:
                            showInternalError(reference.getString(R.string.not_allowed_error));
                            break;
                        case PROFILE_NOT_ENOUGH_FIELDS:
                            showInternalError(reference.getString(R.string.profile_load_failed));
                            break;
                        default:
                            final JSONObject jsonObject = new JSONObject(stringBuilder.toString());

                            if (jsonObject.length() > 0) {
                                Log.d(TAG, "doInBackground: jsonObject: " + jsonObject.toString());

                                reference.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        ImageView icError = reference.findViewById(R.id.icError);
                                        TextView tvError = reference.findViewById(R.id.tvError);
                                        ProgressBar progressBar = reference.findViewById(R.id.progressBar);
                                        ImageView icProfile = reference.findViewById(R.id.icProfile);
                                        TextView etDNI = reference.findViewById(R.id.etDNI);
                                        TextView etMailAddress = reference.findViewById(R.id.etMailAddress);
                                        TextView etPassword = reference.findViewById(R.id.etPassword);
                                        TextView etFullName = reference.findViewById(R.id.etFullName);
                                        TextView etCourse = reference.findViewById(R.id.etCourse);
                                        TextView etDivision = reference.findViewById(R.id.etDivision);
                                        LinearLayout llCourseDivision = reference.findViewById(R.id.llCourseDivision);
                                        Button btUpdate = reference.findViewById(R.id.btUpdate);

                                        icError.setVisibility(View.GONE);
                                        tvError.setVisibility(View.GONE);
                                        progressBar.setVisibility(View.GONE);
                                        icProfile.setVisibility(View.VISIBLE);
                                        etDNI.setVisibility(View.VISIBLE);
                                        etMailAddress.setVisibility(View.VISIBLE);
                                        etPassword.setVisibility(View.VISIBLE);
                                        etFullName.setVisibility(View.VISIBLE);
                                        llCourseDivision.setVisibility(View.VISIBLE);
                                        btUpdate.setVisibility(View.VISIBLE);

                                        try {
                                            etDNI.setText(jsonObject.getString("DNI"));
                                            etMailAddress.setText(jsonObject.getString("E-Mail"));
                                            // etPassword.setText(jsonObject.getString("Password"));
                                            etFullName.setText(jsonObject.getString("Nombre"));
                                            etCourse.setText(jsonObject.getString("Curso"));
                                            etDivision.setText(jsonObject.getString("Division"));
                                        } catch (JSONException e) {
                                            e.printStackTrace();

                                            showInternalError(reference.getString(R.string.profile_load_failed));
                                        }
                                    }
                                });
                            } else {
                                showInternalError(reference.getString(R.string.profile_load_failed));
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
        }
    }
}
