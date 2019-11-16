package util;

import android.app.Service;
import android.net.SSLCertificateSocketFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

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

public abstract class ServiceNetworkWorker extends AsyncTask<Void, Void, Void> {
    private static final String TAG = "ServiceNetworkWorker";
    private WeakReference<Service> targetService;
    private String request;
    private String encodedData;

    protected ServiceNetworkWorker() {
        this.targetService = null;
    }

    protected void setTargetService(Service service) {
        targetService = new WeakReference<>(service);
    }

    public abstract void handleOutput(String serverOutput);

    public abstract void showInternalError(final String message, final Service reference);

    public void setRequest(String request) {
        this.request = request;
    }

    protected void setEncodedData(String encodedData) {
        this.encodedData = encodedData;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        final Service reference = targetService.get();

        if (targetService == null) {
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
                        .appendQueryParameter(reference.getString(R.string.server_request_param), request);

                StringBuilder query = new StringBuilder();

                query.append(builder.build().getEncodedQuery());

                if (encodedData != null) {
                    query.append(encodedData);
                }

                Log.d(TAG, "doInBackground: query: " + query);

                // Write POST data.
                OutputStream outputStream = new BufferedOutputStream(httpsURLConnection.getOutputStream());
                BufferedWriter bufferedWriter = new BufferedWriter(
                        new OutputStreamWriter(outputStream, reference.getString(R.string.server_encoding)));
                bufferedWriter.write(query.toString());
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

                handleOutput(stringBuilder.toString());
            } catch (Exception e) {
                showInternalError(String.format(reference.getString(R.string.products_error_server_failure), reference.getString(R.string.server_hostname)), reference);
                e.printStackTrace();
            } finally {
                if (httpsURLConnection != null) {
                    httpsURLConnection.disconnect();
                }
            }
        } catch (final MalformedURLException e) {
            showInternalError(reference.getString(R.string.products_error_malformed_url), reference);
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
    }
}
