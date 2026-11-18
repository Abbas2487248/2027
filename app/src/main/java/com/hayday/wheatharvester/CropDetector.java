package com.hayday.wheatharvester;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Wheat-only visual template matcher.
 * Each live screen tile is compared with the user's wheat and empty-field
 * reference images using normalized color and edge-orientation histograms.
 * This is image matching, not a trained neural network.
 */
public final class CropDetector {
    public enum Kind { RIPE_WHEAT_CANDIDATE, EMPTY_OR_NON_YELLOW, UNCERTAIN }

    public static final class Region {
        public final float left, top, right, bottom;
        public final float yellowRatio;
        public final float confidence;
        public final Kind kind;

        Region(float left, float top, float right, float bottom,
               float yellowRatio, float confidence, Kind kind) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
            this.yellowRatio = yellowRatio; this.confidence = confidence; this.kind = kind;
        }
        public float centerX() { return (left + right) * 0.5f; }
        public float centerY() { return (top + bottom) * 0.5f; }
    }

    private static final int COLS = 24;
    private static final int ROWS = 14;
    private static final int COLOR_BINS = 64;
    private static final int EDGE_BINS = 8;
    private static final int FEATURE_SIZE = 24;
    private static final float MIN_WHEAT_SIMILARITY = 0.70f;
    private static final float MIN_CLASS_MARGIN = 0.08f;

    private static final class Features {
        final float[] color = new float[COLOR_BINS];
        final float[] edge = new float[EDGE_BINS];
    }

    private CropDetector() {}

    public static List<Region> analyze(Context context, Bitmap bitmap) {
        Features wheat = loadReference(context, "training/wheat_01.png");
        if (wheat == null) wheat = loadReference(context, "crops/ripe_wheat.png");
        Features empty = loadReference(context, "training/empty_01.png");
        if (empty == null) empty = loadReference(context, "crops/empty_field.png");

        if (wheat == null) return new ArrayList<>();

        float[][] scores = new float[ROWS][COLS];
        boolean[][] active = new boolean[ROWS][COLS];

        for (int row = 0; row < ROWS; row++) {
            int y0 = row * bitmap.getHeight() / ROWS;
            int y1 = Math.max(y0 + 1, (row + 1) * bitmap.getHeight() / ROWS);
            for (int col = 0; col < COLS; col++) {
                int x0 = col * bitmap.getWidth() / COLS;
                int x1 = Math.max(x0 + 1, (col + 1) * bitmap.getWidth() / COLS);
                Bitmap tile = Bitmap.createBitmap(bitmap, x0, y0, x1 - x0, y1 - y0);
                Features sample = describe(tile);
                tile.recycle();

                float wheatScore = similarity(sample, wheat);
                float emptyScore = empty == null ? 0f : similarity(sample, empty);
                float margin = wheatScore - emptyScore;
                float confidence = Math.max(0f, Math.min(1f,
                        wheatScore * 0.78f + Math.max(0f, margin) * 0.22f));
                scores[row][col] = confidence;
                active[row][col] = wheatScore >= MIN_WHEAT_SIMILARITY
                        && margin >= MIN_CLASS_MARGIN;
            }
        }

        boolean[][] seen = new boolean[ROWS][COLS];
        List<Region> regions = new ArrayList<>();
        int[] dr = {-1, 1, 0, 0}, dc = {0, 0, -1, 1};
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                if (!active[row][col] || seen[row][col]) continue;
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{row, col});
                seen[row][col] = true;
                int minR = row, maxR = row, minC = col, maxC = col, count = 0;
                float sum = 0f;
                while (!queue.isEmpty()) {
                    int[] point = queue.removeFirst();
                    int rr = point[0], cc = point[1];
                    count++;
                    sum += scores[rr][cc];
                    minR = Math.min(minR, rr); maxR = Math.max(maxR, rr);
                    minC = Math.min(minC, cc); maxC = Math.max(maxC, cc);
                    for (int k = 0; k < 4; k++) {
                        int nr = rr + dr[k], nc = cc + dc[k];
                        if (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS
                                && active[nr][nc] && !seen[nr][nc]) {
                            seen[nr][nc] = true;
                            queue.addLast(new int[]{nr, nc});
                        }
                    }
                }
                if (count < 2) continue;
                float confidence = sum / count;
                regions.add(new Region((float) minC / COLS, (float) minR / ROWS,
                        (float) (maxC + 1) / COLS, (float) (maxR + 1) / ROWS,
                        confidence, confidence, Kind.RIPE_WHEAT_CANDIDATE));
            }
        }
        regions.sort(Comparator.comparingDouble((Region r) -> r.confidence).reversed());
        return regions;
    }

    private static Features loadReference(Context context, String path) {
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream(context.getAssets().open(path));
            return bitmap == null ? null : describe(bitmap);
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            if (bitmap != null) bitmap.recycle();
        }
    }

    private static Features describe(Bitmap source) {
        Bitmap scaled = Bitmap.createScaledBitmap(source, FEATURE_SIZE, FEATURE_SIZE, true);
        Features f = new Features();
        float[] hsv = new float[3];
        for (int y = 0; y < FEATURE_SIZE; y++) {
            for (int x = 0; x < FEATURE_SIZE; x++) {
                Color.colorToHSV(scaled.getPixel(x, y), hsv);
                int hb = Math.min(3, (int) (hsv[0] / 90f));
                int sb = Math.min(3, (int) (hsv[1] * 4f));
                int vb = Math.min(3, (int) (hsv[2] * 4f));
                f.color[hb * 16 + sb * 4 + vb] += 1f;

                if (x > 0 && y > 0 && x < FEATURE_SIZE - 1 && y < FEATURE_SIZE - 1) {
                    float gx = luminance(scaled.getPixel(x + 1, y))
                            - luminance(scaled.getPixel(x - 1, y));
                    float gy = luminance(scaled.getPixel(x, y + 1))
                            - luminance(scaled.getPixel(x, y - 1));
                    float magnitude = (float) Math.sqrt(gx * gx + gy * gy);
                    if (magnitude > 0.025f) {
                        double angle = Math.atan2(gy, gx);
                        if (angle < 0) angle += Math.PI;
                        int bin = Math.min(EDGE_BINS - 1,
                                (int) (angle / Math.PI * EDGE_BINS));
                        f.edge[bin] += magnitude;
                    }
                }
            }
        }
        normalize(f.color);
        normalize(f.edge);
        if (scaled != source) scaled.recycle();
        return f;
    }

    private static float luminance(int color) {
        return (0.2126f * Color.red(color) + 0.7152f * Color.green(color)
                + 0.0722f * Color.blue(color)) / 255f;
    }

    private static void normalize(float[] values) {
        float total = 0f;
        for (float value : values) total += value;
        if (total <= 0f) return;
        for (int i = 0; i < values.length; i++) values[i] /= total;
    }

    private static float histogramIntersection(float[] a, float[] b) {
        float score = 0f;
        for (int i = 0; i < a.length; i++) score += Math.min(a[i], b[i]);
        return score;
    }

    private static float similarity(Features a, Features b) {
        return 0.72f * histogramIntersection(a.color, b.color)
                + 0.28f * histogramIntersection(a.edge, b.edge);
    }
}
