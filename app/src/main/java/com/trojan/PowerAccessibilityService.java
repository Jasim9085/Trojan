package com.trojan;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class PowerAccessibilityService extends AccessibilityService {
    private static final String TAG = "PowerAccessibility";

    // Define unique action strings to avoid conflicts with other apps
    public static final String ACTION_TRIGGER_LOCK_SCREEN = "com.trojan.LOCK_SCREEN";
    public static final String ACTION_TRIGGER_SHUTDOWN = "com.trojan.SHUTDOWN";

    private final BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }

            String action = intent.getAction();
            Log.d(TAG, "Received command: " + action);

            // Perform actions based on the received broadcast
            if (ACTION_TRIGGER_LOCK_SCREEN.equals(action)) {
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
            } else if (ACTION_TRIGGER_SHUTDOWN.equals(action)) {
                // Directly shutting down is not possible. We open the power menu instead.
                performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
            }
        }
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // We don't need to react to UI events, only broadcasts.
    }

    @Override
    public void onInterrupt() {
        // Handle interruptions
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Accessibility Service connected.");
        // Register the broadcast receiver to listen for our custom commands
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TRIGGER_LOCK_SCREEN);
        filter.addAction(ACTION_TRIGGER_SHUTDOWN);
        registerReceiver(commandReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // Unregister the receiver when the service is stopped
        unregisterReceiver(commandReceiver);
        Log.d(TAG, "Accessibility Service disconnected.");
        return super.onUnbind(intent);
    }
}
