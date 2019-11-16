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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.navigation.NavigationView;
import com.takusemba.spotlight.Spotlight;
import com.takusemba.spotlight.shape.Circle;
import com.takusemba.spotlight.target.SimpleTarget;

import org.json.JSONArray;
import org.json.JSONException;

import java.lang.ref.WeakReference;

import util.ActivityNetworkWorker;

public class History extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, HistoryAdapter.ItemClickListener {

    private static final String TAG = "History";

    private HistoryAdapter historyAdapter;

    private EditText etReportContent;
    private AlertDialog dialog;

    private RelativeLayout rlReportAlert;

    private DrawerHandler drawerHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        drawerHandler = new DrawerHandler(this);

        etReportContent = new EditText(this);

        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        final NavigationView navigationView = findViewById(R.id.nav_view);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_history);
        navigationView.bringToFront();

        final RecyclerView recyclerView = findViewById(R.id.rvHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        historyAdapter = new HistoryAdapter(this, null);
        historyAdapter.setClickListener(this);
        recyclerView.setAdapter(historyAdapter);

        swipeRefreshLayout.setRefreshing(true);
        swipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorAccent));
        swipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {

                    @Override
                    public void onRefresh() {
                        Log.d(TAG, "onRefresh: refreshing data...");
                        recyclerView.setVisibility(View.GONE);
                        new HistoryNetworkWorker(History.this).execute();
                    }
                });

        new HistoryNetworkWorker(this).execute();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getBoolean(getString(R.string.key_first_run_history), true)) {
            getWindow().getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    float x;
                    float y;

                    x = 200f;
                    y = toolbar.getY() + 300;

                    SimpleTarget rvItemTarget = new SimpleTarget.Builder(History.this)
                            .setPoint(x, y)
                            .setShape(new Circle(250f))
                            .setTitle(getString(R.string.history_review))
                            .setDescription(getString(R.string.history_review_description))
                            .setOverlayPoint(x + 150f, y + 250f)
                            .build();

                    Spotlight spotlight = Spotlight.with(History.this)
                            .setOverlayColor(R.color.background)
                            .setDuration(100L)
                            .setAnimation(new AccelerateDecelerateInterpolator())
                            .setTargets(rvItemTarget)
                            .setClosedOnTouchedOutside(true);

                    spotlight.start();
                }
            });

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(getString(R.string.key_first_run_history), false);
            editor.apply();
        }

        drawer.addDrawerListener(drawerHandler);
    }

    @Override
    public void onItemClick(View view, int position) {
    } // this method could be implemented later.

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
                rlReportAlert = new RelativeLayout(History.this);
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

                etReportContent.getText().clear();
                etReportContent.setHeight(RelativeLayout.LayoutParams.MATCH_PARENT);
                etReportContent.setHint(getString(R.string.report_hint));
                etReportContent.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                etReportContent.setGravity(Gravity.TOP);
                etReportContent.addTextChangedListener(afterTextChangedListener);

                rlReportAlert.addView(etReportContent, layoutParams);

                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(History.this)
                        .setTitle(getString(R.string.report_title))
                        .setView(rlReportAlert)
                        .setPositiveButton(getString(R.string.action_send), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                new ReportWorker(History.this, etReportContent.getText().toString()).execute();
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

    private static class HistoryNetworkWorker extends ActivityNetworkWorker {
        private WeakReference<History> historyActivity;

        private static final String HISTORY_ERROR = "1";
        private static final String HISTORY_NOT_ALLOWED = "2";
        private static final String HISTORY_EMPTY_RESULT = "3";

        HistoryNetworkWorker(History historyActivity) {
            this.historyActivity = new WeakReference<>(historyActivity);

            setTargetActivity(historyActivity);

            History reference = this.historyActivity.get();

            if (reference != null) {
                setRequest(reference.getString(R.string.request_getUserHistory));
            }
        }

        private void showNoHistory() {
            Log.d(TAG, "doInBackground: no ongoing orders found.");
            final History reference = historyActivity.get();

            if (historyActivity == null) {
                Log.d(TAG, "doInBackground: showNoOrders: failed to get a reference.");
                return;
            }

            reference.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ImageView icNoHistory = reference.findViewById(R.id.icEmptyHistory);
                    TextView tvNoHistory = reference.findViewById(R.id.tvEmptyHistory);
                    RecyclerView recyclerView = reference.findViewById(R.id.rvHistory);
                    ImageView icError = reference.findViewById(R.id.icError);
                    TextView tvError = reference.findViewById(R.id.tvError);
                    TextView tvErrorExtra = reference.findViewById(R.id.tvErrorExtra);

                    icNoHistory.setVisibility(View.VISIBLE);
                    tvNoHistory.setVisibility(View.VISIBLE);
                    icError.setVisibility(View.GONE);
                    tvError.setVisibility(View.GONE);
                    tvErrorExtra.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.GONE);
                }
            });
        }

        @Override
        protected void handleOutput(String serverOutput) {
            final History reference = historyActivity.get();

            if (historyActivity == null) {
                return;
            }

            switch (serverOutput) {
                case HISTORY_ERROR:
                    showInternalError(reference.getString(R.string.internal_error), reference);
                    break;
                case HISTORY_NOT_ALLOWED:
                    showInternalError(reference.getString(R.string.not_allowed_error), reference);
                    break;
                case HISTORY_EMPTY_RESULT:
                    showNoHistory();
                    break;
                default:
                    try {
                        final JSONArray jsonArray = new JSONArray(serverOutput);

                        if (jsonArray.length() > 0) {
                            Log.d(TAG, "doInBackground: jsonArray: " + jsonArray.toString());

                            reference.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    reference.historyAdapter.setNewData(jsonArray);

                                    ImageView icNoProducts = reference.findViewById(R.id.icEmptyHistory);
                                    TextView tvNoProducts = reference.findViewById(R.id.tvEmptyHistory);
                                    RecyclerView recyclerView = reference.findViewById(R.id.rvHistory);
                                    ImageView icError = reference.findViewById(R.id.icError);
                                    TextView tvError = reference.findViewById(R.id.tvError);

                                    icNoProducts.setVisibility(View.GONE);
                                    tvNoProducts.setVisibility(View.GONE);
                                    icError.setVisibility(View.GONE);
                                    tvError.setVisibility(View.GONE);
                                    recyclerView.setVisibility(View.VISIBLE);
                                }
                            });
                        } else {
                            showNoHistory();
                        }
                    } catch (JSONException jsonException) {
                        showInternalError(reference.getString(R.string.internal_error), reference);
                    }
            }
        }

        @Override
        protected void showInternalError(final String message, final Activity reference) {
            Log.d(TAG, "doInBackground: showInternalError: failed to get a reference.");

            reference.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ImageView icNoHistory = reference.findViewById(R.id.icEmptyHistory);
                    TextView tvNoHistory = reference.findViewById(R.id.tvEmptyHistory);
                    RecyclerView recyclerView = reference.findViewById(R.id.rvHistory);
                    ImageView icError = reference.findViewById(R.id.icError);
                    TextView tvError = reference.findViewById(R.id.tvError);
                    TextView tvErrorExtra = reference.findViewById(R.id.tvErrorExtra);

                    icNoHistory.setVisibility(View.GONE);
                    tvNoHistory.setVisibility(View.GONE);
                    icError.setVisibility(View.VISIBLE);
                    tvError.setVisibility(View.VISIBLE);
                    tvErrorExtra.setVisibility(View.VISIBLE);
                    tvErrorExtra.setText(message);
                    recyclerView.setVisibility(View.GONE);
                }
            });
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            History reference = historyActivity.get();

            if (historyActivity != null) {
                SwipeRefreshLayout swipeRefreshLayout = reference.findViewById(R.id.swipeRefreshLayout);
                swipeRefreshLayout.setRefreshing(false);
            }
        }
    }
}
