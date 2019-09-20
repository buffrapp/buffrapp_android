package com.buffrapp.ui.login;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.MediaPlayer;
import android.net.SSLCertificateSocketFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.buffrapp.MainActivity;
import com.buffrapp.R;

import org.apache.http.conn.ssl.AllowAllHostnameVerifier;

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

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final Character SYMBOL_AMPERSAND = '&';
    private static final Character SYMBOL_EQUALS = '=';
    private static final Character SYMBOL_BRACKET_OPEN = '[';
    private static final Character SYMBOL_BRACKET_CLOSED = ']';
    private static final String SYMBOL_AMPERSAND_ENCODED = "%26";

    private LoginViewModel loginViewModel;
    private VideoView videoView;
    private Button loginButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme(R.style.LoginActivity);
        setContentView(R.layout.activity_login);
        loginViewModel = ViewModelProviders.of(this, new LoginViewModelFactory())
                .get(LoginViewModel.class);

        final EditText usernameEditText = findViewById(R.id.username);
        final EditText passwordEditText = findViewById(R.id.password);
        loginButton = findViewById(R.id.login);

        loginViewModel.getLoginFormState().observe(this, new Observer<LoginFormState>() {
            @Override
            public void onChanged(@Nullable LoginFormState loginFormState) {
                if (loginFormState == null) {
                    return;
                }
                loginButton.setEnabled(loginFormState.isDataValid());
                if (loginFormState.getUsernameError() != null) {
                    usernameEditText.setError(getString(loginFormState.getUsernameError()));
                }
                if (loginFormState.getPasswordError() != null) {
                    passwordEditText.setError(getString(loginFormState.getPasswordError()));
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
                loginViewModel.loginDataChanged(usernameEditText.getText().toString(),
                        passwordEditText.getText().toString());
            }
        };
        usernameEditText.addTextChangedListener(afterTextChangedListener);
        passwordEditText.addTextChangedListener(afterTextChangedListener);
        passwordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE &&
                        !(usernameEditText.getText().toString().isEmpty() && passwordEditText.getText().toString().isEmpty())) {
                    loginButton.performClick();
                }
                return false;
            }
        });

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        Resources resources = this.getResources();
        videoView = findViewById(R.id.video);
        Uri uri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(resources.getResourcePackageName(R.raw.login_background))
                .appendPath(resources.getResourceTypeName(R.raw.login_background))
                .appendPath(resources.getResourceEntryName(R.raw.login_background))
                .build();
        videoView.setVideoURI(uri);
        videoView.start();
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                videoView.start();
            }
        });
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        videoView.start();
    }

    public void loginButtonHandler(View view) {
        loginButton.setText(getString(R.string.button_wait));
        loginButton.setBackgroundColor(getResources().getColor(R.color.colorAccentDark));
        loginButton.setEnabled(false);
        new NetworkWorker(this).execute();
    }

    private void updateUiWithUser() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    private static class NetworkWorker extends AsyncTask<Void, Void, Void> {
        private static final int LOGIN_PASS = 0;
        private static final int LOGIN_ERROR = 1;
        private static final int LOGIN_BAD_CREDENTIALS = 3;
        private WeakReference<LoginActivity> loginActivity;
        private int response = -1;

        NetworkWorker(LoginActivity loginActivity) {
            this.loginActivity = new WeakReference<>(loginActivity);
        }

        private String getEncodedAuthQuery(String key, String username, String password) {
            return SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 0 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS +
                    username.replace(SYMBOL_AMPERSAND.toString(), SYMBOL_AMPERSAND_ENCODED) +
                    SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 1 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS +
                    password.replace(SYMBOL_AMPERSAND.toString(), SYMBOL_AMPERSAND_ENCODED);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            final LoginActivity reference = loginActivity.get();
            EditText usernameEditText;
            EditText passwordEditText;

            if (loginActivity == null) {
                Log.d(TAG, "doInBackground: unable to generate a new reference.");
                return null;
            }

            usernameEditText = reference.findViewById(R.id.username);
            passwordEditText = reference.findViewById(R.id.password);

            String preURL = reference.getString(R.string.server_proto) + reference.getString(R.string.server_hostname) + reference.getString(R.string.server_path);
            Log.d(TAG, "populateView: generated URL from resources: \"" + preURL + "\"");

            try {
                URL url = new URL(preURL);
                HttpsURLConnection httpsURLConnection = null;

                Log.d(TAG, "doInBackground: DOUUUUU funciona man.");

                try {
                    // Try to open a connection.
                    httpsURLConnection = (HttpsURLConnection) url.openConnection();
                    httpsURLConnection.setConnectTimeout(reference.getResources().getInteger(R.integer.connection_timeout));
                    httpsURLConnection.setRequestMethod(reference.getString(R.string.server_request_method));

                    // TODO: DEBUGGING!!! REMOVE THIS FOR PRODUCTION.
                    httpsURLConnection.setSSLSocketFactory(SSLCertificateSocketFactory.getInsecure(0, null));
                    httpsURLConnection.setHostnameVerifier(new AllowAllHostnameVerifier());

                    Uri.Builder builder = new Uri.Builder()
                            .appendQueryParameter(reference.getString(R.string.server_request_param), reference.getString(R.string.request_doUserLogin));

                    String query = builder.build().getEncodedQuery() + getEncodedAuthQuery(reference.getString(R.string.server_content_param), usernameEditText.getText().toString(), passwordEditText.getText().toString());
                    Log.d(TAG, "doInBackground: query: " + query);

                    // Write POST data.
                    OutputStream outputStream = new BufferedOutputStream(httpsURLConnection.getOutputStream());
                    BufferedWriter bufferedWriter = new BufferedWriter(
                            new OutputStreamWriter(outputStream, reference.getString(R.string.server_encoding)));
                    bufferedWriter.write(query);
                    bufferedWriter.flush();
                    bufferedWriter.close();

                    // Retrieve the response.
                    final String session_id = httpsURLConnection.getHeaderField(reference.getString(R.string.server_cookie_response_key));
                    Log.d(TAG, "doInBackground: " + session_id);
                    InputStream inputStream = new BufferedInputStream(httpsURLConnection.getInputStream());
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line);
                    }

                    response = -1;
                    try {
                        response = Integer.valueOf(stringBuilder.toString());
                    } catch (NumberFormatException numberFormatException) {
                        numberFormatException.printStackTrace();
                    }

                    switch (response) {
                        case LOGIN_PASS:
                            reference.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(reference);
                                    SharedPreferences.Editor sharedPreferencesEditor = sharedPreferences.edit();

                                    sharedPreferencesEditor.putString(reference.getString(R.string.key_session_id), session_id);
                                    sharedPreferencesEditor.apply();
                                }
                            });
                            break;
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
                                    EditText passwordEditText = reference.findViewById(R.id.password);
                                    passwordEditText.setError(reference.getString(R.string.login_failed));
                                }
                            });
                            break;
                    }

                    Log.d(TAG, "populateView: done fetching data, the result is: \"" + stringBuilder.toString() + "\"");
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

            if (response != LOGIN_PASS) {
                reference.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Button button = reference.findViewById(R.id.login);
                        button.setText(reference.getString(R.string.action_sign_in));
                        button.setEnabled(true);
                        button.setBackgroundColor(reference.getResources().getColor(R.color.colorAccent));
                    }
                });

                if (response < LOGIN_BAD_CREDENTIALS) {
                    reference.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            reference.showLoginFailed(R.string.login_crash);
                        }
                    });
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            final LoginActivity reference = loginActivity.get();

            if (response == LOGIN_PASS) {
                reference.updateUiWithUser();
            }
        }
    }

    private void showLoginFailed(@StringRes Integer errorString) {
        Toast.makeText(getApplicationContext(), errorString, Toast.LENGTH_SHORT).show();
    }
}
