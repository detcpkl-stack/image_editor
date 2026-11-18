package com.example.aiphotostudio;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 1001;
    private static final int MAX_UPLOAD_DIMENSION = 1800;

    private ImageView imageView;
    private Bitmap originalBitmap;
    private Bitmap editedBitmap;
    private SeekBar brightnessBar;
    private SeekBar contrastBar;
    private SeekBar slimmingBar;
    private TextView slimmingLabel;
    private LinearLayout root;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(20));
        root.setBackgroundColor(Color.WHITE);
        scrollView.addView(root);

        TextView heading = new TextView(this);
        heading.setText("AI PHOTO STUDIO");
        heading.setTextSize(25);
        heading.setTextColor(Color.BLACK);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, dp(8), 0, dp(12));
        root.addView(heading);

        imageView = new ImageView(this);
        imageView.setBackgroundColor(0xFFEAEAEA);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(-1, dp(340));
        imageParams.setMargins(0, dp(8), 0, dp(12));
        root.addView(imageView, imageParams);

        addButton("Open Photo", v -> openPhoto());

        addSectionTitle("Basic editing");
        addLabel("Brightness");
        brightnessBar = new SeekBar(this);
        brightnessBar.setMax(200);
        brightnessBar.setProgress(100);
        root.addView(brightnessBar);

        addLabel("Contrast");
        contrastBar = new SeekBar(this);
        contrastBar.setMax(200);
        contrastBar.setProgress(100);
        root.addView(contrastBar);

        brightnessBar.setOnSeekBarChangeListener(seekListener());
        contrastBar.setOnSeekBarChangeListener(seekListener());

        addButton("Black & White", v -> applyFilter(false));
        addButton("Sepia Filter", v -> applyFilter(true));
        addButton("Reset Image", v -> resetImage());

        addSectionTitle("Offline AI tools");
        addButton("Auto-Correct Image (works offline)", v -> {
            if (!requirePhoto()) return;
            Bitmap corrected = autoCorrect(editedBitmap != null ? editedBitmap : originalBitmap);
            if (corrected != null) {
                editedBitmap = corrected;
                imageView.setImageBitmap(editedBitmap);
                showMessage("Auto-correction applied on this device.");
            }
        });

        addSectionTitle("Online AI tools");
        addLabel("Natural slimming strength");
        slimmingLabel = new TextView(this);
        slimmingLabel.setText("Subtle (25%)");
        slimmingLabel.setTextColor(Color.DKGRAY);
        root.addView(slimmingLabel);

        slimmingBar = new SeekBar(this);
        slimmingBar.setMax(60);
        slimmingBar.setProgress(25);
        slimmingBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                slimmingLabel.setText("Strength: " + value + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(slimmingBar);

        addButton("Remove People in Background (Online AI)",
                v -> confirmOnlineEdit("remove_people"));
        addButton("Natural Body Slimming (Online AI)",
                v -> confirmOnlineEdit("slim_body"));

        addSectionTitle("Save");
        addButton("Save Image", v -> saveImage());

        TextView note = new TextView(this);
        note.setText("Auto-correction runs offline. Background removal and body reshaping require your own deployed AI server and an internet connection. The app does not store an OpenAI API key.");
        note.setTextSize(12);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(12), 0, dp(8));
        root.addView(note);

        setContentView(scrollView);
    }

    private void addSectionTitle(String title) {
        TextView text = new TextView(this);
        text.setText(title);
        text.setTextSize(18);
        text.setTextColor(Color.BLACK);
        text.setPadding(0, dp(18), 0, dp(4));
        root.addView(text);
    }

    private void addLabel(String title) {
        TextView text = new TextView(this);
        text.setText(title);
        text.setTextColor(Color.DKGRAY);
        root.addView(text);
    }

    private void addButton(String title, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(title);
        button.setOnClickListener(listener);
        root.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openPhoto() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select a photo"), PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            try (InputStream input = getContentResolver().openInputStream(data.getData())) {
                Bitmap loaded = BitmapFactory.decodeStream(input);
                if (loaded == null) {
                    showMessage("Unsupported image.");
                    return;
                }
                originalBitmap = loaded.copy(Bitmap.Config.ARGB_8888, true);
                resetImage();
            } catch (Exception e) {
                showMessage("Unable to open image: " + e.getMessage());
            }
        }
    }

    private boolean requirePhoto() {
        if (originalBitmap == null) {
            showMessage("Please select a photo first.");
            return false;
        }
        return true;
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
        if (originalBitmap == null || brightnessBar == null || contrastBar == null) return;
        float brightness = brightnessBar.getProgress() - 100;
        float contrast = contrastBar.getProgress() / 100f;
        ColorMatrix matrix = new ColorMatrix(new float[] {
                contrast, 0, 0, 0, brightness,
                0, contrast, 0, 0, brightness,
                0, 0, contrast, 0, brightness,
                0, 0, 0, 1, 0
        });
        editedBitmap = render(originalBitmap, matrix);
        imageView.setImageBitmap(editedBitmap);
    }

    private Bitmap render(Bitmap source, ColorMatrix matrix) {
        Bitmap result = Bitmap.createBitmap(
                source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(source, 0, 0, paint);
        return result;
    }

    private void applyFilter(boolean sepia) {
        if (!requirePhoto()) return;
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
        editedBitmap = render(originalBitmap, matrix);
        imageView.setImageBitmap(editedBitmap);
    }

    // Lightweight offline auto-enhancement: gray-world white balance and
    // percentile-based luminance contrast stretch. This is not generative AI.
    private Bitmap autoCorrect(Bitmap source) {
        if (source == null) return null;

        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);

        long sumR = 0, sumG = 0, sumB = 0, samples = 0;
        int[] histogram = new int[256];

        int step = Math.max(1, pixels.length / 250000);
        for (int i = 0; i < pixels.length; i += step) {
            int color = pixels[i];
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);
            sumR += r; sumG += g; sumB += b; samples++;
            int y = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            histogram[Math.max(0, Math.min(255, y))]++;
        }

        if (samples == 0) return source.copy(Bitmap.Config.ARGB_8888, true);

        float meanR = sumR / (float) samples;
        float meanG = sumG / (float) samples;
        float meanB = sumB / (float) samples;
        float gray = (meanR + meanG + meanB) / 3f;
        float gainR = clamp(gray / Math.max(1f, meanR), 0.80f, 1.25f);
        float gainG = clamp(gray / Math.max(1f, meanG), 0.80f, 1.25f);
        float gainB = clamp(gray / Math.max(1f, meanB), 0.80f, 1.25f);

        int total = 0;
        for (int value : histogram) total += value;
        int lowTarget = Math.max(1, (int) (total * 0.02f));
        int highTarget = Math.max(lowTarget + 1, (int) (total * 0.98f));
        int cumulative = 0, low = 0, high = 255;
        for (int i = 0; i < 256; i++) {
            cumulative += histogram[i];
            if (cumulative >= lowTarget) { low = i; break; }
        }
        cumulative = 0;
        for (int i = 0; i < 256; i++) {
            cumulative += histogram[i];
            if (cumulative >= highTarget) { high = i; break; }
        }
        if (high <= low + 8) { low = 0; high = 255; }

        float range = 255f / (high - low);
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int a = Color.alpha(color);
            float r = Color.red(color) * gainR;
            float g = Color.green(color) * gainG;
            float b = Color.blue(color) * gainB;
            float y = 0.299f * r + 0.587f * g + 0.114f * b;
            float newY = clamp((y - low) * range, 0f, 255f);
            float scale = y > 1f ? newY / y : 1f;
            r *= scale; g *= scale; b *= scale;

            float luminance = 0.299f * r + 0.587f * g + 0.114f * b;
            r = luminance + (r - luminance) * 1.06f;
            g = luminance + (g - luminance) * 1.06f;
            b = luminance + (b - luminance) * 1.06f;

            pixels[i] = Color.argb(a,
                    Math.round(clamp(r, 0f, 255f)),
                    Math.round(clamp(g, 0f, 255f)),
                    Math.round(clamp(b, 0f, 255f)));
        }

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void confirmOnlineEdit(String operation) {
        if (!requirePhoto()) return;

        String server = BuildConfig.AI_SERVER_URL;
        if (server == null || server.contains("YOUR-SERVICE")) {
            showMessage("First deploy the AI server and set AI_SERVER_URL in app/build.gradle.");
            return;
        }

        String explanation = operation.equals("remove_people")
                ? "The selected photo will be sent to your AI server to remove people in the background."
                : "The selected photo will be sent to your AI server for subtle body reshaping.";
        new AlertDialog.Builder(this)
                .setTitle("Online AI editing")
                .setMessage(explanation + "\n\nContinue?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> runOnlineEdit(operation))
                .show();
    }

    private void runOnlineEdit(String operation) {
        final Bitmap input = (editedBitmap != null ? editedBitmap : originalBitmap)
                .copy(Bitmap.Config.ARGB_8888, true);
        final int strength = slimmingBar.getProgress();

        progressDialog = ProgressDialog.show(this, "AI Photo Studio",
                "Uploading photo and processing. Please wait...", true, false);

        executor.execute(() -> {
            Bitmap result = null;
            String error = null;
            try {
                result = requestAiEdit(input, operation, strength);
                if (result == null) error = "The AI server returned an empty image.";
            } catch (Exception e) {
                error = e.getMessage() == null ? "AI request failed." : e.getMessage();
            }

            final Bitmap finalResult = result;
            final String finalError = error;
            mainHandler.post(() -> {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                if (finalResult != null) {
                    editedBitmap = finalResult;
                    imageView.setImageBitmap(editedBitmap);
                    showMessage("AI edit completed. Review the result before saving.");
                } else {
                    showMessage("AI edit failed: " + finalError);
                }
            });
        });
    }

    private Bitmap requestAiEdit(Bitmap input, String operation, int strength) throws Exception {
        String baseUrl = BuildConfig.AI_SERVER_URL.replaceAll("/+$", "");
        String boundary = "----AIPhotoStudio" + System.currentTimeMillis();
        URL url = new URL(baseUrl + "/edit");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(240000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        Bitmap uploadBitmap = scaleForUpload(input, MAX_UPLOAD_DIMENSION);
        ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
        uploadBitmap.compress(Bitmap.CompressFormat.JPEG, 88, imageBytes);
        byte[] photo = imageBytes.toByteArray();

        try (OutputStream output = connection.getOutputStream()) {
            writeTextPart(output, boundary, "operation", operation);
            writeTextPart(output, boundary, "strength", String.valueOf(strength));
            writeTextPart(output, boundary, "image", "photo.jpg", "image/jpeg", photo);
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
        }

        int status = connection.getResponseCode();
        InputStream responseStream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String responseText = readFully(responseStream);
        connection.disconnect();

        if (status < 200 || status >= 300) {
            try {
                JSONObject errorJson = new JSONObject(responseText);
                throw new Exception(errorJson.optString("detail",
                        "AI server error (HTTP " + status + ")"));
            } catch (org.json.JSONException ignored) {
                throw new Exception("AI server error (HTTP " + status + "): "
                        + responseText.substring(0, Math.min(300, responseText.length())));
            }
        }

        JSONObject json = new JSONObject(responseText);
        byte[] resultBytes = android.util.Base64.decode(
                json.getString("image_base64"), android.util.Base64.DEFAULT);
        Bitmap result = BitmapFactory.decodeByteArray(resultBytes, 0, resultBytes.length);
        if (result == null) throw new Exception("Could not decode the AI result.");
        return result;
    }

    private Bitmap scaleForUpload(Bitmap source, int maxDimension) {
        int w = source.getWidth();
        int h = source.getHeight();
        int largest = Math.max(w, h);
        if (largest <= maxDimension) return source;
        float scale = maxDimension / (float) largest;
        return Bitmap.createScaledBitmap(source,
                Math.max(1, Math.round(w * scale)),
                Math.max(1, Math.round(h * scale)), true);
    }

    private void writeTextPart(OutputStream out, String boundary,
                               String name, String value) throws Exception {
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
    }

    private void writeTextPart(OutputStream out, String boundary, String name,
                               String filename, String mime, byte[] data) throws Exception {
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name
                + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + mime + "\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String readFully(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private void resetImage() {
        if (originalBitmap == null) return;
        if (brightnessBar != null) brightnessBar.setProgress(100);
        if (contrastBar != null) contrastBar.setProgress(100);
        editedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        imageView.setImageBitmap(editedBitmap);
    }

    private void saveImage() {
        if (editedBitmap == null) {
            showMessage("Select a photo first.");
            return;
        }
        Uri savedUri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,
                    "AI_Photo_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AI Photo Studio");
            savedUri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (savedUri == null) throw new Exception("Could not create image.");
            try (OutputStream output = getContentResolver().openOutputStream(savedUri)) {
                if (output == null) throw new Exception("Could not open output.");
                if (!editedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                    throw new Exception("Image encoding failed.");
                }
            }
            showMessage("Saved in Pictures/AI Photo Studio.");
        } catch (Exception e) {
            if (savedUri != null) getContentResolver().delete(savedUri, null, null);
            showMessage("Error saving image: " + e.getMessage());
        }
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}
