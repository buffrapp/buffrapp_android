package com.buffrapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import com.buffrapp.ui.login.LoginActivity;

public class DrawerHandler implements DrawerLayout.DrawerListener {
    private final String TAG = "DrawerHandler";
    private int navCurrentId;
    private Activity activity;

    DrawerHandler(Activity activity) {
        this.activity = activity;
    }

    void setNavCurrentId(int navCurrentId) {
        this.navCurrentId = navCurrentId;
    }

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
        // Handle navigation view item clicks here.
        if (navCurrentId < 0) {
            Log.d(TAG, "onDrawerClosed: no item selected, skipping action...");
        } else {
            if (navCurrentId == R.id.nav_products) {
                Intent intent = new Intent(activity, Products.class);
                activity.startActivity(intent);
            } else if (navCurrentId == R.id.nav_requests) {
                Intent intent = new Intent(activity, Requests.class);
                activity.startActivity(intent);
            } else if (navCurrentId == R.id.nav_history) {
                Intent intent = new Intent(activity, History.class);
                activity.startActivity(intent);
            } else if (navCurrentId == R.id.nav_schedule) {
                Intent intent = new Intent(activity, Schedule.class);
                activity.startActivity(intent);
            } else if (navCurrentId == R.id.nav_profile) {
                Intent intent = new Intent(activity, Profile.class);
                activity.startActivity(intent);
            } else if (navCurrentId == R.id.nav_logout) {
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.remove(activity.getString(R.string.key_session_id));
                editor.apply();

                Intent orderStatusLooperIntent = new Intent(activity, OrderStatusLooper.class);
                activity.stopService(orderStatusLooperIntent);

                OrderStatusLooper.removePushNotification(activity);

                Intent intent = new Intent(activity, LoginActivity.class);

                activity.startActivity(intent);
            } else if (navCurrentId == R.id.nav_about) {
                Intent intent = new Intent(activity, About.class);
                activity.startActivity(intent);
            }

            Log.d(TAG, "onDrawerClosed: selected ID is " + navCurrentId);

            if (!activity.getWindow().getDecorView().isShown()) {
                activity.finish();
            }
        }
    }
}
