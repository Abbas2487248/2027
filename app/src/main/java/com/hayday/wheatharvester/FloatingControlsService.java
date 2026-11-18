package com.hayday.wheatharvester;

import android.app.Service;
import android.content.Intent;
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
    private Button toggleButton;
    private static final String PREFS = "wheat_bot";
    private static final String AUTO_HARVEST = "auto_harvest";

    @Override public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildPanel();
    }

    private void buildPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(10, 10, 10, 10);
        box.setBackgroundColor(0xee263b25);

        TextView title = new TextView(this);
        title.setText("بوت القمح");
        title.setTextColor(0xffffffff);
        title.setTextSize(14);
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));

        toggleButton = new Button(this);
        updateToggleLabel();
        toggleButton.setTextSize(12);
        box.addView(toggleButton, new LinearLayout.LayoutParams(-1, -2));
        toggleButton.setOnClickListener(v -> {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            p.edit().putBoolean(AUTO_HARVEST, !p.getBoolean(AUTO_HARVEST, false)).apply();
            updateToggleLabel();
        });

        Button stop = new Button(this);
        stop.setText("إيقاف المراقبة");
        stop.setTextSize(12);
        box.addView(stop, new LinearLayout.LayoutParams(-1, -2));
        stop.setOnClickListener(v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(AUTO_HARVEST, false).apply();
            Intent i = new Intent(this, ScreenMonitorService.class);
            i.setAction(ScreenMonitorService.ACTION_STOP);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            updateToggleLabel();
        });

        Button hide = new Button(this);
        hide.setText("إخفاء الأزرار");
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
                    windowManager.updateViewLayout(panel, params); return true;
                }
                return e.getAction() == MotionEvent.ACTION_UP;
            }
        });
        panel = box;
        windowManager.addView(panel, params);
    }

    private void updateToggleLabel() {
        if (toggleButton == null) return;
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(AUTO_HARVEST, false);
        toggleButton.setText(enabled ? "إيقاف النقر التجريبي" : "تشغيل النقر التجريبي");
    }

    @Override public void onDestroy() {
        if (windowManager != null && panel != null) windowManager.removeView(panel);
        panel = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
