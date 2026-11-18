package com.example.photoai;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.Segmentation;
import com.google.mlkit.vision.segmentation.SegmentationMask;
import com.google.mlkit.vision.segmentation.Segmenter;
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OpenCV-powered implementation.
 *
 * Person mask: ML Kit Selfie Segmentation (on-device).
 * Person removal: mask cleanup + OpenCV Telea inpainting.
 * Slimming: localized remap warp constrained by the person mask.
 *
 * Note: ML Kit selfie segmentation is designed for a prominent person/selfie.
 * For multiple people, use a multi-person instance-segmentation model and pass
 * its individual mask into a future extension of this engine.
 */
public final class OpenCvPhotoAIEngine implements PhotoAIEngine {
    private final Segmenter segmenter;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public OpenCvPhotoAIEngine() {
        if (!OpenCVLoader.initLocal()) {
            throw new IllegalStateException("OpenCV could not be initialized. Check the OpenCV dependency.");
        }
        SelfieSegmenterOptions options =
                new SelfieSegmenterOptions.Builder()
                        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                        .build();
        segmenter = Segmentation.getClient(options);
    }

    @Override
    public void removeForegroundPerson(Bitmap source, BitmapCallback callback) {
        if (source == null || callback == null) {
            if (callback != null) callback.onError(new IllegalArgumentException("Image and callback are required."));
            return;
        }
        segment(source, (mask, error) -> {
            if (error != null) { callback.onError(error); return; }
            worker.execute(() -> {
                try {
                    callback.onSuccess(inpaintPerson(source, mask));
                } catch (Exception e) {
                    callback.onError(e);
                } finally {
                    mask.recycle();
                }
            });
        });
    }

    @Override
    public void slimPerson(Bitmap source, float intensity, BitmapCallback callback) {
        if (source == null || callback == null) {
            if (callback != null) callback.onError(new IllegalArgumentException("Image and callback are required."));
            return;
        }
        if (intensity < 0.05f || intensity > 0.18f) {
            callback.onError(new IllegalArgumentException("Use a natural intensity between 0.05 and 0.18."));
            return;
        }
        segment(source, (mask, error) -> {
            if (error != null) { callback.onError(error); return; }
            worker.execute(() -> {
                try {
                    callback.onSuccess(slimWithMask(source, mask, intensity));
                } catch (Exception e) {
                    callback.onError(e);
                } finally {
                    mask.recycle();
                }
            });
        });
    }

    private interface MaskCallback {
        void complete(Bitmap mask, Exception error);
    }

    private void segment(Bitmap source, MaskCallback callback) {
        InputImage image = InputImage.fromBitmap(source, 0);
        segmenter.process(image)
                .addOnSuccessListener(result -> {
                    try {
                        callback.complete(maskToBitmap(result), null);
                    } catch (Exception e) {
                        callback.complete(null, e);
                    }
                })
                .addOnFailureListener((OnFailureListener) e -> callback.complete(null, e));
    }

    private Bitmap maskToBitmap(SegmentationMask segmentationMask) {
        int w = segmentationMask.getWidth();
        int h = segmentationMask.getHeight();
        ByteBuffer confidence = segmentationMask.getBuffer().duplicate().order(ByteOrder.nativeOrder());
        confidence.rewind();
        Bitmap mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[w * h];
        for (int i = 0; i < pixels.length; i++) {
            float value = confidence.getFloat();
            int alpha = Math.max(0, Math.min(255, Math.round(value * 255f)));
            pixels[i] = Color.argb(alpha, 255, 255, 255);
        }
        mask.setPixels(pixels, 0, w, 0, 0, w, h);
        return mask;
    }

