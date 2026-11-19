package com.example.photoai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // --------------------------------------------------
    // THEME
    // --------------------------------------------------

    private static final int BG = Color.rgb(12, 15, 24);
    private static final int PANEL = Color.rgb(22, 27, 40);
    private static final int TILE = Color.rgb(30, 37, 53);
    private static final int ACCENT = Color.rgb(130, 105, 255);
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.rgb(164, 174, 194);

    // --------------------------------------------------
    // IMAGE
    // --------------------------------------------------

    private Bitmap currentBitmap;
    private ImageView imageView;

    private TextView statusText;
    private TextView imageInfoText;

    private boolean processing = false;

    private OpenCvPhotoAIEngine photoAI;

    // --------------------------------------------------
    // UNDO / REDO
    // --------------------------------------------------

    private final ArrayDeque<Bitmap> undoStack = new ArrayDeque<>();
    private final ArrayDeque<Bitmap> redoStack = new ArrayDeque<>();

    private static final int MAX_HISTORY = 10;

    // --------------------------------------------------
    // THREADING
    // --------------------------------------------------

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    // --------------------------------------------------
    // LAYOUT
    // --------------------------------------------------

    private LinearLayout root;
    private LinearLayout toolsContainer;

    // --------------------------------------------------
    // ACTIVITY
    // --------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        photoAI = new OpenCvPhotoAIEngine();

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(0);
        }

        createUI();
    }

    // ==================================================
    // MAIN UI
    // ==================================================

    private void createUI() {

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        root.setPadding(dp(16), dp(12), dp(16), dp(12));

        setContentView(root);

        // HEADER
        createHeader();

        // STATUS
        statusText = new TextView(this);
        statusText.setText("Ready to edit your photo");
        statusText.setTextColor(MUTED);
        statusText.setTextSize(12);
        statusText.setPadding(0, dp(8), 0, dp(8));

        root.addView(statusText);

        // PHOTO PREVIEW
        createPreview();

        // PHOTO INFORMATION
        imageInfoText = new TextView(this);
        imageInfoText.setText("No image selected");
        imageInfoText.setTextColor(MUTED);
        imageInfoText.setTextSize(12);
        imageInfoText.setGravity(Gravity.CENTER);
        imageInfoText.setPadding(0, dp(10), 0, dp(12));

        root.addView(imageInfoText);

        // TOOLS SCROLL
        ScrollView scroll = new ScrollView(this);

        scroll.setFillViewport(false);

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                );

        root.addView(scroll, scrollParams);

        toolsContainer = new LinearLayout(this);
        toolsContainer.setOrientation(LinearLayout.VERTICAL);

        scroll.addView(toolsContainer);

        createTools();

        // BOTTOM STATUS BAR
        createFooter();
    }

    // ==================================================
    // HEADER
    // ==================================================

    private void createHeader() {

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("AI Photo Studio");
        title.setTextColor(TEXT);
        title.setTextSize(24);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView subtitle = new TextView(this);
        subtitle.setText("CREATIVE PHOTO EDITOR");
        subtitle.setTextColor(ACCENT);
        subtitle.setTextSize(10);
        subtitle.setLetterSpacing(0.15f);

        titleBox.addView(title);
        titleBox.addView(subtitle);

        header.addView(titleBox,
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView logo = new TextView(this);
        logo.setText("✦");
        logo.setTextSize(26);
        logo.setTextColor(Color.WHITE);
        logo.setGravity(Gravity.CENTER);

        GradientDrawable logoBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(120, 92, 255),
                        Color.rgb(65, 142, 255)
                });

        logoBg.setCornerRadius(dp(18));

        logo.setBackground(logoBg);

        logo.setPadding(dp(12), dp(5), dp(12), dp(5));

        header.addView(logo);

        root.addView(header);
    }

    // ==================================================
    // PREVIEW
    // ==================================================

    private void createPreview() {

        FrameLayout previewFrame = new FrameLayout(this);

        GradientDrawable previewBackground = new GradientDrawable();
        previewBackground.setColor(PANEL);
        previewBackground.setCornerRadius(dp(24));

        previewFrame.setBackground(previewBackground);

        previewFrame.setClipToOutline(true);

        LinearLayout.LayoutParams frameParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(260)
                );

        frameParams.setMargins(0, dp(8), 0, dp(8));

        root.addView(previewFrame, frameParams);

        imageView = new ImageView(this);

        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setAdjustViewBounds(true);

        previewFrame.addView(imageView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                ));

        // EMPTY STATE
        TextView emptyText = new TextView(this);

        emptyText.setText("✦\n\nSelect a photo to begin");
        emptyText.setTextColor(MUTED);
        emptyText.setTextSize(15);
        emptyText.setGravity(Gravity.CENTER);

        previewFrame.addView(emptyText,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                ));

        imageView.setTag(emptyText);

        previewFrame.setOnClickListener(v -> pickImage());
    }

    // ==================================================
    // TOOLS
    // ==================================================

    private void createTools() {

        toolsContainer.removeAllViews();

        // QUICK ACTIONS
        addSectionTitle(toolsContainer, "QUICK ACTIONS");

        addToolRow(
                toolsContainer,
                new String[]{"🖼️", "✨", "↩️", "💾"},
                new String[]{"Gallery", "Enhance", "Undo", "Save"},
                new Runnable[]{
                        this::pickImage,
                        this::autoEnhance,
                        this::undo,
                        this::saveImage
                }
        );

        // BASIC EDIT
        addSectionTitle(toolsContainer, "BASIC EDIT");

        addToolRow(
                toolsContainer,
                new String[]{"✂️", "🔄", "↔️", "↕️"},
                new String[]{"Crop", "Rotate", "Flip H", "Flip V"},
                new Runnable[]{
                        this::cropImage,
                        () -> rotateImage(90),
                        this::flipHorizontal,
                        this::flipVertical
                }
        );

        addToolRow(
                toolsContainer,
                new String[]{"📐", "↪️", "🔍", "🖼️"},
                new String[]{"Resize", "Redo", "Original", "Gallery"},
                new Runnable[]{
                        this::resizeImage,
                        this::redo,
                        this::showOriginal,
                        this::pickImage
                }
        );

        // ENHANCE
        addSectionTitle(toolsContainer, "AI & IMAGE ENHANCEMENT");

        addToolRow(
                toolsContainer,
                new String[]{"🌈", "💆", "👤", "☀️"},
                new String[]{"Auto Color", "Smooth", "Natural Slim", "Warm"},
                new Runnable[]{
                        this::autoColor,
                        this::smoothSkin,
                        this::slimPerson,
                        this::warmFilter
                }
        );

        // FILTERS
        addSectionTitle(toolsContainer, "CREATIVE FILTERS");

        addToolRow(
                toolsContainer,
                new String[]{"❄️", "⚫", "🟤", "🌑"},
                new String[]{"Cool", "B&W", "Sepia", "Vignette"},
                new Runnable[]{
                        this::coolFilter,
                        this::blackAndWhite,
                        this::sepiaFilter,
                        this::vignette
                }
        );

        // SPECIAL TOOLS
        addSectionTitle(toolsContainer, "SPECIAL TOOLS");

        addToolRow(
                toolsContainer,
                new String[]{"🧹", "✨", "🎨", "⚙️"},
                new String[]{"Remove People", "Auto Edit", "Filters", "Info"},
                new Runnable[]{
                        this::removePeoplePlaceholder,
                        this::autoEnhance,
                        this::showFilters,
                        this::showAppInfo
                }
        );
    }

    // ==================================================
    // ICON TOOL ROW
    // ==================================================

    private void addToolRow(
            LinearLayout parent,
            String[] icons,
            String[] labels,
            Runnable[] actions) {

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        rowParams.setMargins(0, dp(3), 0, dp(3));

        parent.addView(row, rowParams);

        for (int i = 0; i < icons.length; i++) {

            final Runnable action = actions[i];

            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);

            tile.setPadding(dp(4), dp(9), dp(4), dp(9));

            GradientDrawable background = new GradientDrawable();
            background.setColor(TILE);
            background.setCornerRadius(dp(16));

            tile.setBackground(background);

            LinearLayout.LayoutParams tileParams =
                    new LinearLayout.LayoutParams(
                            0,
                            dp(80),
                            1
                    );

            tileParams.setMargins(dp(3), dp(3), dp(3), dp(3));

            row.addView(tile, tileParams);

            TextView icon = new TextView(this);
            icon.setText(icons[i]);
            icon.setTextSize(24);
            icon.setGravity(Gravity.CENTER);

            tile.addView(icon);

            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextColor(TEXT);
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);

            label.setPadding(0, dp(4), 0, 0);

            tile.addView(label);

            tile.setOnClickListener(v -> {

                if (processing) {
                    Toast.makeText(
                            this,
                            "Please wait...",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                action.run();
            });
        }
    }

    // ==================================================
    // SECTION TITLE
    // ==================================================

    private void addSectionTitle(LinearLayout parent, String text) {

        TextView title = new TextView(this);

        title.setText(text);
        title.setTextColor(MUTED);
        title.setTextSize(11);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLetterSpacing(0.12f);

        title.setPadding(dp(2), dp(15), 0, dp(6));

        parent.addView(title);
    }

    // ==================================================
    // FOOTER
    // ==================================================

    private void createFooter() {

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(8), 0, dp(3));

        TextView text = new TextView(this);

        text.setText("AI PHOTO STUDIO  •  EDIT BEAUTIFULLY");
        text.setTextColor(MUTED);
        text.setTextSize(10);
        text.setGravity(Gravity.CENTER);

        footer.addView(text);

        root.addView(footer);
    }

    // ==================================================
    // GALLERY
    // ==================================================

    private void pickImage() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(intent, 1001);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1001 &&
                resultCode == RESULT_OK &&
                data != null &&
                data.getData() != null) {

            Uri uri = data.getData();

            try {

                Bitmap bitmap;

                try (InputStream stream =
                             getContentResolver().openInputStream(uri)) {

                    bitmap = BitmapFactory.decodeStream(stream);
                }

                if (bitmap != null) {

                    currentBitmap = bitmap;

                    undoStack.clear();
                    redoStack.clear();

                    displayBitmap(bitmap);

                    status("Photo loaded successfully");

                } else {

                    Toast.makeText(
                            this,
                            "Unable to load image",
                            Toast.LENGTH_SHORT
                    ).show();
                }

            } catch (Exception e) {

                Toast.makeText(
                        this,
                        "Error loading image: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    // ==================================================
    // DISPLAY
    // ==================================================

    private void displayBitmap(Bitmap bitmap) {

        currentBitmap = bitmap;

        imageView.setImageBitmap(bitmap);

        imageView.setVisibility(View.VISIBLE);

        TextView empty = (TextView) imageView.getTag();

        if (empty != null) {
            empty.setVisibility(View.GONE);
        }

        imageInfoText.setText(
                bitmap.getWidth() + " × " + bitmap.getHeight() + " px"
        );
    }

    // ==================================================
    // UNDO HISTORY
    // ==================================================

    private void saveUndo() {

        if (currentBitmap == null) return;

        undoStack.push(
                currentBitmap.copy(Bitmap.Config.ARGB_8888, true)
        );

        while (undoStack.size() > MAX_HISTORY) {
            undoStack.removeLast();
        }

        // Any new edit clears redo history
        clearStack(redoStack);
    }

    private void undo() {

        if (undoStack.isEmpty()) {

            Toast.makeText(
                    this,
                    "Nothing to undo",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        redoStack.push(
                currentBitmap.copy(Bitmap.Config.ARGB_8888, true)
        );

        Bitmap restored = undoStack.pop();

        displayBitmap(restored);

        status("Undo completed");
    }

    private void redo() {

        if (redoStack.isEmpty()) {

            Toast.makeText(
                    this,
                    "Nothing to redo",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        undoStack.push(
                currentBitmap.copy(Bitmap.Config.ARGB_8888, true)
        );

        Bitmap restored = redoStack.pop();

        displayBitmap(restored);

        status("Redo completed");
    }

    private void clearStack(ArrayDeque<Bitmap> stack) {

        while (!stack.isEmpty()) {

            Bitmap bitmap = stack.pop();

            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    // ==================================================
    // ORIGINAL PHOTO
    // ==================================================

    private Bitmap originalBitmap;

    private void showOriginal() {

        if (currentBitmap == null) {
            checkImage();
            return;
        }

        Toast.makeText(
                this,
                "Load a photo again to reset all edits",
                Toast.LENGTH_SHORT
        ).show();
    }

    // ==================================================
    // AUTO ENHANCE
    // ==================================================

    private void autoEnhance() {

        runEdit("Enhancing photo...", () ->
                photoAI.autoEnhance(currentBitmap));
    }

    // ==================================================
    // AUTO COLOR
    // ==================================================

    private void autoColor() {

        runEdit("Adjusting colors...", () ->
                photoAI.autoColor(currentBitmap));
    }

    // ==================================================
    // SMOOTH
    // ==================================================

    private void smoothSkin() {

        runEdit("Smoothing image...", () ->
                photoAI.smoothImage(currentBitmap));
    }

    // ==================================================
    // NATURAL SLIM
    // ==================================================

    private void slimPerson() {

        runEdit("Applying natural slimming...", () ->
                photoAI.slimPerson(currentBitmap, 0.08f));
    }

    // ==================================================
    // FILTERS
    // ==================================================

    private void warmFilter() {

        runEdit("Applying warm filter...", () ->
                photoAI.warmFilter(currentBitmap));
    }

    private void coolFilter() {

        runEdit("Applying cool filter...", () ->
                photoAI.coolFilter(currentBitmap));
    }

    private void blackAndWhite() {

        runEdit("Applying black & white...", () ->
                photoAI.blackAndWhite(currentBitmap));
    }

    private void sepiaFilter() {

        runEdit("Applying sepia...", () ->
                photoAI.sepiaFilter(currentBitmap));
    }

    private void vignette() {

        runEdit("Applying vignette...", () ->
                photoAI.vignette(currentBitmap));
    }

    // ==================================================
    // IMAGE PROCESSOR
    // ==================================================

    private interface BitmapOperation {
        Bitmap execute() throws Exception;
    }

    private void runEdit(
            String message,
            BitmapOperation operation) {

        if (!checkImage()) return;

        if (processing) return;

        saveUndo();

        processing = true;

        status(message);

        Bitmap input = currentBitmap.copy(
                Bitmap.Config.ARGB_8888,
                true
        );

        executor.execute(() -> {

            Bitmap result = null;

            try {

                result = operation.execute();

                Bitmap finalResult = result;

                mainHandler.post(() -> {

                    if (finalResult != null) {

                        displayBitmap(finalResult);

                        status("Edit completed successfully");

                    } else {

                        status("Unable to process image");
                    }

                    processing = false;
                });

            } catch (Exception e) {

                mainHandler.post(() -> {

                    status("Processing failed");

                    Toast.makeText(
                            MainActivity.this,
                            e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                    processing = false;
                });

            } finally {

                if (input != null && !input.isRecycled()) {
                    input.recycle();
                }
            }
        });
    }

    // ==================================================
    // ROTATE
    // ==================================================

    private void rotateImage(float degrees) {

        if (!checkImage()) return;

        saveUndo();

        Matrix matrix = new Matrix();

        matrix.postRotate(degrees);

        Bitmap result = Bitmap.createBitmap(
                currentBitmap,
                0,
                0,
                currentBitmap.getWidth(),
                currentBitmap.getHeight(),
                matrix,
                true
        );

        displayBitmap(result);

        status("Rotated " + degrees + " degrees");
    }

    // ==================================================
    // FLIP HORIZONTAL
    // ==================================================

    private void flipHorizontal() {

        if (!checkImage()) return;

        saveUndo();

        Matrix matrix = new Matrix();

        matrix.preScale(-1f, 1f);

        Bitmap result = Bitmap.createBitmap(
                currentBitmap,
                0,
                0,
                currentBitmap.getWidth(),
                currentBitmap.getHeight(),
                matrix,
                true
        );

        displayBitmap(result);

        status("Flipped horizontally");
    }

    // ==================================================
    // FLIP VERTICAL
    // ==================================================

    private void flipVertical() {

        if (!checkImage()) return;

        saveUndo();

        Matrix matrix = new Matrix();

        matrix.preScale(1f, -1f);

        Bitmap result = Bitmap.createBitmap(
                currentBitmap,
                0,
                0,
                currentBitmap.getWidth(),
                currentBitmap.getHeight(),
                matrix,
                true
        );

        displayBitmap(result);

        status("Flipped vertically");
    }

    // ==================================================
    // CROP
    // ==================================================

    private void cropImage() {

        if (!checkImage()) return;

        EditText percentInput = new EditText(this);

        percentInput.setHint("Crop percentage (e.g. 10)");
        percentInput.setInputType(2);
        percentInput.setText("10");

        new AlertDialog.Builder(this)
                .setTitle("✂️ Crop Image")
                .setMessage("Crop equally from all sides.")
                .setView(percentInput)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Crop", (dialog, which) -> {

                    try {

                        int percent = Integer.parseInt(
                                percentInput.getText().toString()
                        );

                        if (percent < 0 || percent >= 45) {

                            Toast.makeText(
                                    this,
                                    "Enter 0 to 44",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }

                        int w = currentBitmap.getWidth();
                        int h = currentBitmap.getHeight();

                        int cropX = (int) (w * percent / 100f);
                        int cropY = (int) (h * percent / 100f);

                        int newW = w - cropX * 2;
                        int newH = h - cropY * 2;

                        saveUndo();

                        Bitmap cropped = Bitmap.createBitmap(
                                currentBitmap,
                                cropX,
                                cropY,
                                newW,
                                newH
                        );

                        displayBitmap(cropped);

                        status("Crop completed");

                    } catch (Exception e) {

                        Toast.makeText(
                                this,
                                "Invalid crop value",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .show();
    }

    // ==================================================
    // RESIZE
    // ==================================================

    private void resizeImage() {

        if (!checkImage()) return;

        LinearLayout layout = new LinearLayout(this);

        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), dp(8));

        EditText widthInput = new EditText(this);
        widthInput.setHint("Width (pixels)");
        widthInput.setInputType(2);
        widthInput.setText(String.valueOf(currentBitmap.getWidth()));

        EditText heightInput = new EditText(this);
        heightInput.setHint("Height (pixels)");
        heightInput.setInputType(2);
        heightInput.setText(String.valueOf(currentBitmap.getHeight()));

        layout.addView(widthInput);
        layout.addView(heightInput);

        new AlertDialog.Builder(this)
                .setTitle("📐 Resize Image")
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Resize", (dialog, which) -> {

                    try {

                        int w = Integer.parseInt(
                                widthInput.getText().toString()
                        );

                        int h = Integer.parseInt(
                                heightInput.getText().toString()
                        );

                        if (w < 1 || h < 1 || w > 8000 || h > 8000) {

                            Toast.makeText(
                                    this,
                                    "Size must be between 1 and 8000 pixels",
                                    Toast.LENGTH_LONG
                            ).show();

                            return;
                        }

                        saveUndo();

                        Bitmap resized = Bitmap.createScaledBitmap(
                                currentBitmap,
                                w,
                                h,
                                true
                        );

                        displayBitmap(resized);

                        status("Resize completed");

                    } catch (Exception e) {

                        Toast.makeText(
                                this,
                                "Invalid dimensions",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .show();
    }

    // ==================================================
    // SAVE IMAGE
    // ==================================================

    private void saveImage() {

        if (!checkImage()) return;

        OutputStream outputStream = null;

        try {

            ContentValues values = new ContentValues();

            values.put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    "AI_Photo_Studio_" + System.currentTimeMillis() + ".jpg"
            );

            values.put(
                    MediaStore.Images.Media.MIME_TYPE,
                    "image/jpeg"
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

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
                throw new Exception("Unable to create image file");
            }

            outputStream = getContentResolver().openOutputStream(uri);

            currentBitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    95,
                    outputStream
            );

            outputStream.flush();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                ContentValues finish = new ContentValues();

                finish.put(
                        MediaStore.Images.Media.IS_PENDING,
                        0
                );

                getContentResolver().update(uri, finish, null, null);
            }

            Toast.makeText(
                    this,
                    "Saved to Pictures/AI Photo Studio",
                    Toast.LENGTH_LONG
            ).show();

            status("Image saved");

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Save failed: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();

        } finally {

            try {

                if (outputStream != null) {
                    outputStream.close();
                }

            } catch (Exception ignored) {
            }
        }
    }

    // ==================================================
    // REMOVE PEOPLE - PLACEHOLDER
    // ==================================================

    private void removePeoplePlaceholder() {

        new AlertDialog.Builder(this)
                .setTitle("Background People Removal")
                .setMessage(
                        "The tool interface is ready, but actual person " +
                        "removal requires an offline AI segmentation and " +
                        "inpainting model. This model has not yet been " +
                        "integrated into this version."
                )
                .setPositiveButton("OK", null)
                .show();
    }

    // ==================================================
    // FILTER MENU
    // ==================================================

    private void showFilters() {

        String[] filters = {
                "Warm",
                "Cool",
                "Black & White",
                "Sepia",
                "Vignette"
        };

        new AlertDialog.Builder(this)
                .setTitle("Creative Filters")
                .setItems(filters, (dialog, which) -> {

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

    // ==================================================
    // APP INFORMATION
    // ==================================================

    private void showAppInfo() {

        new AlertDialog.Builder(this)
                .setTitle("AI Photo Studio")
                .setMessage(
                        "Version 1.0\n\n" +
                                "Photo editing tools:\n" +
                                "• Auto enhancement\n" +
                                "• Auto color\n" +
                                "• Natural slimming\n" +
                                "• Smooth\n" +
                                "• Crop\n" +
                                "• Rotate\n" +
                                "• Resize\n" +
                                "• Flip\n" +
                                "• Creative filters\n" +
                                "• Undo and redo\n" +
                                "• Save to gallery\n\n" +
                                "Offline processing.\n" +
                                "No OpenAI API key required."
                )
                .setPositiveButton("OK", null)
                .show();
    }

    // ==================================================
    // HELPERS
    // ==================================================

    private boolean checkImage() {

        if (currentBitmap == null) {

            Toast.makeText(
                    this,
                    "Please select a photo first",
                    Toast.LENGTH_SHORT
            ).show();

            return false;
        }

        return true;
    }

    private void status(String message) {

        if (statusText != null) {
            statusText.setText(message);
        }
    }

    private int dp(float value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    // ==================================================
    // CLEANUP
    // ==================================================

    @Override
    protected void onDestroy() {

        super.onDestroy();

        executor.shutdown();

        clearStack(undoStack);
        clearStack(redoStack);

        if (photoAI != null) {
            photoAI.shutdown();
        }
    }
}
