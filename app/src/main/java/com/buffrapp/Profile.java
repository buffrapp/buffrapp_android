package com.buffrapp;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.SharedPreferences;
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

import com.google.android.material.navigation.NavigationView;
import com.takusemba.spotlight.Spotlight;
import com.takusemba.spotlight.shape.Circle;
import com.takusemba.spotlight.target.SimpleTarget;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;

import util.ActivityNetworkWorker;

public class Profile extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "Profile";
    private static final int EMPTY = 0;

    private EditText etPassword;
    private EditText etMailAddress;
    private EditText etCourse;
    private EditText etDivision;

    private EditText etCurrentPassword;
    private EditText etReportContent;
    private AlertDialog dialog;
    private Button btUpdate;

    private RelativeLayout rlChallengeAlert;
    private RelativeLayout rlReportAlert;

    private DrawerHandler drawerHandler;

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

        drawerHandler = new DrawerHandler(this);

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
        etReportContent = new EditText(this);

        new ProfileActivityNetworkWorker(this).execute();

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
                    ProfileUpdateWorkerActivity profileUpdateWorker = new ProfileUpdateWorkerActivity(Profile.this, etMailAddress.getText().toString(), null, etCourse.getText().toString(), etDivision.getText().toString(), null);
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
                                    ProfileUpdateWorkerActivity profileUpdateWorker = new ProfileUpdateWorkerActivity(Profile.this, etMailAddress.getText().toString(), etPassword.getText().toString(), etCourse.getText().toString(), etDivision.getText().toString(), etCurrentPassword.getText().toString());
                                    profileUpdateWorker.execute();
                                }
                            })
                            .setNegativeButton(getString(R.string.action_cancel), null)
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

        drawer.addDrawerListener(drawerHandler);
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
                rlReportAlert = new RelativeLayout(Profile.this);
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

                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(Profile.this)
                        .setTitle(getString(R.string.report_title))
                        .setView(rlReportAlert)
                        .setPositiveButton(getString(R.string.action_send), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                new ReportWorker(Profile.this, etReportContent.getText().toString()).execute();
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


    private static class ProfileUpdateWorkerActivity extends ActivityNetworkWorker {
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

        ProfileUpdateWorkerActivity(Profile profileActivity, String mailAddress, String password, String course, String division, String currentPassword) {
            this.profileActivity = new WeakReference<>(profileActivity);

            setTargetActivity(profileActivity);

            Profile reference = this.profileActivity.get();

            if (reference != null) {
                setRequest(reference.getString(R.string.request_setUserProfile));
                String key = reference.getString(R.string.server_content_param);

                setEncodedData(SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 0 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + mailAddress +
                        SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 1 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + (password == null ? EMPTY_STRING : password) +
                        SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 2 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + course +
                        SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 3 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + division +
                        SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 4 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + (currentPassword == null ? EMPTY_STRING : currentPassword));
            }
        }

        @Override
        protected void handleOutput(String serverOutput) {
            final Profile reference = profileActivity.get();

            if (profileActivity == null) {
                return;
            }

            switch (serverOutput) {
                case PROFILE_PASS:
                    reference.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(reference, reference.getString(R.string.profile_update_success), Toast.LENGTH_LONG).show();
                        }
                    });
                    break;
                case PROFILE_INCORRECT_PASSWORD:
                    showInternalError(reference.getString(R.string.profile_challenge_failed), reference);
                    break;
                case PROFILE_ERROR:
                    showInternalError(reference.getString(R.string.internal_error), reference);
                    break;
                case PROFILE_NOT_ALLOWED:
                    showInternalError(reference.getString(R.string.not_allowed_error), reference);
                    break;
                case PROFILE_NOT_ENOUGH_FIELDS:
                    showInternalError(reference.getString(R.string.profile_update_failed), reference);
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
        }

        @Override
        protected void showInternalError(final String message, final Activity reference) {
            if (reference == null) {
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
    }

    private static class ProfileActivityNetworkWorker extends ActivityNetworkWorker {
        private static final String PROFILE_ERROR = "1";
        private static final String PROFILE_NOT_ALLOWED = "2";
        private WeakReference<Profile> profileActivity;

        ProfileActivityNetworkWorker(Profile profileActivity) {
            this.profileActivity = new WeakReference<>(profileActivity);

            setTargetActivity(profileActivity);

            final Profile reference = this.profileActivity.get();

            if (reference != null) {
                setRequest(reference.getString(R.string.request_getUserProfile));
            }
        }

        @Override
        protected void handleOutput(String serverOutput) {
            final Profile reference = profileActivity.get();

            switch (serverOutput) {
                case PROFILE_ERROR:
                    showInternalError(reference.getString(R.string.internal_error), reference);
                    break;
                case PROFILE_NOT_ALLOWED:
                    showInternalError(reference.getString(R.string.not_allowed_error), reference);
                    break;
                default:
                    try {
                        final JSONObject jsonObject = new JSONObject(serverOutput);

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

                                        showInternalError(reference.getString(R.string.profile_load_failed), reference);
                                    }
                                }
                            });
                        } else {
                            showInternalError(reference.getString(R.string.profile_load_failed), reference);
                        }
                    } catch (JSONException jsonException) {
                        showInternalError(reference.getString(R.string.profile_load_failed), reference);
                        jsonException.printStackTrace();
                    }
            }
        }

        @Override
        protected void showInternalError(final String message, final Activity reference) {
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
    }
}
