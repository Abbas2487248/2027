package com.hayday.wheatharvester;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingControlsService extends Service {
    private WindowManager windowManager;
    private View panel;
    private Button startButton;
    private TextView resultText;
    private BroadcastReceiver statusReceiver;
    private static final String PREFS = "wheat_bot";
    private static final String AUTO_HARVEST = "auto_harvest";

    @Override public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildPanel();
        registerStatusReceiver();
    }

    private void buildPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(12, 10, 12, 10);
        box.setBackgroundColor(0xee203820);

        TextView title = new TextView(this);
        title.setText("بوت القمح  ⋮ اسحب من هنا");
        title.setTextColor(0xffffffff);
        title.setTextSize(14);
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));

        resultText = new TextView(this);
        resultText.setText("بانتظار نتيجة التعرف...");
        resultText.setTextColor(0xffffffff);
        resultText.setTextSize(12);
        resultText.setPadding(4, 8, 4, 8);
        resultText.setMaxLines(7);
        box.addView(resultText, new LinearLayout.LayoutParams(260, -2));

        startButton = new Button(this);
        startButton.setText("بدء البوت");
        startButton.setTextSize(12);
        box.addView(startButton, new LinearLayout.LayoutParams(-1, -2));
        startButton.setOnClickListener(v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(AUTO_HARVEST, true).apply();
            updateButtons();
            resultText.setText("تم تشغيل وضع الأتمتة. ستظهر هنا نتائج التعرف المباشر.");
        });

        Button stop = new Button(this);
        stop.setText("إيقاف البوت");
        stop.setTextSize(12);
        box.addView(stop, new LinearLayout.LayoutParams(-1, -2));
        stop.setOnClickListener(v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(AUTO_HARVEST, false).apply();
            updateButtons();
            resultText.setText("تم إيقاف البوت. مراقبة الشاشة مستمرة.");
        });

        Button hide = new Button(this);
        hide.setText("إخفاء اللوحة");
        hide.setTextSize(12);
        box.addView(hide, new LinearLayout.LayoutParams(-1, -2));
        hide.setOnClickListener(v -> stopSelf());

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 18;
        params.y = 180;

        title.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float downX, downY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    startX = params.x; startY = params.y;
                    downX = e.getRawX(); downY = e.getRawY(); return true;
                }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    params.x = startX + (int) (downX - e.getRawX());
                    params.y = startY + (int) (e.getRawY() - downY);
                    if (panel != null) windowManager.updateViewLayout(panel, params);
                    return true;
                }
                return e.getAction() == MotionEvent.ACTION_UP;
            }
        });
        panel = box;
        windowManager.addView(panel, params);
        updateButtons();
    }

    private void updateButtons() {
        if (startButton == null) return;
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(AUTO_HARVEST, false);
        startButton.setText(enabled ? "البوت يعمل" : "بدء البوت");
        startButton.setEnabled(!enabled);
    }

    private void registerStatusReceiver() {
        statusReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (ScreenMonitorService.ACTION_STATUS.equals(intent.getAction()) && resultText != null) {
                    String message = intent.getStringExtra("message");
                    if (message != null) resultText.setText(message);
                }
            }
        };
        IntentFilter filter = new IntentFilter(ScreenMonitorService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(statusReceiver, filter);
    }

    @Override public void onDestroy() {
        if (statusReceiver != null) {
            try { unregisterReceiver(statusReceiver); } catch (Exception ignored) { }
            statusReceiver = null;
        }
        if (windowManager != null && panel != null) windowManager.removeView(panel);
        panel = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}