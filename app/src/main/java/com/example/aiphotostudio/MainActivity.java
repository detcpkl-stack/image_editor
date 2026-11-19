
package com.example.photoai;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;

public class MainActivity extends Activity {

    private ImageView imageView;
    private TextView statusText;

    private Bitmap originalBitmap;
    private Bitmap currentBitmap;
    private Bitmap undoBitmap;

    private OpenCvPhotoAIEngine photoAI;

    private static final int PICK_IMAGE = 1001;

    private boolean processing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        photoAI = new OpenCvPhotoAIEngine();

        createUI();
    }

    // ---------------------------------------------------------
    // UI
    // ---------------------------------------------------------

    private void createUI() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(Color.rgb(17, 18, 24));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("AI PHOTO STUDIO");
        title.setTextSize(26);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(10), 0, dp(6));

        TextView subtitle = new TextView(this);
        subtitle.setText("Offline Photo Editor");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, dp(18));

        imageView = new ImageView(this);
        imageView.setBackgroundColor(Color.rgb(35, 36, 45));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setAdjustViewBounds(true);

        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(380)
                );

        imageParams.setMargins(0, 0, 0, dp(16));

        content.addView(title);
        content.addView(subtitle);
        content.addView(imageView, imageParams);

        statusText = new TextView(this);
        statusText.setText("Select a photo to begin");
        statusText.setTextColor(Color.WHITE);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(8), 0, dp(12));

        content.addView(statusText);

        // Main buttons

        addButton(content, "📷  Choose Photo", this::pickImage);
        addButton(content, "✨  Auto Enhance", this::autoEnhance);
        addButton(content, "🎨  Auto Color", this::autoColor);
        addButton(content, "💎  Smooth Skin", this::smoothSkin);
        addButton(content, "👤  Natural Slim", this::slimPerson);

        // Filters

        addSectionTitle(content, "FILTERS");

        addButton(content, "☀  Warm Filter", this::warmFilter);
        addButton(content, "❄  Cool Filter", this::coolFilter);
        addButton(content, "◼  Black & White", this::blackAndWhite);
        addButton(content, "🟤  Sepia", this::sepiaFilter);
        addButton(content, "🌑  Vignette", this::vignette);

        // Editing controls

        addSectionTitle(content, "EDIT");

        addButton(content, "↩  Undo", this::undo);
        addButton(content, "💾  Save Photo", this::saveImage);

        scrollView.addView(content);

        root.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                )
        );

        setContentView(root);
    }

    // ---------------------------------------------------------
    // UI HELPERS
    // ---------------------------------------------------------

    private void addSectionTitle(
            LinearLayout parent,
            String text
    ) {

        TextView label = new TextView(this);

        label.setText(text);
        label.setTextColor(Color.rgb(160, 190, 255));
        label.setTextSize(17);
        label.setPadding(0, dp(16), 0, dp(8));

        parent.addView(label);
    }

    private void addButton(
            LinearLayout parent,
            String text,
            Runnable action
    ) {

        Button button = new Button(this);

        button.setText(text);
        button.setTextSize(15);
        button.setAllCaps(false);

        button.setTextColor(Color.WHITE);

        GradientDrawable background = new GradientDrawable();

        background.setColor(Color.rgb(42, 46, 62));
        background.setCornerRadius(dp(14));

        button.setBackground(background);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(54)
                );

        params.setMargins(0, dp(5), 0, dp(5));

        parent.addView(button, params);

        button.setOnClickListener(v -> {

            if (processing) {
                Toast.makeText(
                        this,
                        "Please wait for processing to finish",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            action.run();
        });
    }

    private int dp(float value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    // ---------------------------------------------------------
    // IMAGE PICKER
    // ---------------------------------------------------------

    private void pickImage() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(
                intent,
                PICK_IMAGE
        );
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == PICK_IMAGE
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {

            loadImage(data.getData());
        }
    }

    private void loadImage(Uri uri) {

        try {

            Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                    getContentResolver(),
                    uri
            );

            if (bitmap == null) {
                showError("Unable to load image");
                return;
            }

            originalBitmap = bitmap.copy(
                    Bitmap.Config.ARGB_8888,
                    true
            );

            currentBitmap = originalBitmap.copy(
                    Bitmap.Config.ARGB_8888,
                    true
            );

            undoBitmap = null;

            displayBitmap(currentBitmap);

            statusText.setText("Photo loaded successfully");

        } catch (Exception e) {

            showError("Image loading failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------
    // DISPLAY
    // ---------------------------------------------------------

    private void displayBitmap(Bitmap bitmap) {

        if (bitmap == null) {
            return;
        }

        imageView.setImageBitmap(bitmap);

        currentBitmap = bitmap;
    }

    private boolean checkImage() {

        if (currentBitmap == null) {

            Toast.makeText(
                    this,
                    "Please choose a photo first",
                    Toast.LENGTH_SHORT
            ).show();

            return false;
        }

        return true;
    }

    private void saveUndo() {

        if (currentBitmap != null) {

            undoBitmap = currentBitmap.copy(
                    Bitmap.Config.ARGB_8888,
                    true
            );
        }
    }

    private void showError(String message) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();

        statusText.setText(message);
    }

    // ---------------------------------------------------------
    // ASYNC PROCESSING
    // ---------------------------------------------------------

    private void runAsync(
            String operation,
            AsyncOperation task
    ) {

        if (!checkImage()) {
            return;
        }

        if (processing) {
            return;
        }

        saveUndo();

        processing = true;

        statusText.setText(operation + "...");
        
        task.run(currentBitmap, new OpenCvPhotoAIEngine.BitmapCallback() {

            @Override
            public void onSuccess(Bitmap bitmap) {

                runOnUiThread(() -> {

                    processing = false;

                    if (bitmap != null) {

                        displayBitmap(bitmap);

                        statusText.setText(
                                operation + " completed"
                        );

                    } else {

                        statusText.setText(
                                operation + " failed"
                        );
                    }
                });
            }

            @Override
            public void onError(Exception error) {

                runOnUiThread(() -> {

                    processing = false;

                    showError(
                            operation + " error: " +
                                    error.getMessage()
                    );
                });
            }
        });
    }

    interface AsyncOperation {

        void run(
                Bitmap bitmap,
                OpenCvPhotoAIEngine.BitmapCallback callback
        );
    }

    // ---------------------------------------------------------
    // AUTO ENHANCE
    // ---------------------------------------------------------

    private void autoEnhance() {

        runAsync(
                "Auto Enhance",
                (bitmap, callback) ->
                        photoAI.autoEnhanceAsync(
                                bitmap,
                                callback
                        )
        );
    }

    // ---------------------------------------------------------
    // AUTO COLOR
    // ---------------------------------------------------------

    private void autoColor() {

        if (!checkImage()) {
            return;
        }

        saveUndo();

        Bitmap result = photoAI.autoColor(currentBitmap);

        displayBitmap(result);

        statusText.setText("Auto color completed");
    }

    // ---------------------------------------------------------
    // SMOOTH SKIN
    // ---------------------------------------------------------

    private void smoothSkin() {

        runAsync(
                "Skin Smoothing",
                (bitmap, callback) ->
                        photoAI.smoothAsync(
                                bitmap,
                                0.40f,
                                callback
                        )
        );
    }

    // ---------------------------------------------------------
    // NATURAL SLIMMING
    // ---------------------------------------------------------

    private void slimPerson() {

        runAsync(
                "Natural Slimming",
                (bitmap, callback) ->
                        photoAI.slimPersonAsync(
                                bitmap,
                                0.08f,
                                callback
                        )
        );
    }

    // ---------------------------------------------------------
    // WARM FILTER
    // ---------------------------------------------------------

    private void warmFilter() {

        if (!checkImage()) return;

        saveUndo();

        displayBitmap(
                photoAI.warmFilter(currentBitmap)
        );

        statusText.setText("Warm filter applied");
    }

    // ---------------------------------------------------------
    // COOL FILTER
    // ---------------------------------------------------------

    private void coolFilter() {

        if (!checkImage()) return;

        saveUndo();

        displayBitmap(
                photoAI.coolFilter(currentBitmap)
        );

        statusText.setText("Cool filter applied");
    }

    // ---------------------------------------------------------
    // BLACK AND WHITE
    // ---------------------------------------------------------

    private void blackAndWhite() {

        if (!checkImage()) return;

        saveUndo();

        displayBitmap(
                photoAI.blackAndWhite(currentBitmap)
        );

        statusText.setText("Black & white applied");
    }

    // ---------------------------------------------------------
    // SEPIA
    // ---------------------------------------------------------

    private void sepiaFilter() {

        if (!checkImage()) return;

        saveUndo();

        displayBitmap(
                photoAI.sepiaFilter(currentBitmap)
        );

        statusText.setText("Sepia filter applied");
    }

    // ---------------------------------------------------------
    // VIGNETTE
    // ---------------------------------------------------------

    private void vignette() {

        if (!checkImage()) return;

        saveUndo();

        displayBitmap(
                photoAI.vignette(
                        currentBitmap,
                        0.45f
                )
        );

        statusText.setText("Vignette applied");
    }

    // ---------------------------------------------------------
    // UNDO
    // ---------------------------------------------------------

    private void undo() {

        if (undoBitmap == null) {

            Toast.makeText(
                    this,
                    "Nothing to undo",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Bitmap restore = undoBitmap.copy(
                Bitmap.Config.ARGB_8888,
                true
        );

        undoBitmap = null;

        displayBitmap(restore);

        statusText.setText("Previous edit restored");
    }

    // ---------------------------------------------------------
    // SAVE TO GALLERY
    // ---------------------------------------------------------

    private void saveImage() {

        if (!checkImage()) {
            return;
        }

        OutputStream outputStream = null;

        try {

            String filename =
                    "AI_Photo_Studio_" +
                            System.currentTimeMillis() +
                            ".jpg";

            android.content.ContentValues values =
                    new android.content.ContentValues();

            values.put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    filename
            );

            values.put(
                    MediaStore.Images.Media.MIME_TYPE,
                    "image/jpeg"
            );

            if (android.os.Build.VERSION.SDK_INT >= 29) {

                values.put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "Pictures/AI Photo Studio"
                );

                values.put(
                        MediaStore.Images.Media.IS_PENDING,
                        1
                );
            }

            Uri uri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
            );

            if (uri == null) {

                showError("Unable to create image file");
                return;
            }

            outputStream = getContentResolver()
                    .openOutputStream(uri);

            if (outputStream == null) {

                showError("Unable to open output file");
                return;
            }

            boolean success = currentBitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    95,
                    outputStream
            );

            outputStream.flush();

            outputStream.close();

            outputStream = null;

            if (!success) {

                showError("Image compression failed");
                return;
            }

            if (android.os.Build.VERSION.SDK_INT >= 29) {

                android.content.ContentValues update =
                        new android.content.ContentValues();

                update.put(
                        MediaStore.Images.Media.IS_PENDING,
                        0
                );

                getContentResolver().update(
                        uri,
                        update,
                        null,
                        null
                );
            }

            Toast.makeText(
                    this,
                    "Photo saved to Pictures/AI Photo Studio",
                    Toast.LENGTH_LONG
            ).show();

            statusText.setText("Photo saved successfully");

        } catch (Exception e) {

            showError(
                    "Saving failed: " + e.getMessage()
            );

        } finally {

            try {

                if (outputStream != null) {
                    outputStream.close();
                }

            } catch (Exception ignored) {
            }
        }
    }

    // ---------------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------------

    @Override
    protected void onDestroy() {

        if (photoAI != null) {
            photoAI.shutdown();
        }

        super.onDestroy();
    }
}
