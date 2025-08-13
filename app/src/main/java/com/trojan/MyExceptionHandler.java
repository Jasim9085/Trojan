package com.trojan;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MyExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultUEH;
    private static final String TAG = "MyExceptionHandler";

    public MyExceptionHandler(Context context) {
        this.context = context;
        this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        final StringWriter stackTrace = new StringWriter();
        e.printStackTrace(new PrintWriter(stackTrace));
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        final String errorLog = "Timestamp: " + timestamp + "\n" +
			"Error: \n" + stackTrace.toString() + "\n\n";

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(context, "App crashed. Log saved to Downloads/error.txt", Toast.LENGTH_LONG).show();
				}
			});

        writeToFile(errorLog);

        if (defaultUEH != null) {
            defaultUEH.uncaughtException(t, e);
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        }
    }

    private void writeToFile(String log) {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }
            File logFile = new File(downloadsDir, "error.txt");
            FileWriter writer = new FileWriter(logFile, true);
            writer.append(log);
            writer.flush();
            writer.close();
        } catch (Exception ex) {
            Log.e(TAG, "Failed to write to public log file", ex);
        }
    }
}
