package com.myexample.webtoapk;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * Receives scheduled notification alarms fired by AlarmManager
 * and broadcasts tapped-notification events back to MainActivity.
 */
public class NotificationReceiver extends BroadcastReceiver {

    public static final String ACTION_SHOW_SCHEDULED  = BuildConfig.APPLICATION_ID + ".SHOW_SCHEDULED_NOTIFICATION";
    public static final String ACTION_NOTIFICATION_TAPPED = BuildConfig.APPLICATION_ID + ".NOTIFICATION_TAPPED";
    public static final String ACTION_NOTIFICATION_ACTION  = BuildConfig.APPLICATION_ID + ".NOTIFICATION_ACTION";
    public static final String ACTION_DISMISS_NOTIFICATION = BuildConfig.APPLICATION_ID + ".DISMISS_NOTIFICATION";

    public static final String EXTRA_NOTIF_ID      = "notif_id";
    public static final String EXTRA_TITLE         = "title";
    public static final String EXTRA_MESSAGE       = "message";
    public static final String EXTRA_TAG           = "tag";
    public static final String EXTRA_PAYLOAD       = "payload";
    public static final String EXTRA_ACTION_LABEL  = "action_label";
    public static final String EXTRA_ACTION_INDEX  = "action_index";
    public static final String EXTRA_CHANNEL_ID    = "channel_id";
    public static final String EXTRA_BIG_TEXT      = "big_text";
    public static final String EXTRA_IMAGE_URL     = "image_url";
    public static final String EXTRA_SILENT        = "silent";
    public static final String EXTRA_VIBRATE       = "vibrate";
    public static final String EXTRA_PRIORITY      = "priority";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (ACTION_SHOW_SCHEDULED.equals(action)) {
            showScheduledNotification(context, intent);
        } else if (ACTION_NOTIFICATION_TAPPED.equals(action)) {
            // Forward tap event to MainActivity via LocalBroadcast
            forwardToMainActivity(context, intent);
        } else if (ACTION_NOTIFICATION_ACTION.equals(action)) {
            // Notification action button was pressed
            // Dismiss the notification then forward
            int id = intent.getIntExtra(EXTRA_NOTIF_ID, 0);
            NotificationManagerCompat.from(context).cancel(id);
            forwardToMainActivity(context, intent);
        } else if (ACTION_DISMISS_NOTIFICATION.equals(action)) {
            // Forward dismiss to JS
            forwardToMainActivity(context, intent);
        }
    }

    private void showScheduledNotification(Context context, Intent intent) {
        if (!NotificationHelper.hasPermission(context)) {
            Log.w("WebToApk", "Skipping scheduled notification — permission not granted");
            return;
        }

        int id         = intent.getIntExtra(EXTRA_NOTIF_ID, (int) System.currentTimeMillis());
        String title   = intent.getStringExtra(EXTRA_TITLE);
        String message = intent.getStringExtra(EXTRA_MESSAGE);
        String payload = intent.getStringExtra(EXTRA_PAYLOAD);
        String channel = intent.getStringExtra(EXTRA_CHANNEL_ID);
        if (channel == null) channel = NotificationHelper.CHANNEL_DEFAULT;

        NotificationHelper.show(context, id, title, message, payload, channel,
                false, true, 0, null, null, null, null, null);
        Log.d("WebToApk", "Fired scheduled notification id=" + id);
    }

    /** Sends a local broadcast so MainActivity.java can fire a JS callback. */
    private static void forwardToMainActivity(Context context, Intent intent) {
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context)
                .sendBroadcast(intent);
    }
}
