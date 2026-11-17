package com.hayday.wheatharvester;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(24, 36, 24, 24);
        root.setBackgroundColor(Color.rgb(248, 249, 244));

        TextView title = new TextView(this);
        title.setText("حصاد القمح الذكي");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(42, 74, 39));
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView intro = new TextView(this);
        intro.setText("المرحلة الأولى: التعرّف على القمح الناضج من صور اللعبة.\nلن ينفّذ التطبيق أي نقرات في هذه المرحلة.");
        intro.setTextSize(16);
        intro.setTextColor(Color.DKGRAY);
        intro.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        introParams.topMargin = 24;
        root.addView(intro, introParams);

        Button testButton = new Button(this);
        testButton.setText("فحص جاهزية محلّل الصور");
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = 28;
        root.addView(testButton, buttonParams);

        status = new TextView(this);
        status.setText("الحالة: جاهز لإضافة فحص الصور");
        status.setTextSize(15);
        status.setTextColor(Color.DKGRAY);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = 20;
        root.addView(status, statusParams);

        testButton.setOnClickListener(v ->
                status.setText("التطبيق الأساسي يعمل. الخطوة التالية: إدخال صورة شاشة وتجربة كشف القمح."));
        setContentView(root);
    }
}
