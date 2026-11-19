package com.example.photoai;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OpenCvPhotoAIEngine
 *
 * Lightweight Android Bitmap-based image processing engine.
 *
 * This implementation does NOT require OpenCV.
 * It uses Android graphics APIs only.
 *
 * Features:
 * - Image enhancement
 * - Brightness adjustment
 * - Contrast adjustment
 * - Saturation
 * - Warm filter
 * - Cool filter
 * - Black and white filter
 * - Natural smoothing approximation
 * - Natural slimming approximation
 *
 * Note:
 * This class provides basic image effects. Accurate background person removal,
 * face detection, realistic smile editing, and AI body reshaping require
 * dedicated ML models.
 */
public class OpenCvPhotoAIEngine {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface BitmapCallback {
        void onSuccess(Bitmap bitmap);
        void onError(Exception error);
    }

    // ---------------------------------------------------------
    // 1. COPY BITMAP
    // ---------------------------------------------------------

    public Bitmap copyBitmap(Bitmap source) {

        if (source == null) {
            return null;
        }

        return source.copy(Bitmap.Config.ARGB_8888, true);
    }

    // ---------------------------------------------------------
    // 2. AUTO ENHANCE
    // ---------------------------------------------------------

    public Bitmap autoEnhance(Bitmap source) {

        if (source == null) {
            return null;
        }

        Bitmap output = copyBitmap(source);

        if (output == null) {
            return null;
        }

        Bitmap adjusted = adjustBrightnessContrast(
                output,
                1.06f,
                5f
        );

        Bitmap saturated = adjustSaturation(
                adjusted,
                1.12f
        );

        return saturated;
    }

    // ---------------------------------------------------------
    // 3. BRIGHTNESS AND CONTRAST
    // ---------------------------------------------------------

