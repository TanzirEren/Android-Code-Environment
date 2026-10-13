package com.ace.ide;

import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Ui.init(this);
        // JGit needs a home directory on Android
        try {
            System.setProperty("user.home", getFilesDir().getAbsolutePath());
            System.setProperty("java.io.tmpdir", getCacheDir().getAbsolutePath());
        } catch (Throwable ignored) { }
    }
}
