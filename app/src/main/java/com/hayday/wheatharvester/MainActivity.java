package com.hayday.wheatharvester;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 501;
    private static final int REQUEST_OVERLAY = 502;
    private static final String HAY_DAY_PACKAGE = "com.supercell.hayday";
    private static final String PREFS = "wheat_bot";
    private static final String AUTO_HARVEST = "auto_harvest";
    private boolean gameOpened = false;
    private TextView status;
    private Button autoButton;
    private BroadcastReceiver receiver;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(28, 36, 28, 28);
        root.setBackgroundColor(0xfff7f8f2);

        TextView title = new TextView(this);
        title.setText("بوت حصاد القمح - Hay Day");
        title.setTextSize(23);
        title.setTextColor(0xff315b2d);
        title.setGravity(Gravity.CENTER);
        root.addView(title, params());

        TextView info = new TextView(this);
        info.setText("يحلل الشاشة ويحدد مناطق القمح القوية فقط. النقر التجريبي يحتاج تفعيل خدمة إمكانية الوصول يدوياً.");
        info.setTextSize(16);
        info.setTextColor(0xff333333);
        info.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ip = params(); ip.topMargin = 24;
        root.addView(info, ip);

        Button start = new Button(this);
        start.setText("بدء التسجيل وفتح اللعبة");
        LinearLayout.LayoutParams bp = params(); bp.topMargin = 24;
        root.addView(start, bp);
        start.setOnClickListener(v -> requestScreenCapture());

        Button accessibility = new Button(this);
        accessibility.setText("فتح إعدادات إمكانية الوصول");
        LinearLayout.LayoutParams ap = params(); ap.topMargin = 8;
        root.addView(accessibility, ap);
        accessibility.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        autoButton = new Button(this);
        LinearLayout.LayoutParams abp = params(); abp.topMargin = 8;
        root.addView(autoButton, abp);
        updateAutoButton();
        autoButton.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean enabled = !prefs.getBoolean(AUTO_HARVEST, false);
            prefs.edit().putBoolean(AUTO_HARVEST, enabled).apply();
            updateAutoButton();
            status.setText(enabled
                    ? "وضع النقر التجريبي مفعّل. سيحاول نقرة واحدة على أقوى منطقة قمح مرشحة فقط."
                    : "تم إيقاف النقر التجريبي.");
        });

        Button stop = new Button(this);
        stop.setText("إيقاف المراقبة");
        LinearLayout.LayoutParams sp = params(); sp.topMargin = 8;
        root.addView(stop, sp);
        stop.setOnClickListener(v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(AUTO_HARVEST, false).apply();
            updateAutoButton();
            Intent service = new Intent(this, ScreenMonitorService.class);
            service.setAction(ScreenMonitorService.ACTION_STOP);
            startService(service);
            status.setText("تم إيقاف المراقبة والنقر التجريبي.");
        });

        status = new TextView(this);
        status.setText("الحالة: متوقف");
        status.setTextSize(15);
        status.setTextColor(0xff222222);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams st = params(); st.topMargin = 24;
        root.addView(status, st);
        setContentView(root);
        registerStatusReceiver();
    }

    private void updateAutoButton() {
        if (autoButton == null) return;
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(AUTO_HARVEST, false);
        autoButton.setText(enabled ? "إيقاف النقر التجريبي" : "تشغيل النقر التجريبي");
    }

    private void requestScreenCapture() {
        try {
            MediaProjectionManager manager =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (manager == null) { showError("خدمة تسجيل الشاشة غير متاحة على الجهاز."); return; }
            status.setText("بانتظار موافقتك على تسجيل الشاشة...");
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
        } catch (Exception e) {
            showError("تعذر فتح إذن تسجيل الشاشة: " + e.getMessage());
        }
    }

    private void openHayDay() {
        try {
            PackageManager pm = getPackageManager();
            Intent game = pm.getLaunchIntentForPackage(HAY_DAY_PACKAGE);
            if (game == null) {
                status.setText("بدأت مراقبة الشاشة، لكن لم أجد لعبة Hay Day. تأكد أنها مثبتة.");
                Toast.makeText(this, "لم يتم العثور على Hay Day", Toast.LENGTH_LONG).show();
                return;
            }
            game.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(game);
        } catch (Exception e) {
            status.setText("بدأ التسجيل لكن تعذر فتح اللعبة: " + e.getMessage());
        }
    }

    private void showError(String message) {
        if (status != null) status.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private LinearLayout.LayoutParams params() { return new LinearLayout.LayoutParams(-1, -2); }

    private void registerStatusReceiver() {
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (ScreenMonitorService.ACTION_STATUS.equals(intent.getAction()) && status != null) {
                    status.setText(intent.getStringExtra("message"));
                } else if (ScreenMonitorService.ACTION_READY.equals(intent.getAction())) {
                    status.setText("التسجيل شغال. جارٍ فتح Hay Day...");
                    if (!gameOpened) { gameOpened = true; openHayDay(); }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ScreenMonitorService.ACTION_STATUS);
        filter.addAction(ScreenMonitorService.ACTION_READY);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY) {
            if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) {
                requestScreenCapture();
            } else {
                status.setText("لم يتم منح إذن الظهور فوق التطبيقات.");
            }
            return;
        }
        if (requestCode != REQUEST_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) {
            status.setText("لم تتم الموافقة على تسجيل الشاشة. اضغط بدء التسجيل وحاول مرة أخرى.");
            return;
        }
        try {
            Intent service = new Intent(this, ScreenMonitorService.class);
            service.putExtra("resultCode", resultCode);
            service.putExtra("resultData", data);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
            status.setText("تم إرسال طلب التسجيل. أنتظر تأكيد تشغيل التسجيل...");
        } catch (Exception e) {
            showError("تعذر تشغيل خدمة التسجيل: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    @Override protected void onDestroy() {
        if (receiver != null) unregisterReceiver(receiver);
        super.onDestroy();
    }
}
