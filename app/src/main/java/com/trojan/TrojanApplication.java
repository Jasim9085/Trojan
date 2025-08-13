package com.trojan;

import android.app.Application;

public class TrojanApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Set our custom handler as the default one
        Thread.setDefaultUncaughtExceptionHandler(new MyExceptionHandler(this));
    }
}
