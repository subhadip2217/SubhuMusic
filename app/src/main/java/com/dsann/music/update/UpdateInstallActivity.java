package com.dsann.music.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;

public class UpdateInstallActivity extends Activity {

    public static final String EXTRA_APK_PATH = "subhumusic.update.APK_PATH";
    private static final int REQUEST_UNKNOWN_APP_SOURCE = 7401;

    private File apkFile;
    private boolean installerStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String path = getIntent().getStringExtra(EXTRA_APK_PATH);
        if (path == null || path.trim().isEmpty()) {
            Toast.makeText(this, "The downloaded update file is missing.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        apkFile = new File(path);
        if (!apkFile.isFile() || apkFile.length() <= 0L) {
            Toast.makeText(this, "The downloaded APK is incomplete. Please download it again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        continueInstall();
    }

    private void continueInstall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                    .setTitle("Allow SubhuMusic to install updates")
                    .setMessage("Android needs permission to install APK updates downloaded by SubhuMusic. Allow installs from this source, then the installer will open automatically.")
                    .setCancelable(false)
                    .setPositiveButton("Open Settings", (dialog, which) -> {
                        try {
                            Intent settingsIntent = new Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getPackageName())
                            );
                            startActivityForResult(settingsIntent, REQUEST_UNKNOWN_APP_SOURCE);
                        } catch (Exception e) {
                            Toast.makeText(this, "Could not open install permission settings.", Toast.LENGTH_LONG).show();
                            finish();
                        }
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> finish())
                    .show();
            return;
        }

        launchPackageInstaller();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_UNKNOWN_APP_SOURCE) return;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || getPackageManager().canRequestPackageInstalls()) {
            launchPackageInstaller();
        } else {
            Toast.makeText(this, "Install permission was not enabled. The update is still downloaded.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void launchPackageInstaller() {
        if (installerStarted) return;
        installerStarted = true;

        try {
            Uri apkUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    apkFile
            );

            Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (installIntent.resolveActivity(getPackageManager()) == null) {
                installerStarted = false;
                Toast.makeText(this, "No Android package installer is available.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            startActivity(installIntent);
            finish();
        } catch (Exception e) {
            installerStarted = false;
            Toast.makeText(this, "Could not open the Android installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
