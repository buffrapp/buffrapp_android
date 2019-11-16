package com.buffrapp.ui.login;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.buffrapp.MainActivity;
import com.buffrapp.R;

import org.json.JSONArray;
import org.json.JSONException;

import java.lang.ref.WeakReference;

import util.ActivityNetworkWorker;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final Character SYMBOL_AMPERSAND = '&';
    private static final Character SYMBOL_EQUALS = '=';
    private static final Character SYMBOL_BRACKET_OPEN = '[';
    private static final Character SYMBOL_BRACKET_CLOSED = ']';
    private static final String SYMBOL_AMPERSAND_ENCODED = "%26";

    private LoginViewModel loginViewModel;
    private TextureView textureView;
    private MediaPlayer mediaPlayer;
    private Button loginButton;

    private EditText etUsername;
    private EditText etPassword;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        textureView = findViewById(R.id.video);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                try {
                    mediaPlayer = MediaPlayer.create(LoginActivity.this, R.raw.login_background);
                    mediaPlayer.setSurface(new Surface(surface));
                    mediaPlayer.setLooping(true);
                    mediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                    mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            mediaPlayer.start();
                        }
                    });
                } catch (Exception e) {
                    Log.d(TAG, "onSurfaceTextureAvailable: failed to start background video playback.");
                    e.printStackTrace();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });

        loginViewModel = ViewModelProviders.of(this, new LoginViewModelFactory())
                .get(LoginViewModel.class);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        loginButton = findViewById(R.id.login);

        loginViewModel.getLoginFormState().observe(this, new Observer<LoginFormState>() {
            @Override
            public void onChanged(@Nullable LoginFormState loginFormState) {
                if (loginFormState == null) {
                    return;
                }
                loginButton.setEnabled(loginFormState.isDataValid());
                if (loginFormState.getUsernameError() != null) {
                    etUsername.setError(getString(loginFormState.getUsernameError()));
                }
                if (loginFormState.getPasswordError() != null) {
                    etPassword.setError(getString(loginFormState.getPasswordError()));
                }
            }
        });

        loginViewModel.getLoginResult().observe(this, new Observer<LoginResult>() {
            @Override
            public void onChanged(@Nullable LoginResult loginResult) {
                if (loginResult == null) {
                    return;
                }
                if (loginResult.getError() != null) {
                    showLoginFailed(loginResult.getError());
                }
                setResult(Activity.RESULT_OK);

                //Complete and destroy login activity once successful
                finish();
            }
        });

        TextWatcher afterTextChangedListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // ignore
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // ignore
            }

            @Override
            public void afterTextChanged(Editable s) {
                loginViewModel.loginDataChanged(etUsername.getText().toString(),
                        etPassword.getText().toString());
            }
        };
        etUsername.addTextChangedListener(afterTextChangedListener);
        etPassword.addTextChangedListener(afterTextChangedListener);
        etPassword.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE &&
                        !(etUsername.getText().toString().isEmpty() && etPassword.getText().toString().isEmpty())) {
                    loginButton.performClick();
                }
                return false;
            }
        });

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
    }

    public void loginButtonHandler(View view) {
        loginButton.setText(getString(R.string.button_wait));
        loginButton.setBackgroundColor(getResources().getColor(R.color.colorAccentDark));
        loginButton.setEnabled(false);
        new LoginNetworkWorker(this, etUsername.getText().toString(), etPassword.getText().toString()).execute();
    }

    private void updateUiWithUser() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private static class LoginNetworkWorker extends ActivityNetworkWorker {
        private static final String LOGIN_ERROR = "1";
        private static final String LOGIN_BAD_CREDENTIALS = "3";
        private WeakReference<LoginActivity> loginActivity;

        LoginNetworkWorker(LoginActivity loginActivity, String username, String password) {
            this.loginActivity = new WeakReference<>(loginActivity);

            setTargetActivity(loginActivity);

            LoginActivity reference = this.loginActivity.get();

            if (reference != null) {
                setRequest(reference.getString(R.string.request_doUserLogin));
                String key = reference.getString(R.string.server_content_param);

                setEncodedData(SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 0 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS +
                        username.replace(SYMBOL_AMPERSAND.toString(), SYMBOL_AMPERSAND_ENCODED) +
                        SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 1 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS +
                        password.replace(SYMBOL_AMPERSAND.toString(), SYMBOL_AMPERSAND_ENCODED));
            }
        }

        @Override
        protected void handleOutput(String serverOutput) {
            final LoginActivity reference = loginActivity.get();

            if (loginActivity == null) {
                Log.d(TAG, "doInBackground: unable to generate a new reference.");
                return;
            }

            switch (serverOutput) {
                case LOGIN_ERROR:
                    reference.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            reference.showLoginFailed(R.string.login_inconsistent);
                        }
                    });
                    break;
                case LOGIN_BAD_CREDENTIALS:
                    reference.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            EditText passwordEditText = reference.findViewById(R.id.etPassword);
                            passwordEditText.setError(reference.getString(R.string.login_failed));
                        }
                    });
                    break;
                default:
                    try {
                        JSONArray jsonArray = new JSONArray(serverOutput);
                        final String userName = jsonArray.getString(0);

                        reference.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(reference);
                                SharedPreferences.Editor sharedPreferencesEditor = sharedPreferences.edit();

                                sharedPreferencesEditor.putString(reference.getString(R.string.key_session_id), getCookies());
                                sharedPreferencesEditor.putString(reference.getString(R.string.key_session_user_name), userName);
                                sharedPreferencesEditor.apply();

                                reference.updateUiWithUser();
                            }
                        });
                    } catch (JSONException jsonException) {
                        showInternalError(reference.getString(R.string.login_crash), reference);
                    }
            }

            if (serverOutput.equals(LOGIN_ERROR) || serverOutput.equals(LOGIN_BAD_CREDENTIALS)) {
                reference.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Button button = reference.findViewById(R.id.login);
                        button.setText(reference.getString(R.string.action_sign_in));
                        button.setEnabled(true);
                        button.setBackgroundColor(reference.getResources().getColor(R.color.colorAccent));
                    }
                });

                if (serverOutput.equals(LOGIN_ERROR) || serverOutput == null) {
                    reference.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            reference.showLoginFailed(R.string.login_crash);
                        }
                    });
                }
            }
        }

        @Override
        protected void showInternalError(String message, Activity reference) {

        }
    }

    private void showLoginFailed(@StringRes Integer errorString) {
        Toast.makeText(getApplicationContext(), errorString, Toast.LENGTH_SHORT).show();
    }
}
