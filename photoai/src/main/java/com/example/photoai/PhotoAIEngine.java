package com.example.photoai;

import android.graphics.Bitmap;

/**
 * Public API for PhotoAI Engine.
 * All image processing is performed on the device. No OpenAI key or paid API.
 */
public interface PhotoAIEngine {
    interface BitmapCallback {
        void onSuccess(Bitmap bitmap);
        void onError(Exception error);
    }

    /**
     * Removes the most prominent person detected by ML Kit segmentation.
     * Inpainting quality depends on how much background is visible around the person.
     */
    void removeForegroundPerson(Bitmap source, BitmapCallback callback);

    /**
     * Applies a conservative horizontal slimming warp to the segmented person.
     * intensity must be between 0.05f and 0.18f.
     */
    void slimPerson(Bitmap source, float intensity, BitmapCallback callback);

    /** Releases ML Kit resources. */
    void close();
}
