package com.example.aiphotostudio;

import android.app.Activity;
import android.os.Bundle;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.widget.*;

import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 100;
    private ImageView imageView;
    private Bitmap originalBitmap, editedBitmap;
    private SeekBar brightnessBar, contrastBar;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);
        root.setBackgroundColor(Color.WHITE);

        TextView heading = new TextView(this);
        heading.setText("AI PHOTO STUDIO");
        heading.setTextSize(25);
        heading.setTextColor(Color.BLACK);
        heading.setGravity(android.view.Gravity.CENTER);
        root.addView(heading);

        imageView = new ImageView(this);
        imageView.setBackgroundColor(0xFFEAEAEA);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(-1, 0, 1);
        imageParams.setMargins(0, 15, 0, 15);
        root.addView(imageView, imageParams);

        addButton("Open Photo", v -> openPhoto());

        TextView brightnessLabel = new TextView(this);
        brightnessLabel.setText("Brightness");
        root.addView(brightnessLabel);

        brightnessBar = new SeekBar(this);
        brightnessBar.setMax(200);
        brightnessBar.setProgress(100);
        root.addView(brightnessBar);

        TextView contrastLabel = new TextView(this);
        contrastLabel.setText("Contrast");
        root.addView(contrastLabel);

        contrastBar = new SeekBar(this);
        contrastBar.setMax(200);
        contrastBar.setProgress(100);
        root.addView(contrastBar);

        brightnessBar.setOnSeekBarChangeListener(seekListener());
        contrastBar.setOnSeekBarChangeListener(seekListener());

        addButton("Black & White", v -> applyFilter(false));
        addButton("Sepia Filter", v -> applyFilter(true));
        addButton("Reset Image", v -> resetImage());
        addButton("Save Image", v -> saveImage());

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(root);
        setContentView(scrollView);
    }

    private void addButton(String title, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(title);
        button.setOnClickListener(listener);
        root.addView(button);
    }

    private void openPhoto() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            try {
                Uri uri = data.getData();
                try (java.io.InputStream input =
                             getContentResolver().openInputStream(uri)) {
                    originalBitmap = BitmapFactory.decodeStream(input);
                }
                if (originalBitmap != null) resetImage();
                else showMessage("Unsupported image");
            } catch (Exception e) {
                showMessage("Unable to open image");
            }
        }
    }

    private SeekBar.OnSeekBarChangeListener seekListener() {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                adjustImage();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        };
    }

    private void adjustImage() {
        if (originalBitmap == null) return;
        float brightness = brightnessBar.getProgress() - 100;
        float contrast = contrastBar.getProgress() / 100f;
        ColorMatrix matrix = new ColorMatrix(new float[] {
                contrast, 0, 0, 0, brightness,
                0, contrast, 0, 0, brightness,
                0, 0, contrast, 0, brightness,
                0, 0, 0, 1, 0
        });
        editedBitmap = render(matrix);
        imageView.setImageBitmap(editedBitmap);
    }

    private Bitmap render(ColorMatrix matrix) {
        Bitmap result = Bitmap.createBitmap(
                originalBitmap.getWidth(), originalBitmap.getHeight(),
                Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(originalBitmap, 0, 0, paint);
        return result;
    }

    private void applyFilter(boolean sepia) {
        if (originalBitmap == null) {
            showMessage("Select a photo first");
            return;
        }
        ColorMatrix matrix = new ColorMatrix();
        if (!sepia) {
            matrix.setSaturation(0);
        } else {
            matrix.set(new float[] {
                    0.393f, 0.769f, 0.189f, 0, 0,
                    0.349f, 0.686f, 0.168f, 0, 0,
                    0.272f, 0.534f, 0.131f, 0, 0,
                    0, 0, 0, 1, 0
            });
        }
        editedBitmap = render(matrix);
        imageView.setImageBitmap(editedBitmap);
    }

    private void resetImage() {
        if (originalBitmap == null) return;
        brightnessBar.setProgress(100);
        contrastBar.setProgress(100);
        editedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        imageView.setImageBitmap(editedBitmap);
    }

    private void saveImage() {
        if (editedBitmap == null) {
            showMessage("Select a photo first");
            return;
        }
        Uri savedUri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,
                    "AI_Photo_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/AI Photo Studio");

            savedUri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (savedUri == null) throw new Exception("Could not create image");

            try (OutputStream output = getContentResolver().openOutputStream(savedUri)) {
                if (output == null) throw new Exception("Could not open output");
                boolean success = editedBitmap.compress(
                        Bitmap.CompressFormat.JPEG, 95, output);
                if (!success) throw new Exception("Image encoding failed");
            }
            showMessage("Image saved in Pictures/AI Photo Studio");
        } catch (Exception e) {
            if (savedUri != null) {
                getContentResolver().delete(savedUri, null, null);
            }
            showMessage("Error saving image: " + e.getMessage());
        }
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
