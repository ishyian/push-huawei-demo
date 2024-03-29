package com.devinotele.huawei.exampleapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.devinotele.huawei.exampleapp.network.RetrofitHelper;
import com.devinotele.huawei.exampleapp.util.BriefTextWatcher;
import com.devinotele.huaweidevinosdk.sdk.DevinoLogsCallback;
import com.devinotele.huaweidevinosdk.sdk.DevinoSdk;
import com.huawei.agconnect.config.AGConnectServicesConfig;
import com.huawei.hms.aaid.HmsInstanceId;
import com.huawei.hms.common.ApiException;

import static com.devinotele.huawei.exampleapp.util.Util.checkEmail;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String SAVED_LOGS = "savedLogs";

    private String logs = "";
    private TextView logsView;
    private EditText email, phone;
    private Switch switchSound, switchPicture, switchDeeplink;
    private DevinoLogsCallback logsCallback;
    private Boolean logsVisible = false;
    private FrameLayout logsField;
    private ImageView logsIcon;
    private ScrollView logsScrollView;
    private RetrofitHelper retrofitHelper;

    private final int REQUEST_CODE_SEND_GEO = 11;
    private final int REQUEST_CODE_START_UPDATES = 13;
    private final int REQUEST_CODE_OTHER = 17;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        setUpViews();
        showLogs(false);

        if (savedInstanceState != null) {
            logs = savedInstanceState.getString(SAVED_LOGS);
            logsView.setText(logs);
        }

        logsCallback = message -> runOnUiThread(
                () -> {
                    logs = logs + "\n" + message.replaceAll("\"", "\'") + "\n";
                    logsView.post(() -> logsView.setText(logs));
                    scrollDown(logsScrollView);
                }
        );

        retrofitHelper = new RetrofitHelper(logsCallback);

        DevinoSdk.getInstance().requestLogs(logsCallback);
        DevinoSdk.getInstance().appStarted();

        startGeo(1);

    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
        if (!notificationsEnabled) {
            logsCallback.onMessageLogged("Notifications are disabled for this application.");
        }

    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        outState.putString(SAVED_LOGS, logs);
        super.onSaveInstanceState(outState, outPersistentState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DevinoSdk.getInstance().stop();
        Bundle state = new Bundle();
        state.putString(SAVED_LOGS, logs);
        onSaveInstanceState(state);
    }

    private void doRegistration() {
        email.setBackground(getDrawable(R.drawable.ic_border_grey));
        phone.setBackground(getDrawable(R.drawable.ic_border_grey));

        Boolean emailOk = checkEmail(email.getText().toString());
        Boolean phoneOk = PhoneNumberUtils.isGlobalPhoneNumber(phone.getText().toString()) &&
                phone.getText().length() == 12;

        if (!emailOk) email.setBackground(getDrawable(R.drawable.ic_border_red));
        if (!phoneOk) phone.setBackground(getDrawable(R.drawable.ic_border_red));

        if (phoneOk && emailOk)
            DevinoSdk.getInstance().register(this.getApplicationContext(), phone.getText().toString(), email.getText().toString());
        else
            logsCallback.onMessageLogged("Invalid phone or email");
    }

    private void startGeo(int intervalSeconds) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            DevinoSdk.getInstance().subscribeGeo(this, intervalSeconds);
            logsCallback.onMessageLogged("Subscribed geo with interval: " + intervalSeconds + " min");
        } else {
            logsCallback.onMessageLogged("GEO PERMISSION MISSING!");
            DevinoSdk.getInstance().requestGeoPermission(this, REQUEST_CODE_START_UPDATES);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_update_user:
                doRegistration();
                break;

            case R.id.send_push:
                retrofitHelper.sendPushWithDevino(
                        AGConnectServicesConfig.fromContext(MainActivity.this),
                        HmsInstanceId.getInstance(MainActivity.this),
                        switchPicture.isChecked(),
                        switchSound.isChecked(),
                        switchDeeplink.isChecked()
                );
                break;

            case R.id.send_geo:
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    showGeoPermissionDialog();
                else
                    DevinoSdk.getInstance().sendCurrentGeo();
                break;

            case R.id.clear_logs:
                logs = "\n\n    ";
                logsView.setText(logs);
                break;

            case R.id.logs_toggle_button:
                showLogs(!logsVisible);
                break;
        }
    }

    private void showGeoPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Geo Permission Missing")
                .setMessage("May Devino SDK take care of that now?")
                .setPositiveButton(android.R.string.yes, (dialog, which) ->
                        DevinoSdk.getInstance().requestGeoPermission(this, REQUEST_CODE_SEND_GEO)
                )
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CODE_START_UPDATES: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    logsCallback.onMessageLogged("GEO PERMISSION GRANTED");
                    startGeo(1);
                } else {
                    logsCallback.onMessageLogged("PERMISSION DENIED");
                }
            }
            break;
            case REQUEST_CODE_SEND_GEO: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    logsCallback.onMessageLogged("GEO PERMISSION GRANTED");
                    DevinoSdk.getInstance().sendCurrentGeo();
                } else {
                    logsCallback.onMessageLogged("PERMISSION DENIED");
                }
            }

        }
    }

    private void showLogs(Boolean show) {
        if (show) {
            logsField.setVisibility(View.VISIBLE);
            logsIcon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_arrow_drop_down));
        } else {
            logsField.setVisibility(View.GONE);
            logsIcon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_arrow_drop_up));
        }
        logsVisible = !logsVisible;
    }

    private void scrollDown(ScrollView scrollView) {
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void setUpViews() {
        logsView = findViewById(R.id.logs_view);
        TextView title = findViewById(R.id.title);
        logsScrollView = findViewById(R.id.logs_scroll_view);
        logsField = findViewById(R.id.logs_field);
        logsIcon = findViewById(R.id.logs_toggle_icon);
        email = findViewById(R.id.input_email);
        phone = findViewById(R.id.input_phone);
        switchSound = findViewById(R.id.switch_sound);
        switchPicture = findViewById(R.id.switch_picture);
        switchDeeplink = findViewById(R.id.switch_deeplink);

        ImageView clearLogs = findViewById(R.id.clear_logs);
        Button updateUser = findViewById(R.id.button_update_user);
        Button sendGeo = findViewById(R.id.send_geo);
        Button sendPush = findViewById(R.id.send_push);
        FrameLayout logsToggleButton = findViewById(R.id.logs_toggle_button);
        TextView version = findViewById(R.id.version_name);

        sendGeo.setOnClickListener(this);
        sendPush.setOnClickListener(this);
        clearLogs.setOnClickListener(this);
        updateUser.setOnClickListener(this);
        logsToggleButton.setOnClickListener(this);

        title.setOnLongClickListener(v -> {
            new Thread() {
                @Override
                public void run() {
                    try {
                        String tokenScope = "HCM";
                        String apAppId = AGConnectServicesConfig.fromContext(MainActivity.this).getString("client/app_id");
                        String token = HmsInstanceId.getInstance(MainActivity.this).getToken(apAppId, tokenScope);

                        if (!TextUtils.isEmpty(token)) {
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("token", token);
                            clipboard.setPrimaryClip(clip);
                            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "token copied", Toast.LENGTH_SHORT).show());
                        }
                    } catch (ApiException ex) {
                        try {
                            logsCallback.onMessageLogged("Push Kit Error: " + ex.getMessage());
                        } catch (Throwable error) {
                            error.printStackTrace();
                            return;
                        }
                    }

                }
            }.start();
            return false;
        });

        phone.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) phone.setSelection(phone.getText().length());
        });

        phone.addTextChangedListener(new BriefTextWatcher() {
            @SuppressLint("SetTextI18n")
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() < 3) phone.setText("+79");
                else if (!s.subSequence(0, 3).toString().equals("+79")) {
                    String prefix = s.subSequence(0, 3).toString();
                    String newValue = s.toString().replace(prefix, "+79");
                    phone.setText(newValue);
                }
                if (s.length() > 12) phone.setText(s.subSequence(0, 12));
                phone.setSelection(phone.getText().length());
            }
        });

        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version.setText(getString(R.string.version_placeholder, pInfo.versionName));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

}