
package com.example.aiphotostudio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private LinearLayout root;
    private LinearLayout toolsContainer;
    private FrameLayout imageFrame;
    private ImageView imageView;
    private MaskView maskView;

    private Bitmap originalBitmap;
    private Bitmap currentBitmap;

    private final ArrayDeque<Bitmap> undoStack = new ArrayDeque<>();
    private final ArrayDeque<Bitmap> redoStack = new ArrayDeque<>();

    private static final int MAX_HISTORY = 8;
    private static final int PICK_IMAGE = 1001;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private boolean isBusy = false;
    private boolean maskingMode = false;

    private int brushSize = 35;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            createUI();
        } catch (Throwable startupError) {
            // Keep the app from closing silently; show the startup error on screen.
            TextView errorView = new TextView(this);
            errorView.setTextColor(Color.WHITE);
            errorView.setBackgroundColor(Color.rgb(24, 24, 30));
            errorView.setPadding(dp(20), dp(24), dp(20), dp(24));
            errorView.setTextSize(15);
            errorView.setText("AI Photo Studio could not start.\n\n"
                    + startupError.getClass().getSimpleName() + ": "
                    + String.valueOf(startupError.getMessage())
                    + "\n\nPlease send this message to support.");
            setContentView(errorView);
        }
    }

    // ----------------------------------------------------
    // MAIN UI
    // ----------------------------------------------------

    private void createUI() {

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(17, 18, 27));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(24));

        scrollView.addView(content);

        root.addView(scrollView, new LinearLayout.LayoutParams(
                -1, 0, 1f));

        // Header
        TextView title = text("AI PHOTO STUDIO", 24,
                Color.WHITE, true);

        content.addView(title);

        TextView subtitle = text(
                "Offline photo editing studio",
                13, Color.LTGRAY, false);

        content.addView(subtitle);

        addSpace(content, 18);

        // Image container
        imageFrame = new FrameLayout(this);
        imageFrame.setBackgroundColor(Color.rgb(30, 31, 42));

        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setAdjustViewBounds(true);

        imageFrame.addView(imageView,
                new FrameLayout.LayoutParams(
                        -1, -1));

        maskView = new MaskView();
        imageFrame.addView(maskView,
                new FrameLayout.LayoutParams(
                        -1, -1));

        content.addView(imageFrame,
                new LinearLayout.LayoutParams(
                        -1, dp(340)));

        addSpace(content, 12);

        TextView imageHint = text(
                "Choose a photo to begin editing",
                13, Color.GRAY, false);

        imageHint.setGravity(Gravity.CENTER);
        content.addView(imageHint);

        addSpace(content, 15);

        Button galleryButton = button("📷  SELECT PHOTO");
        galleryButton.setOnClickListener(v -> pickImage());

        content.addView(galleryButton,
                new LinearLayout.LayoutParams(
                        -1, dp(50)));

        addSpace(content, 20);

        toolsContainer = new LinearLayout(this);
        toolsContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(toolsContainer);

        createTools();

        setContentView(root);
    }

    // ----------------------------------------------------
    // TOOL BUTTONS
    // ----------------------------------------------------

    private void createTools() {

        addSection("QUICK ACTIONS");

        addToolRow(
                new String[]{"✨", "↩", "↪", "💾"},
                new String[]{"Enhance", "Undo", "Redo", "Save"},
                new Runnable[]{
                        this::autoEnhance,
                        this::undo,
                        this::redo,
                        this::saveImage
                });

        addSection("BASIC EDIT");

        addToolRow(
                new String[]{"✂", "⟳", "↔", "↕"},
                new String[]{"Crop", "Rotate", "Flip H", "Flip V"},
                new Runnable[]{
                        this::cropImage,
                        this::rotateImage,
                        this::flipHorizontal,
                        this::flipVertical
                });

        addToolRow(
                new String[]{"↔", "◉", "⟲", "↗"},
                new String[]{"Resize", "Original", "Reset", "Auto Color"},
                new Runnable[]{
                        this::resizeImage,
                        this::showOriginal,
                        this::resetImage,
                        this::autoColor
                });

        addSection("ENHANCEMENT");

        addToolRow(
                new String[]{"☀", "◌", "♧", "☕"},
                new String[]{"Brightness", "Smooth", "Natural Slim", "Warm"},
                new Runnable[]{
                        this::brightness,
                        this::smoothImage,
                        this::slimPerson,
                        this::warmFilter
                });

        addSection("CREATIVE FILTERS");

        addToolRow(
                new String[]{"❄", "◐", "◧", "◒"},
                new String[]{"Cool", "B&W", "Sepia", "Vignette"},
                new Runnable[]{
                        this::coolFilter,
                        this::blackAndWhite,
                        this::sepiaFilter,
                        this::vignette
                });

        addSection("SPECIAL TOOLS");

        addToolRow(
                new String[]{"🧹", "✨", "🎨", "ℹ"},
                new String[]{"Remove People", "Auto Edit", "Filters", "About"},
                new Runnable[]{
                        this::removePeople,
                        this::autoEdit,
                        this::showFilters,
                        this::showAbout
                });

        addSpace(toolsContainer, 15);

        Button cancelMask = button("CANCEL REMOVE PEOPLE");
        cancelMask.setOnClickListener(v -> cancelMaskMode());

        toolsContainer.addView(cancelMask);
    }

    private void addSection(String title) {

        TextView section = text(
                title, 14,
                Color.rgb(184, 150, 255), true);

        section.setPadding(0, dp(16), 0, dp(10));

        toolsContainer.addView(section);
    }

    private void addToolRow(
            String[] icons,
            String[] labels,
            Runnable[] actions) {

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        for (int i = 0; i < icons.length; i++) {

            final Runnable action = actions[i];

            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setBackgroundColor(Color.rgb(34, 35, 48));
            tile.setPadding(dp(3), dp(9), dp(3), dp(9));

            TextView icon = text(
                    icons[i], 25,
                    Color.WHITE, true);

            icon.setGravity(Gravity.CENTER);

            TextView label = text(
                    labels[i], 10,
                    Color.LTGRAY, false);

            label.setGravity(Gravity.CENTER);

            tile.addView(icon);
            tile.addView(label);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            0, dp(76), 1f);

            params.setMargins(dp(3), dp(3), dp(3), dp(3));

            row.addView(tile, params);

            tile.setOnClickListener(v -> {
                if (currentBitmap == null) {
                    toast("Please select a photo first");
                    return;
                }

                if (isBusy) {
                    toast("Please wait...");
                    return;
                }

                action.run();
            });
        }

        toolsContainer.addView(row);
    }

    // ----------------------------------------------------
    // IMAGE PICKER
    // ----------------------------------------------------

    private void pickImage() {

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {

            loadImage(data.getData());
        }
    }

    private void loadImage(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(stream, null, bounds);
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                toast("Unable to load image");
                return;
            }

            // Decode a smaller image first to avoid loading full-resolution photos
            // into memory. The editor keeps images at a maximum of 1280 pixels.
            final int maxSize = 1280;
            int sampleSize = 1;
            while (bounds.outWidth / sampleSize > maxSize
                    || bounds.outHeight / sampleSize > maxSize) {
                sampleSize *= 2;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap;
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(stream, null, options);
            }

            if (bitmap == null) {
                toast("Unable to load photo");
                return;
            }

            bitmap = resizeBitmapIfNeeded(bitmap, maxSize);
            Bitmap newOriginal = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            Bitmap newCurrent = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            if (newOriginal == null || newCurrent == null) {
                if (newOriginal != null) newOriginal.recycle();
                if (newCurrent != null) newCurrent.recycle();
                bitmap.recycle();
                toast("Not enough memory to open this photo");
                return;
            }

            clearHistoryStacks();
            recycleBitmap(originalBitmap);
            recycleBitmap(currentBitmap);
            originalBitmap = newOriginal;
            currentBitmap = newCurrent;
            recycleBitmap(bitmap);

            maskingMode = false;
            if (maskView != null) maskView.clearMask();
            showBitmap();
            toast("Photo loaded successfully");

        } catch (OutOfMemoryError memoryError) {
            System.gc();
            toast("Photo is too large for available memory. Try a smaller image.");
        } catch (Exception e) {
            e.printStackTrace();
            toast("Unable to open photo");
        }
    }


    private void clearHistoryStacks() {
        while (!undoStack.isEmpty()) recycleBitmap(undoStack.pop());
        while (!redoStack.isEmpty()) recycleBitmap(redoStack.pop());
    }


    private void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    private Bitmap resizeBitmapIfNeeded(
            Bitmap bitmap, int maxSize) {

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width <= maxSize && height <= maxSize) {
            return bitmap;
        }

        float scale = Math.min(
                (float) maxSize / width,
                (float) maxSize / height);

        return Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, (int) (width * scale)),
                Math.max(1, (int) (height * scale)),
                true);
    }

    // ----------------------------------------------------
    // BITMAP DISPLAY
    // ----------------------------------------------------

    private void showBitmap() {

        if (currentBitmap != null) {
            imageView.setImageBitmap(currentBitmap);
        }

        maskView.invalidate();
    }

    private void requireImage() {

        if (currentBitmap == null) {
            toast("Select a photo first");
        }
    }

    // ----------------------------------------------------
    // HISTORY / UNDO / REDO
    // ----------------------------------------------------

    private void saveHistory() {

        if (currentBitmap == null) return;

        undoStack.push(currentBitmap.copy(
                Bitmap.Config.ARGB_8888, true));

        while (undoStack.size() > MAX_HISTORY) {
            undoStack.removeLast().recycle();
        }

        clearRedo();
    }

    private void clearRedo() {

        while (!redoStack.isEmpty()) {
            Bitmap b = redoStack.pop();

            if (!b.isRecycled()) {
                b.recycle();
            }
        }
    }

    private void undo() {

        if (undoStack.isEmpty()) {
            toast("Nothing to undo");
            return;
        }

        redoStack.push(currentBitmap.copy(
                Bitmap.Config.ARGB_8888, true));

        currentBitmap = undoStack.pop();

        showBitmap();
    }

    private void redo() {

        if (redoStack.isEmpty()) {
            toast("Nothing to redo");
            return;
        }

        undoStack.push(currentBitmap.copy(
                Bitmap.Config.ARGB_8888, true));

        currentBitmap = redoStack.pop();

        showBitmap();
    }

    // ----------------------------------------------------
    // GENERIC EDIT RUNNER
    // ----------------------------------------------------

    interface ImageOperation {
        Bitmap apply(Bitmap source);
    }

    private void runEdit(
            String message,
            ImageOperation operation) {

        if (currentBitmap == null || isBusy) {
            return;
        }

        isBusy = true;

        Bitmap input = currentBitmap.copy(
                Bitmap.Config.ARGB_8888, true);

        toast(message);

        executor.execute(() -> {

            Bitmap result = null;

            try {
                result = operation.apply(input);
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                if (input != null && !input.isRecycled()) input.recycle();
            }

            final Bitmap finalResult = result;

            mainHandler.post(() -> {

                isBusy = false;

                if (finalResult != null) {

                    saveHistory();

                    Bitmap previous = currentBitmap;
                    currentBitmap = finalResult;
                    if (previous != null && previous != finalResult
                            && !previous.isRecycled()) previous.recycle();
                    showBitmap();

                    toast("Editing complete");

                } else {

                    toast("Editing failed");
                }
            });
        });
    }

    // ----------------------------------------------------
    // AUTO ENHANCE
    // ----------------------------------------------------

    private void autoEnhance() {

        runEdit("Enhancing photo...",
                this::enhanceBitmap);
    }

    private Bitmap enhanceBitmap(Bitmap src) {

        Bitmap out = Bitmap.createBitmap(
                src.getWidth(),
                src.getHeight(),
                Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(out);

        Paint paint = new Paint(
                Paint.ANTI_ALIAS_FLAG);

        ColorMatrix matrix = new ColorMatrix();

        matrix.set(new float[]{
                1.18f, 0, 0, 0, 5,
                0, 1.18f, 0, 0, 5,
                0, 0, 1.18f, 0, 5,
                0, 0, 0, 1, 0
        });

        paint.setColorFilter(
                new ColorMatrixColorFilter(matrix));

        canvas.drawBitmap(src, 0, 0, paint);

        return out;
    }

    // ----------------------------------------------------
    // AUTO COLOR
    // ----------------------------------------------------

    private void autoColor() {

        runEdit("Adjusting colors...",
                this::autoColorBitmap);
    }

    private Bitmap autoColorBitmap(Bitmap src) {

        return adjustBitmap(
                src, 1.12f, 1.04f, 1.05f, 0);
    }

    // ----------------------------------------------------
    // BRIGHTNESS
    // ----------------------------------------------------

    private void brightness() {

        showSliderDialog(
                "Brightness",
                -80, 80, 0,
                value -> runEdit(
                        "Adjusting brightness...",
                        src -> adjustBitmap(
                                src, 1f, 1f, 1f, value)));
    }

    // ----------------------------------------------------
    // SMOOTH
    // ----------------------------------------------------

    private void smoothImage() {

        runEdit("Smoothing image...",
                this::smoothBitmap);
    }

    private Bitmap smoothBitmap(Bitmap src) {

        int w = src.getWidth();
        int h = src.getHeight();

        Bitmap out = Bitmap.createBitmap(
                w, h, Bitmap.Config.ARGB_8888);

        int[] pixels = new int[w * h];
        src.getPixels(pixels, 0, w, 0, 0, w, h);

        int[] result = pixels.clone();

        for (int y = 1; y < h - 1; y++) {

            for (int x = 1; x < w - 1; x++) {

                int index = y * w + x;

                int center = pixels[index];

                int r = Color.red(center);
                int g = Color.green(center);
                int b = Color.blue(center);

                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {

                        int p = pixels[(y + dy) * w + x + dx];

                        r += Color.red(p);
                        g += Color.green(p);
                        b += Color.blue(p);
                    }
                }

                result[index] = Color.rgb(
                        r / 10, g / 10, b / 10);
            }
        }

        out.setPixels(result, 0, w, 0, 0, w, h);

        return out;
    }

    // ----------------------------------------------------
    // NATURAL SLIM
    // ----------------------------------------------------

    private void slimPerson() {

        showSliderDialog(
                "Natural Slim",
                0, 15, 5,
                value -> runEdit(
                        "Applying natural slim...",
                        src -> slimBitmap(src, value)));
    }

    private Bitmap slimBitmap(
            Bitmap src, int intensity) {

        int w = src.getWidth();
        int h = src.getHeight();

        Bitmap out = Bitmap.createBitmap(
                w, h, Bitmap.Config.ARGB_8888);

        int strength = Math.min(
                18, Math.max(0, intensity));

        float centerX = w / 2f;

        for (int y = 0; y < h; y++) {

            float normalizedY = (float) y / h;

            float factor = 1f - (strength / 100f);

            float center = centerX;

            float squeeze = (float) Math.sin(
                    normalizedY * Math.PI);

            float currentWidth = w * factor;

            float left = center - currentWidth / 2f;

            for (int x = 0; x < w; x++) {

                float srcX = center
                        + (x - center)
                        * (1f - (strength / 100f)
                        * squeeze);

                srcX = Math.max(
                        0, Math.min(w - 1, srcX));

                int pixel = src.getPixel(
                        (int) srcX, y);

                out.setPixel(x, y, pixel);
            }
        }

        return out;
    }

    // ----------------------------------------------------
    // FILTERS
    // ----------------------------------------------------

    private void warmFilter() {

        runEdit("Applying warm filter...",
                src -> adjustBitmap(
                        src, 1.04f, 1.0f, 0.91f, 4));
    }

    private void coolFilter() {

        runEdit("Applying cool filter...",
                src -> adjustBitmap(
                        src, 0.92f, 1.0f, 1.10f, 2));
    }

    private void blackAndWhite() {

        runEdit("Applying black and white...",
                src -> grayscaleBitmap(src, false));
    }

    private void sepiaFilter() {

        runEdit("Applying sepia...",
                src -> grayscaleBitmap(src, true));
    }

    private void vignette() {

        runEdit("Applying vignette...",
                this::vignetteBitmap);
    }

    private Bitmap grayscaleBitmap(
            Bitmap src, boolean sepia) {

        int w = src.getWidth();
        int h = src.getHeight();

        Bitmap out = Bitmap.createBitmap(
                w, h, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < h; y++) {

            for (int x = 0; x < w; x++) {

                int p = src.getPixel(x, y);

                int r = Color.red(p);
                int g = Color.green(p);
                int b = Color.blue(p);

                int gray = (int)(
                        0.299 * r
                        + 0.587 * g
                        + 0.114 * b);

                if (sepia) {

                    r = clamp((int)(
                            gray * 1.07 + 20));

                    g = clamp((int)(
                            gray * 0.94 + 10));

                    b = clamp((int)(
                            gray * 0.72));
                } else {

                    r = g = b = gray;
                }

                out.setPixel(
                        x, y, Color.rgb(r, g, b));
            }
        }

        return out;
    }

    private Bitmap vignetteBitmap(Bitmap src) {

        int w = src.getWidth();
        int h = src.getHeight();

        Bitmap out = Bitmap.createBitmap(
                w, h, Bitmap.Config.ARGB_8888);

        float centerX = w / 2f;
        float centerY = h / 2f;

        float maxDist = (float) Math.sqrt(
                centerX * centerX + centerY * centerY);

        for (int y = 0; y < h; y++) {

            for (int x = 0; x < w; x++) {

                int p = src.getPixel(x, y);

                float dx = x - centerX;
                float dy = y - centerY;

                float distance = (float) Math.sqrt(
                        dx * dx + dy * dy);

                float factor = 1f
                        - 0.38f * distance / maxDist;

                out.setPixel(x, y, Color.rgb(
                        clamp((int)(Color.red(p) * factor)),
                        clamp((int)(Color.green(p) * factor)),
                        clamp((int)(Color.blue(p) * factor))));
            }
        }

        return out;
    }

    // ----------------------------------------------------
    // CROP
    // ----------------------------------------------------

    private void cropImage() {

        new AlertDialog.Builder(this)
                .setTitle("Crop Image")
                .setMessage(
                        "Crop 10% from all edges?")
                .setPositiveButton("Crop", (d, w) -> {

                    runEdit("Cropping...",
                            this::cropBitmap);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private Bitmap cropBitmap(Bitmap src) {

        int w = src.getWidth();
        int h = src.getHeight();

        int marginX = (int)(w * 0.10f);
        int marginY = (int)(h * 0.10f);

        return Bitmap.createBitmap(
                src,
                marginX,
                marginY,
                w - marginX * 2,
                h - marginY * 2);
    }

    // ----------------------------------------------------
    // ROTATE
    // ----------------------------------------------------

    private void rotateImage() {

        runEdit("Rotating...",
                src -> rotateBitmap(src, 90));
    }

    private Bitmap rotateBitmap(
            Bitmap src, float degrees) {

        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);

        return Bitmap.createBitmap(
                src, 0, 0,
                src.getWidth(),
                src.getHeight(),
                matrix, true);
    }

    // ----------------------------------------------------
    // FLIP
    // ----------------------------------------------------

    private void flipHorizontal() {

        runEdit("Flipping...",
                src -> flipBitmap(src, true));
    }

    private void flipVertical() {

        runEdit("Flipping...",
                src -> flipBitmap(src, false));
    }

    private Bitmap flipBitmap(
            Bitmap src, boolean horizontal) {

        Matrix matrix = new Matrix();

        matrix.setScale(
                horizontal ? -1 : 1,
                horizontal ? 1 : -1);

        return Bitmap.createBitmap(
                src, 0, 0,
                src.getWidth(),
                src.getHeight(),
                matrix, true);
    }

    // ----------------------------------------------------
    // RESIZE
    // ----------------------------------------------------

    private void resizeImage() {

        showSliderDialog(
                "Resize image",
                25, 150, 100,
                value -> runEdit(
                        "Resizing...",
                        src -> Bitmap.createScaledBitmap(
                                src,
                                Math.max(1,
                                        src.getWidth()
                                        * value / 100),
                                Math.max(1,
                                        src.getHeight()
                                        * value / 100),
                                true)));
    }

    // ----------------------------------------------------
    // ORIGINAL / RESET
    // ----------------------------------------------------

    private void showOriginal() {

        if (originalBitmap == null) return;

        currentBitmap = originalBitmap.copy(
                Bitmap.Config.ARGB_8888, true);

        showBitmap();
    }

    private void resetImage() {

        if (originalBitmap == null) return;

        saveHistory();

        currentBitmap = originalBitmap.copy(
                Bitmap.Config.ARGB_8888, true);

        showBitmap();

        toast("Image reset");
    }

    // ----------------------------------------------------
    // REMOVE PEOPLE - MANUAL MASK + BASIC FILL
    // ----------------------------------------------------

    private void removePeople() {

        if (currentBitmap == null) {
            requireImage();
            return;
        }

        maskingMode = true;

        maskView.clearMask();
        maskView.setMaskMode(true);

        new AlertDialog.Builder(this)
                .setTitle("Remove People")
                .setMessage(
                        "Paint over the person or unwanted object "
                        + "with your finger.\n\n"
                        + "Then press REMOVE to replace the "
                        + "marked area using nearby pixels.\n\n"
                        + "This offline version uses basic pixel "
                        + "filling, not AI inpainting.")
                .setPositiveButton("START", (d, w) -> {
                    toast("Paint over the person to remove");
                })
                .setNegativeButton("CANCEL", (d, w) -> {
                    cancelMaskMode();
                })
                .show();
    }

    private void cancelMaskMode() {

        maskingMode = false;
        maskView.setMaskMode(false);
        maskView.clearMask();
        maskView.invalidate();
    }

    private void performRemoval() {

        if (!maskView.hasMask()) {
            toast("Paint the area you want to remove");
            return;
        }

        final Bitmap source = currentBitmap.copy(
                Bitmap.Config.ARGB_8888, true);

        final Bitmap mask = maskView.getMaskBitmap();

        maskingMode = false;
        maskView.setMaskMode(false);

        isBusy = true;

        toast("Removing selected area...");

        executor.execute(() -> {

            Bitmap result = removeMaskedArea(
                    source, mask);

            mainHandler.post(() -> {

                isBusy = false;

                if (result != null) {

                    saveHistory();

                    currentBitmap = result;

                    maskView.clearMask();

                    showBitmap();

                    toast("Area processing complete");
                }
            });
        });
    }

    /*
     * Basic offline replacement.
     * This is NOT AI inpainting. It averages nearby
     * unmasked pixels to fill the painted area.
     */
    private Bitmap removeMaskedArea(
            Bitmap source, Bitmap mask) {

        int w = source.getWidth();
        int h = source.getHeight();

        int[] pixels = new int[w * h];
        int[] maskPixels = new int[w * h];

        source.getPixels(
                pixels, 0, w, 0, 0, w, h);

        mask.getPixels(
                maskPixels, 0, w, 0, 0, w, h);

        int[] output = pixels.clone();

        int radius = 5;

        for (int y = radius; y < h - radius; y++) {

            for (int x = radius; x < w - radius; x++) {

                int index = y * w + x;

                if (Color.alpha(maskPixels[index]) == 0) {
                    continue;
                }

                long r = 0, g = 0, b = 0;
                int count = 0;

                for (int dy = -radius; dy <= radius; dy++) {

                    for (int dx = -radius; dx <= radius; dx++) {

                        if (Math.abs(dx) <= 1
                                && Math.abs(dy) <= 1) {
                            continue;
                        }

                        int nx = x + dx;
                        int ny = y + dy;

                        int ni = ny * w + nx;

                        if (Color.alpha(maskPixels[ni]) > 0) {
                            continue;
                        }

                        int p = pixels[ni];

                        r += Color.red(p);
                        g += Color.green(p);
                        b += Color.blue(p);

                        count++;
                    }
                }

                if (count > 0) {

                    output[index] = Color.rgb(
                            (int)(r / count),
                            (int)(g / count),
                            (int)(b / count));
                }
            }
        }

        Bitmap result = Bitmap.createBitmap(
                w, h, Bitmap.Config.ARGB_8888);

        result.setPixels(
                output, 0, w, 0, 0, w, h);

        return result;
    }

    // ----------------------------------------------------
    // MASK PAINT VIEW
    // ----------------------------------------------------

    private class MaskView extends View {

        private Bitmap maskBitmap;
        private Canvas maskCanvas;

        private Paint maskPaint;
        private Paint displayPaint;

        private Path displayPath;

        private boolean maskMode = false;

        private float lastX, lastY;

        MaskView() {

            super(MainActivity.this);

            setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            maskPaint.setColor(Color.WHITE);
            maskPaint.setStyle(Paint.Style.STROKE);
            maskPaint.setStrokeWidth(brushSize);
            maskPaint.setStrokeCap(Paint.Cap.ROUND);
            maskPaint.setStrokeJoin(Paint.Join.ROUND);

            displayPaint = new Paint(
                    Paint.ANTI_ALIAS_FLAG);

            displayPaint.setColor(
                    Color.argb(110, 255, 70, 70));

            displayPaint.setStyle(Paint.Style.STROKE);
            displayPaint.setStrokeWidth(brushSize);
            displayPaint.setStrokeCap(Paint.Cap.ROUND);
            displayPaint.setStrokeJoin(Paint.Join.ROUND);

            displayPath = new Path();
        }

        void setMaskMode(boolean enabled) {
            maskMode = enabled;
            invalidate();
        }

        void clearMask() {

            displayPath.reset();

            if (maskBitmap != null) {

                maskBitmap.eraseColor(Color.TRANSPARENT);
            }

            invalidate();
        }

        boolean hasMask() {

            if (maskBitmap == null) return false;

            int[] pixels = new int[
                    maskBitmap.getWidth()
                    * maskBitmap.getHeight()];

            maskBitmap.getPixels(
                    pixels, 0,
                    maskBitmap.getWidth(),
                    0, 0,
                    maskBitmap.getWidth(),
                    maskBitmap.getHeight());

            for (int p : pixels) {
                if (Color.alpha(p) > 0) return true;
            }

            return false;
        }

        Bitmap getMaskBitmap() {

            if (maskBitmap == null
                    || currentBitmap == null) {
                return null;
            }

            return Bitmap.createScaledBitmap(
                    maskBitmap,
                    currentBitmap.getWidth(),
                    currentBitmap.getHeight(),
                    true);
        }

        private void ensureMask() {

            if (maskBitmap != null) return;

            maskBitmap = Bitmap.createBitmap(
                    Math.max(1, getWidth()),
                    Math.max(1, getHeight()),
                    Bitmap.Config.ARGB_8888);

            maskCanvas = new Canvas(maskBitmap);
        }

        @Override
        protected void onDraw(Canvas canvas) {

            super.onDraw(canvas);

            if (maskMode) {

                canvas.drawPath(
                        displayPath, displayPaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {

            if (!maskMode) return false;

            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:

                    ensureMask();

                    lastX = x;
                    lastY = y;

                    displayPath.moveTo(x, y);

                    maskPaint.setStrokeWidth(
                            dp(brushSize));

                    displayPaint.setStrokeWidth(
                            dp(brushSize));

                    maskCanvas.drawCircle(
                            x, y,
                            dp(brushSize) / 2f,
                            new Paint() {{
                                setColor(Color.WHITE);
                                setStyle(Paint.Style.FILL);
                            }});

                    invalidate();

                    return true;

                case MotionEvent.ACTION_MOVE:

                    ensureMask();

                    displayPath.lineTo(x, y);

                    maskCanvas.drawLine(
                            lastX, lastY, x, y,
                            maskPaint);

                    lastX = x;
                    lastY = y;

                    invalidate();

                    return true;

                case MotionEvent.ACTION_UP:

                    displayPath.lineTo(x, y);

                    maskCanvas.drawLine(
                            lastX, lastY, x, y,
                            maskPaint);

                    invalidate();

                    return true;
            }

            return true;
        }
    }

    // ----------------------------------------------------
    // AUTO EDIT
    // ----------------------------------------------------

    private void autoEdit() {

        runEdit("Auto editing...",
                src -> enhanceBitmap(src));
    }

    // ----------------------------------------------------
    // FILTER DIALOG
    // ----------------------------------------------------

    private void showFilters() {

        String[] options = {
                "Warm",
                "Cool",
                "Black & White",
                "Sepia",
                "Vignette"
        };

        new AlertDialog.Builder(this)
                .setTitle("Filters")
                .setItems(options, (d, which) -> {

                    switch (which) {
                        case 0:
                            warmFilter();
                            break;
                        case 1:
                            coolFilter();
                            break;
                        case 2:
                            blackAndWhite();
                            break;
                        case 3:
                            sepiaFilter();
                            break;
                        case 4:
                            vignette();
                            break;
                    }
                })
                .show();
    }

    // ----------------------------------------------------
    // ABOUT
    // ----------------------------------------------------

    private void showAbout() {

        new AlertDialog.Builder(this)
                .setTitle("AI Photo Studio")
                .setMessage(
                        "Offline Photo Editor\n\n"
                        + "Edit photos using local "
                        + "image-processing tools.\n\n"
                        + "No OpenAI API key required.")
                .setPositiveButton("OK", null)
                .show();
    }

    // ----------------------------------------------------
    // SAVE TO GALLERY
    // ----------------------------------------------------

    private void saveImage() {

        if (currentBitmap == null) {
            requireImage();
            return;
        }

        try {

            String filename =
                    "AIPhotoStudio_"
                    + System.currentTimeMillis()
                    + ".jpg";

            ContentValues values = new ContentValues();

            values.put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    filename);

            values.put(
                    MediaStore.Images.Media.MIME_TYPE,
                    "image/jpeg");

            if (Build.VERSION.SDK_INT >= 29) {

                values.put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "Pictures/AI Photo Studio");

                values.put(
                        MediaStore.Images.Media.IS_PENDING,
                        1);
            }

            Uri uri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values);

            if (uri == null) {
                toast("Unable to save photo");
                return;
            }

            OutputStream output =
                    getContentResolver().openOutputStream(uri);

            currentBitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    95, output);

            output.close();

            if (Build.VERSION.SDK_INT >= 29) {

                values.clear();

                values.put(
                        MediaStore.Images.Media.IS_PENDING,
                        0);

                getContentResolver().update(
                        uri, values, null, null);
            }

            toast("Saved to Pictures/AI Photo Studio");

        } catch (Exception e) {

            toast("Save failed");
        }
    }

    // ----------------------------------------------------
    // SLIDER DIALOG
    // ----------------------------------------------------

    interface SliderCallback {
        void onSelected(int value);
    }

    private void showSliderDialog(
            String title,
            int min,
            int max,
            int initial,
            SliderCallback callback) {

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));

        TextView valueText = text(
                String.valueOf(initial),
                18, Color.WHITE, true);

        valueText.setGravity(Gravity.CENTER);

        SeekBar seekBar = new SeekBar(this);

        seekBar.setMax(max - min);
        seekBar.setProgress(initial - min);

        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar bar,
                            int progress,
                            boolean fromUser) {

                        valueText.setText(
                                String.valueOf(min + progress));
                    }

                    @Override
                    public void onStartTrackingTouch(
                            SeekBar bar) {}

                    @Override
                    public void onStopTrackingTouch(
                            SeekBar bar) {}
                });

        layout.addView(valueText);
        layout.addView(seekBar);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(layout)
                .setPositiveButton("Apply",
                        (dialog, which) -> {

                            callback.onSelected(
                                    min + seekBar.getProgress());
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ----------------------------------------------------
    // BITMAP ADJUSTMENT
    // ----------------------------------------------------

    private Bitmap adjustBitmap(
            Bitmap src,
            float red,
            float green,
            float blue,
            float brightness) {

        Bitmap out = Bitmap.createBitmap(
                src.getWidth(),
                src.getHeight(),
                Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(out);

        Paint paint = new Paint(
                Paint.ANTI_ALIAS_FLAG);

        ColorMatrix matrix = new ColorMatrix(
                new float[]{
                        red, 0, 0, 0, brightness,
                        0, green, 0, 0, brightness,
                        0, 0, blue, 0, brightness,
                        0, 0, 0, 1, 0
                });

        paint.setColorFilter(
                new ColorMatrixColorFilter(matrix));

        canvas.drawBitmap(src, 0, 0, paint);

        return out;
    }

    private int clamp(int value) {

        return Math.max(0, Math.min(255, value));
    }

    // ----------------------------------------------------
    // UI HELPERS
    // ----------------------------------------------------

    private TextView text(
            String value,
            int size,
            int color,
            boolean bold) {

        TextView tv = new TextView(this);

        tv.setText(value);
        tv.setTextSize(size);
        tv.setTextColor(color);

        if (bold) {
            tv.setTypeface(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD);
        }

        return tv;
    }

    private Button button(String value) {

        Button b = new Button(this);

        b.setText(value);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(
                Color.rgb(112, 75, 220));

        return b;
    }

    private void addSpace(
            LinearLayout layout,
            int size) {

        View spacer = new View(this);

        layout.addView(spacer,
                new LinearLayout.LayoutParams(
                        -1, dp(size)));
    }

    private int dp(int value) {

        return (int)(value
                * getResources()
                .getDisplayMetrics()
                .density);
    }

    private void toast(String message) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT).show();
    }

    // ----------------------------------------------------
    // CLEANUP
    // ----------------------------------------------------

    @Override
    protected void onDestroy() {

        super.onDestroy();

        executor.shutdown();

        if (maskView != null) {
            maskView.clearMask();
        }
    }
}
