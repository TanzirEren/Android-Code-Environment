package com.ace.ide;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Installs a built APK through the system PackageInstaller. */
public final class Installer {

    public static final String ACTION = "com.ace.ide.INSTALL_RESULT";

    private Installer() {}

    public static void install(final Activity a, final File apk) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            Ui.toast(a, "Allow ACE to install apps, then press Install again");
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
            } catch (Throwable t) { /* ignore */ }
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    PackageInstaller pi = a.getPackageManager().getPackageInstaller();
                    PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                    int id = pi.createSession(sp);
                    PackageInstaller.Session s = pi.openSession(id);
                    InputStream in = new FileInputStream(apk);
                    OutputStream out = s.openWrite("ace.apk", 0, apk.length());
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    s.fsync(out);
                    in.close();
                    out.close();
                    Intent i = new Intent(a, Result.class).setAction(ACTION);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    PendingIntent pe = PendingIntent.getBroadcast(a, id, i, flags);
                    s.commit(pe.getIntentSender());
                    s.close();
                } catch (final Throwable t) {
                    a.runOnUiThread(new Runnable() {
                        @Override public void run() { Ui.toast(a, "Install failed: " + t.getMessage()); }
                    });
                }
            }
        }).start();
    }

    public static class Result extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { c.startActivity(confirm); } catch (Throwable ignored) { }
                }
            } else if (status == PackageInstaller.STATUS_SUCCESS) {
                Ui.toast(c, "Installed ✔");
            } else {
                String m = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Ui.toast(c, "Install not completed" + (m != null ? ": " + m : ""));
            }
        }
    }
}
