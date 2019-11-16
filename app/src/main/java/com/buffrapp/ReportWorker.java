package com.buffrapp;

import android.app.Activity;
import android.os.Build;
import android.widget.Toast;

import java.lang.ref.WeakReference;

import util.ActivityNetworkWorker;

public class ReportWorker extends ActivityNetworkWorker {

    private static final String TAG = "ReportWorker";

    private static final String REPORT_PASS = "0";
    private static final String REPORT_ERROR = "1";
    private static final Character SYMBOL_AMPERSAND = '&';
    private static final Character SYMBOL_EQUALS = '=';
    private static final Character SYMBOL_BRACKET_OPEN = '[';
    private static final Character SYMBOL_BRACKET_CLOSED = ']';
    private WeakReference<Activity> reportActivity;

    ReportWorker(Activity reportActivity, String reportText) {
        this.reportActivity = new WeakReference<>(reportActivity);

        setTargetActivity(reportActivity);

        Activity reference = this.reportActivity.get();

        if (reference != null) {
            setRequest(reference.getString(R.string.request_sendTechnicalReport));
            String key = reference.getString(R.string.server_content_param);

            setEncodedData(SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 0 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + reference.getClass().getSimpleName() +
                    SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 1 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + Build.MANUFACTURER +
                    SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 2 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + Build.MODEL +
                    SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 3 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + Build.PRODUCT +
                    SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 4 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + Build.FINGERPRINT +
                    SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 5 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + Build.BOARD +
                    SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 6 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + Build.TIME +
                    SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 7 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + Build.VERSION.RELEASE +
                    SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 8 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + Build.VERSION.CODENAME +
                    SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 9 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + Build.VERSION.SDK_INT +
                    SYMBOL_AMPERSAND + key + SYMBOL_BRACKET_OPEN + 10 + SYMBOL_BRACKET_CLOSED + SYMBOL_EQUALS + reportText);
        }
    }

    @Override
    protected void handleOutput(String serverOutput) {
        final Activity reference = reportActivity.get();

        if (reportActivity == null) {
            return;
        }

        switch (serverOutput) {
            case REPORT_PASS:
                reference.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(reference, reference.getString(R.string.report_success), Toast.LENGTH_LONG).show();
                    }
                });
                break;
            case REPORT_ERROR:
                showInternalError(reference.getString(R.string.internal_error), reference);
                break;
        }
    }

    @Override
    protected void showInternalError(final String message, final Activity reference) {
        reference.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(reference, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}