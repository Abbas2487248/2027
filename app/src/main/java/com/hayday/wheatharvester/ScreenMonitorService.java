package com.hayday.wheatharvester;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.util.List;

public class ScreenMonitorService extends Service {
    public static final String ACTION_STATUS = "com.hayday.wheatharvester.STATUS";
    public static final String ACTION_STOP = "com.hayday.wheatharvester.STOP";
    public static final String ACTION_READY = "com.hayday.wheatharvester.READY";
    private static final String CHANNEL = "hayday_screen_monitor";
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler handler;
    private int width, height, density;
    private boolean running;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        thread = new HandlerThread("HayDayScreenAnalysis");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopCapture();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(7, buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(7, buildNotification());
            }

            if (projection == null && intent != null) {
                int code = intent.getIntExtra("resultCode", 0);
                Intent data = getIntentExtra(intent);
                if (code != Activity.RESULT_OK || data == null) {
                    publish("تعذر بدء التسجيل: لم تصل موافقة تسجيل الشاشة إلى الخدمة.");
                    stopForeground(true);
                    stopSelf();
                    return START_NOT_STICKY;
                }

                MediaProjectionManager manager =
                        (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                if (manager == null) throw new IllegalStateException("MediaProjectionManager غير متاح");

                projection = manager.getMediaProjection(code, data);
                if (projection == null) throw new IllegalStateException("لم يمنح النظام صلاحية التقاط الشاشة");

                projection.registerCallback(new MediaProjection.Callback() {
                    @Override public void onStop() {
                        publish("توقف تسجيل الشاشة من النظام. ارجع للتطبيق واضغط بدء التسجيل مرة أخرى.");
                        stopCapture();
                        stopForeground(true);
                        stopSelf();
                    }
                }, handler);
                startCapture();
            }
        } catch (SecurityException | IllegalStateException e) {
            publish("فشل تشغيل تسجيل الشاشة: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            stopCapture();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        } catch (Exception e) {
            publish("حدث خطأ عند بدء المراقبة: " + e.getMessage());
            stopCapture();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    private Intent getIntentExtra(Intent source) {
        if (Build.VERSION.SDK_INT >= 33) return source.getParcelableExtra("resultData", Intent.class);
        return source.getParcelableExtra("resultData");
    }

    private void startCapture() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.Display d = getDisplay();
            if (d != null) d.getRealMetrics(metrics); else wm.getDefaultDisplay().getRealMetrics(metrics);
        } else {
            wm.getDefaultDisplay().getRealMetrics(metrics);
        }
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        density = metrics.densityDpi;
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        display = projection.createVirtualDisplay("HayDayLiveAnalysis", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, handler);
        if (display == null) throw new IllegalStateException("تعذر إنشاء شاشة المراقبة الافتراضية");
        running = true;
        handler.post(analyzeFrame);
        publish("التسجيل يعمل الآن. تأكدنا من التقاط الشاشة؛ جارٍ فتح اللعبة...");
        Intent ready = new Intent(ACTION_READY);
        ready.setPackage(getPackageName());
        sendBroadcast(ready);
    }

    private final Runnable analyzeFrame = new Runnable() {
        @Override public void run() {
            if (!running || reader == null) return;
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    Bitmap bitmap = imageToBitmap(image);
                    if (bitmap != null) {
                        List<CropDetector.Region> regions = CropDetector.findYellowCandidates(bitmap);
                        StringBuilder msg = new StringBuilder();
                        msg.append("تعرف مباشر — الشاشة ").append(width).append("×").append(height)
                           .append("\nمناطق صفراء مرشحة: ").append(regions.size()).append("\n");
                        int limit = Math.min(8, regions.size());
                        for (int i = 0; i < limit; i++) {
                            CropDetector.Region r = regions.get(i);
                            msg.append(i + 1).append(") x:")
                               .append(Math.round(r.left * 100)).append("–").append(Math.round(r.right * 100))
                               .append("% y:").append(Math.round(r.top * 100)).append("–")
                               .append(Math.round(r.bottom * 100)).append("% — أصفر ")
                               .append(Math.round(r.yellowRatio * 100)).append("%\n");
                        }
                        msg.append("تنبيه: هذه مرشحات لونية وليست تأكيداً للقمح الناضج.");
                        publish(msg.toString());
                        bitmap.recycle();
                    }
                }
            } catch (Exception e) {
                publish("خطأ أثناء تحليل الشاشة: " + e.getMessage());
            } finally {
                if (image != null) image.close();
            }
            if (running) handler.postDelayed(this, 2000);
        }
    };

    private Bitmap imageToBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        Bitmap padded = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, width, height);
        padded.recycle();
        return cropped;
    }

    private void publish(String message) {
        Intent i = new Intent(ACTION_STATUS);
        i.setPackage(getPackageName());
        i.putExtra("message", message);
        sendBroadcast(i);
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(this, CHANNEL)
                    .setContentTitle("Hay Day — التعرف المباشر")
                    .setContentText("تحليل الشاشة قيد التشغيل")
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .setOngoing(true).build();
        }
        return new Notification.Builder(this).setContentTitle("Hay Day — التعرف المباشر")
                .setContentText("تحليل الشاشة قيد التشغيل")
                .setSmallIcon(android.R.drawable.ic_menu_view).build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "مراقبة شاشة Hay Day",
                    NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void stopCapture() {
        running = false;
        if (handler != null) handler.removeCallbacks(analyzeFrame);
        if (display != null) { display.release(); display = null; }
        if (reader != null) { reader.close(); reader = null; }
        if (projection != null) { projection.stop(); projection = null; }
    }

    @Override public void onDestroy() {
        stopCapture();
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
