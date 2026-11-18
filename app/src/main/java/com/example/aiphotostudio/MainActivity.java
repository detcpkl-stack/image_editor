package com.example.aiphotostudio;

import android.graphics.Bitmap;
import android.widget.Toast;

import com.example.photoai.OpenCvPhotoAIEngine;
import com.example.photoai.PhotoAIEngine;

/**
 * Copy the relevant methods into your existing MainActivity.
 * Assumes you already have a selected Bitmap named currentBitmap and an
 * ImageView named imagePreview.
 */
public class PhotoAIIntegrationExample {
    private PhotoAIEngine photoAI;

    public void initializePhotoAI() {
        photoAI = new OpenCvPhotoAIEngine();
    }

    public void removeBackgroundPerson(Bitmap currentBitmap,
                                       android.widget.ImageView imagePreview,
                                       android.content.Context context) {
        photoAI.removeForegroundPerson(currentBitmap, new PhotoAIEngine.BitmapCallback() {
            @Override public void onSuccess(Bitmap bitmap) {
                imagePreview.post(() -> imagePreview.setImageBitmap(bitmap));
            }
            @Override public void onError(Exception error) {
                imagePreview.post(() ->
                        Toast.makeText(context, error.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    public void applyNaturalSlimming(Bitmap currentBitmap,
                                     android.widget.ImageView imagePreview,
                                     android.content.Context context) {
        photoAI.slimPerson(currentBitmap, 0.08f, new PhotoAIEngine.BitmapCallback() {
            @Override public void onSuccess(Bitmap bitmap) {
                imagePreview.post(() -> imagePreview.setImageBitmap(bitmap));
            }
            @Override public void onError(Exception error) {
                imagePreview.post(() ->
                        Toast.makeText(context, error.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    public void releasePhotoAI() {
        if (photoAI != null) {
            photoAI.close();
            photoAI = null;
        }
    }
}

