package com.buffrapp;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.buffrapp.ui.login.LoginActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String session_id = PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.key_session_id), null);
        if (session_id == null) {
            Log.d(TAG, "onCreate: session ID was null, loading LoginActivity...");
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        } else {
            Intent orderStatusLooperIntent = new Intent(this, OrderStatusLooper.class);
            stopService(orderStatusLooperIntent);

            try {
                startService(orderStatusLooperIntent);
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }

            Log.d(TAG, "onCreate: session ID found, it was \"" + session_id + "\", loading Products...");
            Intent intent = new Intent(this, Products.class);
            startActivity(intent);
            finish();
        }
    }
}
