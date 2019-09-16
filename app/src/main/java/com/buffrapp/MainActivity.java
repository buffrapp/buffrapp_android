package com.buffrapp;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.buffrapp.ui.login.LoginActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // setContentView(R.layout.activity_main);
        setTheme(R.style.LoginActivity);

        String session_id = PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.key_session_id), null);
        if (session_id == null) {
            Log.d(TAG, "onCreate: session ID was null, loading LoginActivity...");
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        } else {
            Intent orderStatusLooperIntent = new Intent(this, OrderStatusLooper.class);
            stopService(orderStatusLooperIntent);
            startService(orderStatusLooperIntent);

            Log.d(TAG, "onCreate: session ID found, it was \"" + session_id + "\", loading Products...");
            Intent intent = new Intent(this, Products.class);
            startActivity(intent);
        }
    }
}
