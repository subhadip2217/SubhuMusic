package com.dsann.music.update;

import android.app.ActivityManager;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.File;

public class UpdateReceiver extends BroadcastReceiver {

    private static final String TAG = "UpdateReceiver";
    private static final String PREFS_NAME = "update_prefs";
    private static final String KEY_DOWNLOAD_ID = "download_id";
    private static final String KEY_DOWNLOAD_VERSION = "download_version";
    private static final String CHANNEL_ID = "subhumusic_updates";
    private static final int NOTIFICATION_ID = 7402;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
            return;
        }

        long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long savedDownloadId = prefs.getLong(KEY_DOWNLOAD_ID, -1L);
        if (downloadId == -1L || downloadId != savedDownloadId) return;

        DownloadManager downloadManager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            Log.e(TAG, "DownloadManager is unavailable after the update download completed.");
            return;
        }

        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                Log.e(TAG, "Could not find the completed SubhuMusic update download.");
                return;
            }

            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
            int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);

            if (statusIndex < 0 || uriIndex < 0) {
                Log.e(TAG, "DownloadManager did not return the expected update metadata.");
                return;
            }

            int status = cursor.getInt(statusIndex);
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                int reason = reasonIndex >= 0 ? cursor.getInt(reasonIndex) : -1;
                Log.e(TAG, "SubhuMusic update download failed. Status=" + status + ", reason=" + reason);
                Toast.makeText(context, "SubhuMusic update download failed. Please try again.", Toast.LENGTH_LONG).show();
                prefs.edit().remove(KEY_DOWNLOAD_ID).apply();
                return;
            }

            String localUri = cursor.getString(uriIndex);
            if (localUri == null || localUri.trim().isEmpty()) {
                Log.e(TAG, "Completed download did not provide a local APK path.");
                return;
            }

            Uri downloadedUri = Uri.parse(localUri);
            if (!"file".equalsIgnoreCase(downloadedUri.getScheme())) {
                Log.e(TAG, "Unexpected local update URI: " + downloadedUri);
                return;
            }

            String path = downloadedUri.getPath();
            if (path == null) {
                Log.e(TAG, "The downloaded update file path is empty.");
                return;
            }

            File apkFile = new File(path);
            if (!apkFile.isFile() || apkFile.length() <= 0L) {
                Log.e(TAG, "Downloaded update APK is missing or empty: " + apkFile);
                return;
            }

            String version = prefs.getString(KEY_DOWNLOAD_VERSION, "latest");
            prefs.edit().remove(KEY_DOWNLOAD_ID).apply();
            openInstallerOrNotify(context, apkFile, version);
        } catch (Exception e) {
            Log.e(TAG, "Failed to process completed SubhuMusic update download.", e);
        }
    }

    private void openInstallerOrNotify(Context context, File apkFile, String version) {
        Intent installIntent = new Intent(context, UpdateInstallActivity.class);
        installIntent.putExtra(UpdateInstallActivity.EXTRA_APK_PATH, apkFile.getAbsolutePath());
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (isAppVisible()) {
            try {
                context.startActivity(installIntent);
                return;
            } catch (Exception e) {
                Log.w(TAG, "Could not open installer immediately; showing an install notification.", e);
            }
        }

        showInstallNotification(context, installIntent, version);
    }

    private boolean isAppVisible() {
        try {
            ActivityManager.RunningAppProcessInfo processInfo =
                    new ActivityManager.RunningAppProcessInfo();
            ActivityManager.getMyMemoryState(processInfo);
            return processInfo.importance
                    <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
        } catch (Exception e) {
            return false;
        }
    }

    private void showInstallNotification(Context context, Intent installIntent, String version) {
        try {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "SubhuMusic updates",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                channel.setDescription("Notifications when a SubhuMusic APK update is ready to install.");
                notificationManager.createNotificationChannel(channel);
            }

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    NOTIFICATION_ID,
                    installIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(com.dsann.music.R.drawable.ic_update)
                    .setContentTitle("SubhuMusic update is ready")
                    .setContentText("Tap to install version " + version)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Notification permission is unavailable; the update APK remains downloaded.", e);
        } catch (Exception e) {
            Log.e(TAG, "Could not show the SubhuMusic install notification.", e);
        }
    }
}
