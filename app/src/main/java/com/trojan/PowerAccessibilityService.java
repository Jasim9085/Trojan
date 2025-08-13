package com.trojan; // Or your actual package name

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.Queue;

public class PowerAccessibilityService extends AccessibilityService {

    public static final String ACTION_TRIGGER_SHUTDOWN = "com.trojan.ACTION_TRIGGER_SHUTDOWN";
    public static final String ACTION_TRIGGER_LOCK_SCREEN = "com.trojan.ACTION_TRIGGER_LOCK_SCREEN";

    private BroadcastReceiver commandReceiver;

    // ===============================================================
    // ==                  START: NEW STATE FLAG                    ==
    // ===============================================================
    /**
     * This flag ensures we only search for the power off button
     * when we are explicitly expecting the power menu to be open.
     */
    private boolean isWaitingForPowerMenu = false;
    // ===============================================================
    // ==                   END: NEW STATE FLAG                     ==
    // ===============================================================


    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // --- IMPROVEMENT ---
        // Only proceed if the event is a window change AND we are waiting for the power menu.
        if (!isWaitingForPowerMenu || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        // Now that we've received a window change event, we assume the power menu is visible.
        // We can turn the flag off so we don't try to search again on subsequent window changes.
        isWaitingForPowerMenu = false;

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            return;
        }

        // Search for the "Power off" button and click it.
        findAndClickButton(rootNode, new String[]{"Power off", "power off", "Shutdown", "Restart"});
        rootNode.recycle();
    }

    private void findAndClickButton(AccessibilityNodeInfo rootNode, String[] targetTexts) {
        // This method is unchanged
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(rootNode);

        while (!queue.isEmpty()) {
            AccessibilityNodeInfo currentNode = queue.poll();
            if (currentNode == null) continue;

            CharSequence nodeText = currentNode.getText();
            CharSequence contentDesc = currentNode.getContentDescription();

            for (String target : targetTexts) {
                if ((nodeText != null && nodeText.toString().equalsIgnoreCase(target)) ||
                    (contentDesc != null && contentDesc.toString().equalsIgnoreCase(target))) {
                    AccessibilityNodeInfo clickableNode = currentNode;
                    while (clickableNode != null && !clickableNode.isClickable()) {
                        clickableNode = clickableNode.getParent();
                    }
                    if (clickableNode != null) {
                        clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        return;
                    }
                }
            }
            for (int i = 0; i < currentNode.getChildCount(); i++) {
                queue.add(currentNode.getChild(i));
            }
        }
    }


    @Override
    public void onInterrupt() {
        // If the service is interrupted, reset our state flag
        isWaitingForPowerMenu = false;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.DEFAULT | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);

        commandReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null) {
                    String action = intent.getAction();
                    if (action.equals(ACTION_TRIGGER_SHUTDOWN)) {
                        // --- IMPROVEMENT ---
                        // Set the flag to true right before we open the power dialog
                        isWaitingForPowerMenu = true;
                        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);

                    } else if (action.equals(ACTION_TRIGGER_LOCK_SCREEN)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                        }
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TRIGGER_SHUTDOWN);
        filter.addAction(ACTION_TRIGGER_LOCK_SCREEN);
        registerReceiver(commandReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (commandReceiver != null) {
            unregisterReceiver(commandReceiver);
        }
    }
}
