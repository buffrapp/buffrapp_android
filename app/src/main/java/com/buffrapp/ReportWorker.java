package com.buffrapp;

import android.app.Activity;
import android.net.SSLCertificateSocketFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

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

public class ReportWorker extends AsyncTask<Void, Void, Void> {

    private static final String TAG = "ReportWorker";

    private static final String REPORT_PASS = "0";
    private static final String REPORT_ERROR = "1";
    private static final Character SYMBOL_AMPERSAND = '&';
    private static final Character SYMBOL_EQUALS = '=';
    private static final Character SYMBOL_BRACKET_OPEN = '[';
    private static final Character SYMBOL_BRACKET_CLOSED = ']';
    private static final String EMPTY_STRING = "";
    private WeakReference<Activity> reportActivity;
    private String reportContent = EMPTY_STRING;

    ReportWorker(Activity reportActivity) {
        this.reportActivity = new WeakReference<>(reportActivity);
    }

    public void setReportContent(String reportContent) {
        this.reportContent = reportContent;
    }

    private String getEncodedProfileData(String key, String activityName, String reportText) {
        return SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 0 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + activityName +
                SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 1 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + Build.MANUFACTURER +
                SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 2 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + Build.MODEL +
                SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 3 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + Build.PRODUCT +
                SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 4 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + Build.FINGERPRINT +
                SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 5 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + Build.BOARD +
                SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 6 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + Build.TIME +
                SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 7 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + Build.VERSION.RELEASE +
                SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 8 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + Build.VERSION.CODENAME +
                SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 9 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + Build.VERSION.SDK_INT +
                SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 10 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + reportText;
    }

    private void showInternalError(final String message) {
        Log.d(TAG, "doInBackground: an internal error has occurred.");
        final Activity reference = reportActivity.get();

        if (reportActivity == null) {
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
        final Activity reference = reportActivity.get();

        if (reportActivity == null) {
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
                        .appendQueryParameter(reference.getString(R.string.server_request_param), reference.getString(R.string.request_sendTechnicalReport));

                String query = builder.build().getEncodedQuery() + getEncodedProfileData(
                        reference.getString(R.string.server_content_param),
                        reference.getClass().getSimpleName(),
                        reportContent);

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
                    case REPORT_PASS:
                        reference.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(reference, reference.getString(R.string.report_success), Toast.LENGTH_LONG).show();
                            }
                        });
                        break;
                    case REPORT_ERROR:
                        showInternalError(reference.getString(R.string.internal_error));
                        break;
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