    public Bitmap adjustBrightnessContrast(
            Bitmap source,
            float contrast,
            float brightness
    ) {

        if (source == null) {
            return null;
        }

        Bitmap output = Bitmap.createBitmap(
                source.getWidth(),
                source.getHeight(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ColorMatrix matrix = new ColorMatrix();

        matrix.set(new float[]{
                contrast, 0, 0, 0, brightness,
                0, contrast, 0, 0, brightness,
                0, 0, contrast, 0, brightness,
                0, 0, 0, 1, 0
        });

        paint.setColorFilter(
                new ColorMatrixColorFilter(matrix)
        );

        canvas.drawBitmap(source, 0, 0, paint);

        return output;
    }

    // ---------------------------------------------------------
    // 4. SATURATION
    // ---------------------------------------------------------

    public Bitmap adjustSaturation(
            Bitmap source,
            float saturation
    ) {

        if (source == null) {
            return null;
        }

        Bitmap output = Bitmap.createBitmap(
                source.getWidth(),
                source.getHeight(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ColorMatrix matrix = new ColorMatrix();

        matrix.setSaturation(saturation);

        paint.setColorFilter(
                new ColorMatrixColorFilter(matrix)
        );

        canvas.drawBitmap(source, 0, 0, paint);

        return output;
    }

    // ---------------------------------------------------------
    // 5. NATURAL SKIN SMOOTHING APPROXIMATION
    // ---------------------------------------------------------

    public Bitmap smoothImage(Bitmap source, float intensity) {

        if (source == null) {
            return null;
        }

        intensity = clamp(intensity, 0f, 1f);

        Bitmap output = copyBitmap(source);

        if (intensity <= 0f) {
            return output;
        }

        int width = source.getWidth();
        int height = source.getHeight();

        int[] original = new int[width * height];
        int[] result = new int[width * height];

        source.getPixels(
                original,
                0,
                width,
                0,
                0,
                width,
                height
        );

        System.arraycopy(original, 0, result, 0, original.length);

        int radius = 1;

        for (int y = radius; y < height - radius; y++) {

            for (int x = radius; x < width - radius; x++) {

                int index = y * width + x;

                int center = original[index];

                int centerR = Color.red(center);
                int centerG = Color.green(center);
                int centerB = Color.blue(center);

                int sumR = 0;
                int sumG = 0;
                int sumB = 0;
                int count = 0;

                for (int ky = -radius; ky <= radius; ky++) {

                    for (int kx = -radius; kx <= radius; kx++) {

                        int neighborIndex =
                                (y + ky) * width + (x + kx);

                        int color = original[neighborIndex];

                        sumR += Color.red(color);
                        sumG += Color.green(color);
                        sumB += Color.blue(color);

                        count++;
                    }
                }

                int blurR = sumR / count;
                int blurG = sumG / count;
                int blurB = sumB / count;

                int newR = blend(centerR, blurR, intensity * 0.25f);
                int newG = blend(centerG, blurG, intensity * 0.25f);
                int newB = blend(centerB, blurB, intensity * 0.25f);

                result[index] = Color.argb(
                        Color.alpha(center),
                        newR,
                        newG,
                        newB
                );
            }
        }

        output.setPixels(
                result,
                0,
                width,
                0,
                0,
                width,
                height
        );

        return output;
    }

    // ---------------------------------------------------------
    // 6. NATURAL SLIMMING APPROXIMATION
    // ---------------------------------------------------------

    /**
     * Basic horizontal warp approximation.
     *
     * This is not AI body detection. It applies a mild central horizontal
     * compression while preserving image dimensions.
     *
     * A true slimming feature needs person segmentation and pose estimation.
     */
    public Bitmap slimPerson(
            Bitmap source,
            float intensity
    ) {

        if (source == null) {
            return null;
        }

        intensity = clamp(intensity, 0f, 0.25f);

        if (intensity == 0f) {
            return copyBitmap(source);
        }

        int width = source.getWidth();
        int height = source.getHeight();

        Bitmap output = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
        );

        int[] sourcePixels = new int[width * height];
        int[] destinationPixels = new int[width * height];

        source.getPixels(
                sourcePixels,
                0,
                width,
                0,
                0,
                width,
                height
        );

        float centerX = width / 2f;

        for (int y = 0; y < height; y++) {

            for (int x = 0; x < width; x++) {

                float normalizedX =
                        (x - centerX) / Math.max(centerX, 1f);

                float edgeWeight =
                        1f - Math.abs(normalizedX);

                edgeWeight = Math.max(0f, edgeWeight);

                float localStrength =
                        intensity * edgeWeight;

                float sourceX =
                        centerX + (x - centerX) *
                        (1f + localStrength);

                int sx = Math.round(sourceX);

                sx = Math.max(0, Math.min(width - 1, sx));

                int pixel =
                        sourcePixels[y * width + sx];

                destinationPixels[y * width + x] = pixel;
            }
        }

        output.setPixels(
                destinationPixels,
                0,
                width,
                0,
                0,
                width,
                height
        );

        return output;
    }

    // ---------------------------------------------------------
    // 7. BLACK AND WHITE
    // ---------------------------------------------------------

    public Bitmap blackAndWhite(Bitmap source) {

        if (source == null) {
            return null;
        }

        Bitmap output = Bitmap.createBitmap(
                source.getWidth(),
                source.getHeight(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ColorMatrix matrix = new ColorMatrix();

        matrix.setSaturation(0f);

        paint.setColorFilter(
                new ColorMatrixColorFilter(matrix)
        );

        canvas.drawBitmap(source, 0, 0, paint);

        return output;
    }

    // ---------------------------------------------------------
    // 8. WARM FILTER
    // ---------------------------------------------------------

    public Bitmap warmFilter(Bitmap source) {

        if (source == null) {
            return null;
        }

        Bitmap output = Bitmap.createBitmap(
                source.getWidth(),
                source.getHeight(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ColorMatrix matrix = new ColorMatrix();

        matrix.set(new float[]{
                1.08f, 0, 0, 0, 8,
                0, 1.02f, 0, 0, 3,
                0, 0, 0.94f, 0, 0,
                0, 0, 0, 1, 0
        });

        paint.setColorFilter(
                new ColorMatrixColorFilter(matrix)
        );

        canvas.drawBitmap(source, 0, 0, paint);

        return output;
    }

    // ---------------------------------------------------------
    // 9. COOL FILTER
    // ---------------------------------------------------------

    public Bitmap coolFilter(Bitmap source) {

        if (source == null) {
            return null;
        }

        Bitmap output = Bitmap.createBitmap(
                source.getWidth(),
                source.getHeight(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ColorMatrix matrix = new ColorMatrix();

        matrix.set(new float[]{
                0.95f, 0, 0, 0, 0,
                0, 1.00f, 0, 0, 0,
                0, 0, 1.08f, 0, 8,
                0, 0, 0, 1, 0
        });

        paint.setColorFilter(
                new ColorMatrixColorFilter(matrix)
        );

        canvas.drawBitmap(source, 0, 0, paint);

        return output;
    }

    // ---------------------------------------------------------
    // 10. SEPIA FILTER
    // ---------------------------------------------------------

    public Bitmap sepiaFilter(Bitmap source) {

        if (source == null) {
            return null;
        }

        Bitmap output = Bitmap.createBitmap(
                source.getWidth(),
                source.getHeight(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ColorMatrix matrix = new ColorMatrix();

        matrix.set(new float[]{
                0.393f, 0.769f, 0.189f, 0, 0,
                0.349f, 0.686f, 0.168f, 0, 0,
                0.272f, 0.534f, 0.131f, 0, 0,
                0, 0, 0, 1, 0
        });

        paint.setColorFilter(
                new ColorMatrixColorFilter(matrix)
        );

        canvas.drawBitmap(source, 0, 0, paint);

        return output;
    }

    // ---------------------------------------------------------
    // 11. AUTO COLOR
    // ---------------------------------------------------------

    public Bitmap autoColor(Bitmap source) {

        if (source == null) {
            return null;
        }

        Bitmap first = adjustBrightnessContrast(
                source,
                1.08f,
                4f
        );

        return adjustSaturation(first, 1.10f);
    }

    // ---------------------------------------------------------
    // 12. VIGNETTE
    // ---------------------------------------------------------

    public Bitmap vignette(Bitmap source, float intensity) {

        if (source == null) {
            return null;
        }

        intensity = clamp(intensity, 0f, 1f);

        Bitmap output = copyBitmap(source);

        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        int width = source.getWidth();
        int height = source.getHeight();

        float centerX = width / 2f;
        float centerY = height / 2f;

        float radius = Math.max(width, height) * 0.75f;

        android.graphics.RadialGradient gradient =
                new android.graphics.RadialGradient(
                        centerX,
                        centerY,
                        radius,
                        new int[]{
                                Color.TRANSPARENT,
                                Color.argb(
                                        (int) (150 * intensity),
                                        0,
                                        0,
                                        0
                                )
                        },
                        new float[]{0.45f, 1f},
                        android.graphics.Shader.TileMode.CLAMP
                );

        paint.setShader(gradient);

        canvas.drawRect(
                0,
                0,
                width,
                height,
                paint
        );

        return output;
    }

    // ---------------------------------------------------------
    // 13. ASYNC AUTO ENHANCE
    // ---------------------------------------------------------

    public void autoEnhanceAsync(
            Bitmap source,
            BitmapCallback callback
    ) {

        executor.execute(() -> {

            try {

                Bitmap result = autoEnhance(source);

                if (callback != null) {

                    callback.onSuccess(result);
                }

            } catch (Exception e) {

                if (callback != null) {

                    callback.onError(e);
                }
            }
        });
    }

    // ---------------------------------------------------------
    // 14. ASYNC SLIMMING
    // ---------------------------------------------------------

    public void slimPersonAsync(
            Bitmap source,
            float intensity,
            BitmapCallback callback
    ) {

        executor.execute(() -> {

            try {

                Bitmap result = slimPerson(
                        source,
                        intensity
                );

                if (callback != null) {

                    callback.onSuccess(result);
                }

            } catch (Exception e) {

                if (callback != null) {

                    callback.onError(e);
                }
            }
        });
    }

    // ---------------------------------------------------------
    // 15. ASYNC SMOOTHING
    // ---------------------------------------------------------

    public void smoothAsync(
            Bitmap source,
            float intensity,
            BitmapCallback callback
    ) {

        executor.execute(() -> {

            try {

                Bitmap result = smoothImage(
                        source,
                        intensity
                );

                if (callback != null) {

                    callback.onSuccess(result);
                }

            } catch (Exception e) {

                if (callback != null) {

                    callback.onError(e);
                }
            }
        });
    }

    // ---------------------------------------------------------
    // 16. RELEASE
    // ---------------------------------------------------------

    public void shutdown() {

        executor.shutdown();
    }

    // ---------------------------------------------------------
    // 17. UTILITIES
    // ---------------------------------------------------------

    private int blend(
            int original,
            int processed,
            float amount
    ) {

        amount = clamp(amount, 0f, 1f);

        return Math.round(
                original * (1f - amount) +
                        processed * amount
        );
    }

    private float clamp(
            float value,
            float min,
            float max
    ) {

        return Math.max(
                min,
                Math.min(max, value)
        );
    }
}
