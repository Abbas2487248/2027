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
 * Conservative wheat-only detector. Color similarity is a candidate signal,
 * not proof of crop identity. Automatic actions require a high threshold and
 * an explicit user opt-in through Accessibility settings.
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
    private static final float MIN_YELLOW = 0.34f;

    private CropDetector() {}

    public static List<Region> analyze(Context context, Bitmap bitmap) {
        float ripeHue = referenceHue(context, "crops/ripe_wheat.png", 48f);
        float emptyHue = referenceHue(context, "crops/empty_field.png", 28f);
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        float[][] ratios = new float[ROWS][COLS];
        float[][] hueScores = new float[ROWS][COLS];
        boolean[][] active = new boolean[ROWS][COLS];
        float[] hsv = new float[3];

        for (int row = 0; row < ROWS; row++) {
            int y0 = row * height / ROWS, y1 = (row + 1) * height / ROWS;
            for (int col = 0; col < COLS; col++) {
                int x0 = col * width / COLS, x1 = (col + 1) * width / COLS;
                int total = 0, yellow = 0;
                float hueDistance = 0f;
                int sx = Math.max(1, (x1 - x0) / 12);
                int sy = Math.max(1, (y1 - y0) / 12);
                for (int y = y0; y < y1; y += sy) {
                    for (int x = x0; x < x1; x += sx) {
                        Color.colorToHSV(bitmap.getPixel(x, y), hsv);
                        total++;
                        if (hsv[0] >= 32f && hsv[0] <= 68f && hsv[1] >= 0.30f && hsv[2] >= 0.28f) {
                            yellow++;
                            hueDistance += Math.abs(hsv[0] - ripeHue);
                        }
                    }
                }
                float ratio = total == 0 ? 0f : (float) yellow / total;
                ratios[row][col] = ratio;
                float meanDistance = yellow == 0 ? 180f : hueDistance / yellow;
                float referenceSeparation = Math.min(1f, Math.abs(ripeHue - emptyHue) / 35f);
                hueScores[row][col] = Math.max(0f, 1f - meanDistance / 30f) * referenceSeparation;
                active[row][col] = ratio >= MIN_YELLOW;
            }
        }

        boolean[][] seen = new boolean[ROWS][COLS];
        List<Region> regions = new ArrayList<>();
        int[] dr = {-1, 1, 0, 0}, dc = {0, 0, -1, 1};
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (!active[r][c] || seen[r][c]) continue;
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{r, c}); seen[r][c] = true;
                int minR=r, maxR=r, minC=c, maxC=c, count=0;
                float sum=0f, hueSum=0f;
                while (!queue.isEmpty()) {
                    int[] p = queue.removeFirst();
                    int cr=p[0], cc=p[1]; count++;
                    sum += ratios[cr][cc]; hueSum += hueScores[cr][cc];
                    minR=Math.min(minR,cr); maxR=Math.max(maxR,cr);
                    minC=Math.min(minC,cc); maxC=Math.max(maxC,cc);
                    for (int k=0;k<4;k++) {
                        int nr=cr+dr[k], nc=cc+dc[k];
                        if (nr>=0 && nr<ROWS && nc>=0 && nc<COLS
                                && active[nr][nc] && !seen[nr][nc]) {
                            seen[nr][nc]=true; queue.addLast(new int[]{nr,nc});
                        }
                    }
                }
                if (count < 2) continue;
                float yellowRatio = sum / count;
                float hueMatch = hueSum / count;
                float confidence = Math.min(0.95f, 0.55f * yellowRatio + 0.45f * hueMatch);
                // Strict threshold: weak yellow patches stay uncertain and cannot be tapped.
                Kind kind = (yellowRatio >= 0.70f && hueMatch >= 0.70f && confidence >= 0.78f)
                        ? Kind.RIPE_WHEAT_CANDIDATE : Kind.UNCERTAIN;
                regions.add(new Region((float)minC/COLS, (float)minR/ROWS,
                        (float)(maxC+1)/COLS, (float)(maxR+1)/ROWS,
                        yellowRatio, confidence, kind));
            }
        }
        regions.sort(Comparator.comparingDouble((Region r) -> r.confidence).reversed());
        return regions;
    }

    private static float referenceHue(Context context, String path, float fallback) {
        Bitmap ref = null;
        try {
            ref = BitmapFactory.decodeStream(context.getAssets().open(path));
            if (ref == null) return fallback;
            float[] hsv = new float[3];
            float total = 0f, count = 0f;
            int sx = Math.max(1, ref.getWidth() / 32);
            int sy = Math.max(1, ref.getHeight() / 32);
            for (int y=0; y<ref.getHeight(); y+=sy) {
                for (int x=0; x<ref.getWidth(); x+=sx) {
                    Color.colorToHSV(ref.getPixel(x,y), hsv);
                    if (hsv[1] > 0.20f && hsv[2] > 0.20f) { total += hsv[0]; count++; }
                }
            }
            return count == 0 ? fallback : total / count;
        } catch (IOException | RuntimeException e) {
            return fallback;
        } finally {
            if (ref != null) ref.recycle();
        }
    }
}
