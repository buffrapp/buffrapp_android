package com.buffrapp;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.SSLCertificateSocketFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
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
    private static final int EMPTY = 0;

    private int navCurrentId = -1;

    private EditText etPassword;
    private EditText etMailAddress;
    private EditText etCourse;
    private EditText etDivision;

    private EditText etCurrentPassword;
    private AlertDialog dialog;
    private Button btUpdate;

    private RelativeLayout rlChallengeAlert;

    private void resetView() {
        btUpdate.setText(getString(R.string.action_profile_send_update));
        btUpdate.setEnabled(true);
        btUpdate.setBackgroundColor(getResources().getColor(R.color.colorAccent));

        etPassword.getText().clear();
    }

    // Password validation check
    private boolean isPasswordValid(String password) {
        return password != null && password.trim().length() > 5;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        etMailAddress = findViewById(R.id.etMailAddress);
        etPassword = findViewById(R.id.etPassword);
        etCourse = findViewById(R.id.etCourse);
        etDivision = findViewById(R.id.etDivision);

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

        dialog = null;
        etCurrentPassword = new EditText(Profile.this);

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

        btUpdate = findViewById(R.id.btUpdate);
        btUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btUpdate.setBackgroundColor(getResources().getColor(R.color.colorAccentDark));
                btUpdate.setText(getString(R.string.button_wait));
                btUpdate.setEnabled(false);

                if (etPassword.getText().toString().isEmpty()) {
                    ProfileUpdateWorker profileUpdateWorker = new ProfileUpdateWorker(Profile.this);
                    profileUpdateWorker.setMailAddress(etMailAddress.getText().toString());
                    profileUpdateWorker.setCourse(etCourse.getText().toString());
                    profileUpdateWorker.setDivision(etDivision.getText().toString());
                    profileUpdateWorker.execute();
                } else {
                    rlChallengeAlert = new RelativeLayout(Profile.this);
                    RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT
                    );
                    rlChallengeAlert.setPadding(getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin),
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
                            String password = etCurrentPassword.getText().toString();

                            if (isPasswordValid(password)) {
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            } else {
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                                etCurrentPassword.setError(getString(R.string.invalid_password));
                            }
                        }
                    };

                    etCurrentPassword.setHeight(getResources().getDimensionPixelSize(R.dimen.profile_challenge_input));
                    etCurrentPassword.setHint(getString(R.string.profile_challenge_hint));
                    etCurrentPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    etCurrentPassword.addTextChangedListener(afterTextChangedListener);

                    rlChallengeAlert.addView(etCurrentPassword, layoutParams);

                    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(Profile.this)
                            .setTitle(getString(R.string.profile_challenge_current_password))
                            .setView(rlChallengeAlert)
                            .setPositiveButton(getString(R.string.profile_challenge_action_verify), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    ProfileUpdateWorker profileUpdateWorker = new ProfileUpdateWorker(Profile.this);
                                    profileUpdateWorker.setMailAddress(etMailAddress.getText().toString());
                                    profileUpdateWorker.setPassword(etPassword.getText().toString());
                                    profileUpdateWorker.setCourse(etCourse.getText().toString());
                                    profileUpdateWorker.setDivision(etDivision.getText().toString());
                                    profileUpdateWorker.setCurrentPassword(etCurrentPassword.getText().toString());
                                    profileUpdateWorker.execute();
                                }
                            })
                            .setNegativeButton(getString(R.string.profile_challenge_action_cancel), null)
                            .setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialog) {
                                    rlChallengeAlert.removeAllViews();

                                    resetView();
                                }
                            });

                    dialog = dialogBuilder.create();
                    dialog.show();
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                }
            }
        });

        // Set up password validation for the etPassword field.
        TextWatcher afterTextChangedListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String password = etPassword.getText().toString();

                if (password.length() == EMPTY || isPasswordValid(password)) {
                    btUpdate.setEnabled(true);
                    btUpdate.setBackgroundColor(getResources().getColor(R.color.colorAccent));
                } else {
                    btUpdate.setEnabled(false);
                    btUpdate.setBackgroundColor(getResources().getColor(R.color.colorAccentDark));

                    etPassword.setError(getString(R.string.invalid_password));
                }
            }
        };
        etPassword.addTextChangedListener(afterTextChangedListener);

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


    private static class ProfileUpdateWorker extends AsyncTask<Void, Void, Void> {
        private static final String PROFILE_PASS = "0";
        private static final String PROFILE_ERROR = "1";
        private static final String PROFILE_NOT_ALLOWED = "2";
        private static final String PROFILE_NOT_ENOUGH_FIELDS = "3";
        private static final String PROFILE_INCORRECT_PASSWORD = "4";
        private static final Character SYMBOL_AMPERSAND = '&';
        private static final Character SYMBOL_EQUALS = '=';
        private static final Character SYMBOL_BRACKET_OPEN = '[';
        private static final Character SYMBOL_BRACKET_CLOSED = ']';
        private static final String EMPTY_STRING = "";
        private WeakReference<Profile> profileActivity;
        private String mailAddress = EMPTY_STRING;
        private String password = EMPTY_STRING;
        private String course = EMPTY_STRING;
        private String division = EMPTY_STRING;
        private String currentPassword = EMPTY_STRING;

        ProfileUpdateWorker(Profile profileActivity) {
            this.profileActivity = new WeakReference<>(profileActivity);
        }

        public void setMailAddress(String nMailAddress) {
            mailAddress = nMailAddress;
        }

        public void setPassword(String nPassword) {
            password = nPassword;
        }

        public void setCourse(String nCourse) {
            course = nCourse;
        }

        public void setDivision(String nDivision) {
            division = nDivision;
        }

        public void setCurrentPassword(String nCurrentPassword) {
            currentPassword = nCurrentPassword;
        }

        private String getEncodedProfileData(String key, String mailAddress, String password, String course, String division, String currentPassword) {
            return SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 0 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + mailAddress +
                    SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 1 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + password +
                    SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 2 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + course +
                    SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 3 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + division +
                    SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 4 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + currentPassword;
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
                    Toast.makeText(reference, message, Toast.LENGTH_LONG).show();
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
                            .appendQueryParameter(reference.getString(R.string.server_request_param), reference.getString(R.string.request_setUserProfile));

                    String query = builder.build().getEncodedQuery() + getEncodedProfileData(reference.getString(R.string.server_content_param), mailAddress, password, course, division, currentPassword);
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
                        case PROFILE_PASS:
                            reference.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(reference, reference.getString(R.string.profile_update_success), Toast.LENGTH_LONG).show();
                                }
                            });
                            break;
                        case PROFILE_INCORRECT_PASSWORD:
                            showInternalError(reference.getString(R.string.profile_challenge_failed));
                            break;
                        case PROFILE_ERROR:
                            showInternalError(reference.getString(R.string.internal_error));
                            break;
                        case PROFILE_NOT_ALLOWED:
                            showInternalError(reference.getString(R.string.not_allowed_error));
                            break;
                        case PROFILE_NOT_ENOUGH_FIELDS:
                            showInternalError(reference.getString(R.string.profile_update_failed));
                            break;
                    }

                    reference.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Button btUpdate = reference.findViewById(R.id.btUpdate);
                            btUpdate.setText(reference.getString(R.string.action_profile_send_update));
                            btUpdate.setEnabled(true);
                            btUpdate.setBackgroundColor(reference.getResources().getColor(R.color.colorAccent));
                        }
                    });
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

    private static class NetworkWorker extends AsyncTask<Void, Void, Void> {
        private static final String PROFILE_ERROR = "1";
        private static final String PROFILE_NOT_ALLOWED = "2";
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
                    EditText etDNI = reference.findViewById(R.id.etDNI);
                    EditText etMailAddress = reference.findViewById(R.id.etMailAddress);
                    EditText etPassword = reference.findViewById(R.id.etPassword);
                    EditText etFullName = reference.findViewById(R.id.etFullName);
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
                                        EditText etDNI = reference.findViewById(R.id.etDNI);
                                        EditText etMailAddress = reference.findViewById(R.id.etMailAddress);
                                        EditText etPassword = reference.findViewById(R.id.etPassword);
                                        EditText etFullName = reference.findViewById(R.id.etFullName);
                                        EditText etCourse = reference.findViewById(R.id.etCourse);
                                        EditText etDivision = reference.findViewById(R.id.etDivision);
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
                                            // setPassword.setText(jsonObject.getString("Password"));
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
