package com.hayday.wheatharvester;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
        info.setText("التعرّف المباشر على الشاشة\nاضغط بدء المراقبة ثم وافق على مشاركة الشاشة. سيحلل التطبيق الإطارات دورياً ويعرض مواقع المناطق الصفراء المرشحة. لا ينفذ لمساً أو حصاداً تلقائياً في هذه النسخة.");
        info.setTextSize(16);
        info.setTextColor(0xff333333);
        info.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ip = params(); ip.topMargin = 24;
        root.addView(info, ip);

        Button start = new Button(this);
        start.setText("بدء التعرف المباشر");
        LinearLayout.LayoutParams bp = params(); bp.topMargin = 24;
        root.addView(start, bp);
        start.setOnClickListener(v -> {
            try {
                MediaProjectionManager manager =
                        (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                if (manager == null) {
                    status.setText("تعذر تشغيل تسجيل الشاشة: خدمة النظام غير متاحة.");
                    Toast.makeText(this, "خدمة تسجيل الشاشة غير متاحة على الجهاز", Toast.LENGTH_LONG).show();
                    return;
                }
                Intent captureIntent = manager.createScreenCaptureIntent();
                if (captureIntent == null) {
                    status.setText("تعذر إنشاء نافذة السماح بتسجيل الشاشة.");
                    Toast.makeText(this, "لم يتمكن النظام من إنشاء طلب تسجيل الشاشة", Toast.LENGTH_LONG).show();
                    return;
                }
                status.setText("جارٍ فتح نافذة السماح بتسجيل الشاشة...");
                startActivityForResult(captureIntent, REQUEST_CAPTURE);
            } catch (Exception e) {
                String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                status.setText("تعذر بدء تسجيل الشاشة: " + reason);
                Toast.makeText(this, "خطأ: " + reason, Toast.LENGTH_LONG).show();
            }
        });

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

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private void registerStatusReceiver() {
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (ScreenMonitorService.ACTION_STATUS.equals(intent.getAction())) {
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
        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent service = new Intent(this, ScreenMonitorService.class);
                service.putExtra("resultCode", resultCode);
                service.putExtra("resultData", data);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
                else startService(service);
                status.setText("بدأت المراقبة. ارجع إلى Hay Day واترك التطبيق يعمل.");
            } else status.setText("لم تتم الموافقة على مشاركة الشاشة.");
        }
    }

    @Override protected void onDestroy() {
        if (receiver != null) unregisterReceiver(receiver);
        super.onDestroy();
    }
}
