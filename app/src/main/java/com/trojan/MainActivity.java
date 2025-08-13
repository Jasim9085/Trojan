package com.trojan;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.annotation.NonNull;

// NEW, CORRECT IMPORT for getting the token
import com.google.firebase.messaging.FirebaseMessaging;

// These old imports are no longer needed and have been removed
// import com.google.firebase.iid.FirebaseInstanceId;
// import com.google.firebase.iid.InstanceIdResult;

// These are needed for the OnCompleteListener
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.PrintWriter;
import java.io.StringWriter;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private TextView tvFcmToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvFcmToken = findViewById(R.id.tvFcmToken);

        fetchTokenWithCrashHandler();
    }

    private void fetchTokenWithCrashHandler() {
        try {
            // ====================================================================
            // == THIS IS THE UPDATED CODE BLOCK TO GET THE FIREBASE TOKEN       ==
            // ====================================================================
            FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() { // The task now returns a String
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            // If the task fails, show that specific error
                            if (task.getException() != null) {
                                StringWriter sw = new StringWriter();
                                task.getException().printStackTrace(new PrintWriter(sw));
                                tvFcmToken.setText("Firebase task failed:\n" + sw.toString());
                            }
                            return;
                        }
                        // The result of the task is the token itself
                        String token = task.getResult();
                        tvFcmToken.setText(token);
                    }
                });
        } catch (Exception e) {
            // If just calling FirebaseMessaging.getInstance() crashes, catch it here.
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String error = "FATAL ERROR on startup:\n" + sw.toString();
            tvFcmToken.setText(error);
            Log.e(TAG, error);
        }
     }
}
