package com.hayday.wheatharvester;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 41;
    private TextView status;
    private ImageView preview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(24, 28, 24, 28);
        root.setBackgroundColor(Color.rgb(248, 249, 244));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("بوت حصاد القمح - Hay Day");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(42, 74, 39));
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView intro = new TextView(this);
        intro.setText("المرحلة الحالية: تجربة تحليل صورة من اللعبة. اختر لقطة شاشة حتى نفحص الألوان ومناطق القمح المحتملة. لا توجد نقرات أو زراعة أو بيع آلي في هذه النسخة.");
        intro.setTextSize(15);
        intro.setTextColor(Color.DKGRAY);
        intro.setGravity(Gravity.CENTER);
        addTop(root, intro, 16);

        Button choose = new Button(this);
        choose.setText("اختيار صورة من الهاتف");
        addTop(root, choose, 20);
        choose.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_IMAGE);
        });

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addTop(root, preview, 18);

        status = new TextView(this);
        status.setText("الحالة: بانتظار اختيار صورة");
        status.setTextSize(15);
        status.setTextColor(Color.DKGRAY);
        status.setGravity(Gravity.CENTER);
        addTop(root, status, 18);

        TextView note = new TextView(this);
        note.setText("تنبيه: فحص اللون خطوة أولية فقط، وليس تعرّفاً مؤكداً على نوع المحصول. نحتاج صوراً فعلية متنوعة لاختبار الدقة قبل إضافة التحكم باللعبة.");
        note.setTextSize(13);
        note.setTextColor(Color.GRAY);
        addTop(root, note, 18);

        setContentView(scroll);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void addTop(LinearLayout root, android.view.View view, int margin) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = margin;
        root.addView(view, params);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                status.setText("تعذر قراءة الصورة. اختر صورة PNG أو JPG أخرى.");
                return;
            }
            preview.setImageBitmap(bitmap);
            analyzeImage(bitmap);
        } catch (Exception e) {
            status.setText("حدث خطأ أثناء فتح الصورة: " + e.getMessage());
        }
    }

    private void analyzeImage(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int stepX = Math.max(1, width / 240);
        int stepY = Math.max(1, height / 240);
        long sampled = 0;
        long yellow = 0;
        long green = 0;

        float[] hsv = new float[3];
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int pixel = bitmap.getPixel(x, y);
                Color.colorToHSV(pixel, hsv);
                float hue = hsv[0];
                float saturation = hsv[1];
                float value = hsv[2];
                if (saturation < 0.20f || value < 0.18f) continue;
                sampled++;
                if (hue >= 35f && hue <= 68f) yellow++;
                if (hue >= 70f && hue <= 165f) green++;
            }
        }

        if (sampled == 0) {
            status.setText("الصورة لا تحتوي ألواناً واضحة كافية للتحليل.");
            return;
        }
        double yellowPct = yellow * 100.0 / sampled;
        double greenPct = green * 100.0 / sampled;
        status.setText(String.format(java.util.Locale.US,
                "تم فحص الصورة: %d × %d\nبكسلات العينة: %d\nألوان صفراء: %.1f%%\nألوان خضراء: %.1f%%\n\nهذه نسب ألوان عامة وليست كشفاً مؤكداً للقمح الناضج.",
                width, height, sampled, yellowPct, greenPct));
    }
}
