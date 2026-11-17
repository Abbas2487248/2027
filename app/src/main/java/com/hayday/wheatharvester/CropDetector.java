package com.hayday.wheatharvester;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight candidate-region detector.
 * This is a visual heuristic for prototyping, not a trained Hay Day crop model.
 */
public final class CropDetector {
    public static final class Region {
        public final float left, top, right, bottom;
        public final float yellowRatio;

        Region(float left, float top, float right, float bottom, float yellowRatio) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
            this.yellowRatio = yellowRatio;
        }
    }

    private static final int COLS = 24;
    private static final int ROWS = 14;

    private CropDetector() {}

    public static List<Region> findYellowCandidates(Bitmap bitmap) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        float[][] ratios = new float[ROWS][COLS];
        boolean[][] active = new boolean[ROWS][COLS];
        float[] hsv = new float[3];

        for (int row = 0; row < ROWS; row++) {
            int y0 = row * height / ROWS;
            int y1 = (row + 1) * height / ROWS;
            for (int col = 0; col < COLS; col++) {
                int x0 = col * width / COLS;
                int x1 = (col + 1) * width / COLS;
                int total = 0, yellow = 0;
                int sx = Math.max(1, (x1 - x0) / 10);
                int sy = Math.max(1, (y1 - y0) / 10);
                for (int y = y0; y < y1; y += sy) {
                    for (int x = x0; x < x1; x += sx) {
                        Color.colorToHSV(bitmap.getPixel(x, y), hsv);
                        total++;
                        if (hsv[0] >= 34f && hsv[0] <= 68f
                                && hsv[1] >= 0.28f && hsv[2] >= 0.30f) yellow++;
                    }
                }
                ratios[row][col] = total == 0 ? 0f : (float) yellow / total;
                active[row][col] = ratios[row][col] >= 0.30f;
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
                float sum=0f;
                while (!queue.isEmpty()) {
                    int[] p = queue.removeFirst();
                    int cr=p[0], cc=p[1]; count++; sum += ratios[cr][cc];
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
                if (count >= 3) {
                    regions.add(new Region((float)minC/COLS, (float)minR/ROWS,
                            (float)(maxC+1)/COLS, (float)(maxR+1)/ROWS, sum/count));
                }
            }
        }
        return regions;
    }
}