    private Bitmap inpaintPerson(Bitmap source, Bitmap personMask) {
        Mat src = new Mat();
        Mat mask = new Mat();
        Mat resizedMask = new Mat();
        Mat binary = new Mat();
        Mat softened = new Mat();
        Mat output = new Mat();
        Mat bgr = new Mat();
        Mat inpaintedBgr = new Mat();
        try {
            Bitmap input = source.copy(Bitmap.Config.ARGB_8888, false);
            Utils.bitmapToMat(input, src);
            Imgproc.cvtColor(src, bgr, Imgproc.COLOR_RGBA2BGR);
            Bitmap maskCopy = personMask.copy(Bitmap.Config.ARGB_8888, false);
            Utils.bitmapToMat(maskCopy, mask);
            Imgproc.resize(mask, resizedMask, src.size());
            Imgproc.cvtColor(resizedMask, binary, Imgproc.COLOR_RGBA2GRAY);
            Imgproc.threshold(binary, binary, 90, 255, Imgproc.THRESH_BINARY);
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(11, 11));
            Imgproc.dilate(binary, binary, kernel);
            Imgproc.GaussianBlur(binary, softened, new Size(5, 5), 0);
            Imgproc.threshold(softened, binary, 35, 255, Imgproc.THRESH_BINARY);
            Photo.inpaint(bgr, binary, inpaintedBgr, 5.0, Photo.INPAINT_TELEA);
            Imgproc.cvtColor(inpaintedBgr, output, Imgproc.COLOR_BGR2RGBA);
            Bitmap result = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(output, result);
            return result;
        } finally {
            src.release(); mask.release(); resizedMask.release(); binary.release();
            softened.release(); output.release(); bgr.release(); inpaintedBgr.release();
        }
    }

    private Bitmap slimWithMask(Bitmap source, Bitmap personMask, float intensity) {
        Mat src = new Mat();
        Mat maskRgba = new Mat();
        Mat mask = new Mat();
        Mat resizedMask = new Mat();
        Mat mapX = new Mat(source.getHeight(), source.getWidth(), CvType.CV_32FC1);
        Mat mapY = new Mat(source.getHeight(), source.getWidth(), CvType.CV_32FC1);
        Mat warped = new Mat();
        Mat result = new Mat();
        Mat maskFloat = new Mat();
        Mat invMask = new Mat();
        Mat personOnly = new Mat();
        Mat backgroundOnly = new Mat();
        try {
            Utils.bitmapToMat(source.copy(Bitmap.Config.ARGB_8888, false), src);
            Utils.bitmapToMat(personMask.copy(Bitmap.Config.ARGB_8888, false), maskRgba);
            Imgproc.resize(maskRgba, resizedMask, src.size());
            Imgproc.cvtColor(resizedMask, mask, Imgproc.COLOR_RGBA2GRAY);
            Imgproc.GaussianBlur(mask, mask, new Size(9, 9), 0);

            // Compute the segmented person's bounding box.
            Mat hardMask = new Mat();
            Imgproc.threshold(mask, hardMask, 80, 255, Imgproc.THRESH_BINARY);
            org.opencv.core.MatOfPoint points = new org.opencv.core.MatOfPoint();
            org.opencv.core.Mat nonZero = new org.opencv.core.Mat();
            Core.findNonZero(hardMask, nonZero);
            if (nonZero.empty()) {
                hardMask.release(); points.release(); nonZero.release();
                return source.copy(Bitmap.Config.ARGB_8888, true);
            }
            org.opencv.core.Rect bounds = Imgproc.boundingRect(nonZero);
            hardMask.release(); points.release(); nonZero.release();

            int width = src.cols();
            int height = src.rows();
            float centerX = bounds.x + bounds.width / 2f;
            float radius = Math.max(1f, bounds.width / 2f);
            float amount = intensity;

            // Horizontal remap narrows the central silhouette. The feathered mask
            // limits the effect to the segmented person, reducing background distortion.
            float[] rowX = new float[width];
            float[] rowY = new float[width];
            for (int y = 0; y < height; y++) {
                float vertical = (y >= bounds.y && y <= bounds.y + bounds.height)
                        ? (float) Math.sin(Math.PI * (y - bounds.y) / Math.max(1, bounds.height))
                        : 0f;
                for (int x = 0; x < width; x++) {
                    float normalized = (x - centerX) / radius;
                    float falloff = Math.max(0f, 1f - normalized * normalized);
                    float displacement = (x - centerX) * amount * vertical * falloff;
                    rowX[x] = x + displacement;
                    rowY[x] = y;
                }
                mapX.put(y, 0, rowX);
                mapY.put(y, 0, rowY);
            }
            Imgproc.remap(src, warped, mapX, mapY, Imgproc.INTER_CUBIC,
                    Core.BORDER_REFLECT, new Scalar(0, 0, 0, 0));

            // Alpha blend warped pixels only where person confidence is high.
            Imgproc.cvtColor(mask, maskFloat, Imgproc.COLOR_GRAY2RGBA);
            maskFloat.convertTo(maskFloat, CvType.CV_32FC4, 1.0 / 255.0);
            Core.multiply(warped, maskFloat, personOnly);
            Core.subtract(Scalar.all(1.0), maskFloat, invMask);
            Core.multiply(src, invMask, backgroundOnly);
            Core.add(personOnly, backgroundOnly, result);

            Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(result, output);
            return output;
        } finally {
            src.release(); maskRgba.release(); mask.release(); resizedMask.release();
            mapX.release(); mapY.release(); warped.release(); result.release();
            maskFloat.release(); invMask.release(); personOnly.release(); backgroundOnly.release();
        }
    }

    @Override
    public void close() {
        segmenter.close();
        worker.shutdown();
    }
}
