package com.hayday.wheatharvester;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 501;
    private static final String HAY_DAY_PACKAGE = "com.supercell.hayday";
    private TextView status;
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
        info.setText("اضغط بدء التسجيل ووافق على مشاركة الشاشة. بعدها سيفتح Hay Day تلقائياً وتستمر مراقبة الشاشة بالخلفية.");
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

        Button stop = new Button(this);
        stop.setText("إيقاف المراقبة");
        LinearLayout.LayoutParams sp = params(); sp.topMargin = 8;
        root.addView(stop, sp);
        stop.setOnClickListener(v -> {
            Intent service = new Intent(this, ScreenMonitorService.class);
            service.setAction(ScreenMonitorService.ACTION_STOP);
            startService(service);
            status.setText("تم طلب إيقاف المراقبة.");
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

    private void requestScreenCapture() {
        try {
            MediaProjectionManager manager =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (manager == null) {
                showError("خدمة تسجيل الشاشة غير متاحة على الجهاز.");
                return;
            }
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

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private void registerStatusReceiver() {
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (ScreenMonitorService.ACTION_STATUS.equals(intent.getAction()) && status != null) {
                    status.setText(intent.getStringExtra("message"));
                }
            }
        };
        IntentFilter filter = new IntentFilter(ScreenMonitorService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) {
            status.setText("لم تتم الموافقة على تسجيل الشاشة. اضغط بدء التسجيل وحاول مرة أخرى.");
            return;
        }

        try {
            Intent service = new Intent(this, ScreenMonitorService.class);
            service.putExtra("resultCode", resultCode);
            service.putExtra("resultData", data);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);

            status.setText("تم تشغيل التسجيل. جارٍ فتح Hay Day...");
            openHayDay();
        } catch (Exception e) {
            showError("تعذر تشغيل خدمة التسجيل: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    @Override protected void onDestroy() {
        if (receiver != null) unregisterReceiver(receiver);
        super.onDestroy();
    }
}
