package com.trojan;

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
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.PrintWriter;
import java.io.StringWriter;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private TextView tvFcmToken;
    private Button btnOpenAccessibility;
    private Button btnTestLock;
    private Button btnTestShutdown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- Find all UI elements ---
        tvFcmToken = findViewById(R.id.tvFcmToken);
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility);
        btnTestLock = findViewById(R.id.btnTestLock);
        btnTestShutdown = findViewById(R.id.btnTestShutdown);

        // --- Setup Button Listeners ---

        // Button to open Accessibility Settings directly
        btnOpenAccessibility.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Opening Accessibility Settings...", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            }
        });

        // Button to test the LOCK command locally
        btnTestLock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Sending local LOCK broadcast");
                Toast.makeText(MainActivity.this, "Testing Lock Command...", Toast.LENGTH_SHORT).show();
                sendBroadcast(new Intent(PowerAccessibilityService.ACTION_TRIGGER_LOCK_SCREEN));
            }
        });

        // Button to test the SHUTDOWN command locally
        btnTestShutdown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Sending local SHUTDOWN broadcast");
                Toast.makeText(MainActivity.this, "Testing Shutdown Command...", Toast.LENGTH_SHORT).show();
                sendBroadcast(new Intent(PowerAccessibilityService.ACTION_TRIGGER_SHUTDOWN));
            }
        });

        // --- Fetch and display the Firebase Token ---
        fetchAndDisplayToken();
    }

    /**
     * Fetches the current FCM registration token and displays it in the TextView.
     * Includes robust error handling.
     */
    private void fetchAndDisplayToken() {
        tvFcmToken.setText("Fetching token...");
        try {
            FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            // If the task fails, show that specific error
                            if (task.getException() != null) {
                                StringWriter sw = new StringWriter();
                                task.getException().printStackTrace(new PrintWriter(sw));
                                tvFcmToken.setText("Firebase task failed:\n" + sw.toString());
                            } else {
                                tvFcmToken.setText("Firebase task failed with no exception.");
                            }
                            return;
                        }
                        // Get the new token
                        String token = task.getResult();
                        String tokenText = "FCM Registration Token:\n\n" + token;
                        tvFcmToken.setText(tokenText);
                        Log.d(TAG, tokenText);
                    }
                });
        } catch (Exception e) {
            // If just calling FirebaseMessaging.getInstance() crashes, catch it here.
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String error = "FATAL ERROR on startup:\n" + sw.toString();
            tvFcmToken.setText(error);
            Log.e(TAG, error);
        }
    }
}
