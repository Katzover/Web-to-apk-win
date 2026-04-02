package com.myexample.webtoapk;

import android.app.AlarmManager;
import android.app.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Central helper for all notification operations.
 * Keeps channel creation, permission checking, and notification building in one place.
 */
public class NotificationHelper {

    public static final String CHANNEL_DEFAULT  = "webtoapk_default";
    public static final String CHANNEL_SILENT   = "webtoapk_silent";
    public static final String CHANNEL_URGENT   = "webtoapk_urgent";

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    /** Set up all notification channels (idempotent — safe to call multiple times). */
    public static void createChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);

        // Default channel
        NotificationChannel def = new NotificationChannel(
                CHANNEL_DEFAULT, "Notifications", NotificationManager.IMPORTANCE_DEFAULT);
        def.setDescription("General app notifications");
        nm.createNotificationChannel(def);

        // Silent channel (no sound, no vibration)
        NotificationChannel silent = new NotificationChannel(
                CHANNEL_SILENT, "Silent Notifications", NotificationManager.IMPORTANCE_LOW);
        silent.setDescription("Quiet notifications — no sound or vibration");
        silent.setSound(null, null);
        silent.enableVibration(false);
        nm.createNotificationChannel(silent);

        // Urgent channel (heads-up)
        NotificationChannel urgent = new NotificationChannel(
                CHANNEL_URGENT, "Urgent Notifications", NotificationManager.IMPORTANCE_HIGH);
        urgent.setDescription("High-priority notifications that appear as heads-up");
        nm.createNotificationChannel(urgent);
    }

    public static boolean hasPermission(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(ctx,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * Show a notification immediately, with full option set.
     *
     * @param actionsJson  JSON array of {label, payload} objects for action buttons (max 3), or null
     * @param bigText      Long text body (replaces message in expanded view), or null
     * @param imageUrl     Remote URL for a big-picture style, or null
     * @param groupKey     Optional notification group key for stacking
     * @param payload      Arbitrary string passed back to JS when the notification is tapped
     */
    public static void show(Context ctx, int id, String title, String message,
                            String payload, String channelId,
                            boolean silent, boolean autoCancel,
                            int priority, String actionsJson,
                            String bigText, String imageUrl,
                            String groupKey, boolean[] vibrationPattern) {

        if (channelId == null || channelId.isEmpty()) channelId = CHANNEL_DEFAULT;
        if (silent) channelId = CHANNEL_SILENT;

        String finalChannelId = channelId;

        // Tap intent — opens app and broadcasts the tap event
        Intent tapIntent = new Intent(ctx, NotificationReceiver.class);
        tapIntent.setAction(NotificationReceiver.ACTION_NOTIFICATION_TAPPED);
        tapIntent.putExtra(NotificationReceiver.EXTRA_NOTIF_ID, id);
        tapIntent.putExtra(NotificationReceiver.EXTRA_PAYLOAD, payload);
        tapIntent.putExtra(NotificationReceiver.EXTRA_TITLE, title);
        tapIntent.putExtra(NotificationReceiver.EXTRA_MESSAGE, message);
        PendingIntent tapPending = PendingIntent.getBroadcast(ctx, id,
                tapIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Main activity open intent (what the user actually sees)
        Intent openIntent = new Intent(ctx, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (payload != null) openIntent.putExtra(NotificationReceiver.EXTRA_PAYLOAD, payload);
        PendingIntent openPending = PendingIntent.getActivity(ctx, id * 100,
                openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Dismiss intent
        Intent dismissIntent = new Intent(ctx, NotificationReceiver.class);
        dismissIntent.setAction(NotificationReceiver.ACTION_DISMISS_NOTIFICATION);
        dismissIntent.putExtra(NotificationReceiver.EXTRA_NOTIF_ID, id);
        dismissIntent.putExtra(NotificationReceiver.EXTRA_PAYLOAD, payload);
        PendingIntent dismissPending = PendingIntent.getBroadcast(ctx, id * 200,
                dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, finalChannelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title != null ? title : "")
                .setContentText(message != null ? message : "")
                .setPriority(priority != 0 ? priority : NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openPending)
                .setDeleteIntent(dismissPending)
                .setAutoCancel(autoCancel);

        // Big text style
        if (bigText != null && !bigText.isEmpty()) {
            builder.setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(bigText)
                    .setSummaryText(message));
        }

        // Grouping
        if (groupKey != null && !groupKey.isEmpty()) {
            builder.setGroup(groupKey);
        }

        // Action buttons (parsed from JSON)
        if (actionsJson != null && !actionsJson.isEmpty()) {
            try {
                JSONArray actions = new JSONArray(actionsJson);
                for (int i = 0; i < Math.min(actions.length(), 3); i++) {
                    JSONObject a = actions.getJSONObject(i);
                    String label = a.optString("label", "Action");
                    String actionPayload = a.optString("payload", "");

                    Intent actionIntent = new Intent(ctx, NotificationReceiver.class);
                    actionIntent.setAction(NotificationReceiver.ACTION_NOTIFICATION_ACTION);
                    actionIntent.putExtra(NotificationReceiver.EXTRA_NOTIF_ID, id);
                    actionIntent.putExtra(NotificationReceiver.EXTRA_ACTION_LABEL, label);
                    actionIntent.putExtra(NotificationReceiver.EXTRA_ACTION_INDEX, i);
                    actionIntent.putExtra(NotificationReceiver.EXTRA_PAYLOAD, actionPayload);
                    PendingIntent actionPending = PendingIntent.getBroadcast(ctx,
                            id * 10 + i, actionIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                    builder.addAction(0, label, actionPending);
                }
            } catch (Exception e) {
                Log.e("WebToApk", "Failed to parse notification actions: " + actionsJson, e);
            }
        }

        // Image (async — show text-only first, then update with image)
        if (imageUrl != null && !imageUrl.isEmpty()) {
            final int finalId = id;
            final NotificationCompat.Builder finalBuilder = builder;
            final String finalChannel = finalChannelId;
            executor.submit(() -> {
                Bitmap bmp = fetchBitmap(imageUrl);
                if (bmp != null) {
                    finalBuilder.setStyle(new NotificationCompat.BigPictureStyle()
                            .bigPicture(bmp)
                            .setSummaryText(message));
                    postNotification(ctx, finalId, finalBuilder.build());
                }
            });
        }

        postNotification(ctx, id, builder.build());
    }

    private static void postNotification(Context ctx, int id, android.app.Notification n) {
        try {
            NotificationManagerCompat.from(ctx).notify(id, n);
        } catch (SecurityException e) {
            Log.e("WebToApk", "No notification permission", e);
        }
    }

    private static Bitmap fetchBitmap(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoInput(true);
            conn.connect();
            InputStream in = conn.getInputStream();
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            Log.e("WebToApk", "Failed to fetch notification image: " + urlStr, e);
            return null;
        }
    }

    /** Schedule a notification via AlarmManager. Returns false if exact alarms aren't permitted. */
    public static boolean schedule(Context ctx, int id, long triggerAtMillis,
                                   String title, String message, String payload,
                                   String channelId) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(ctx, NotificationReceiver.class);
        intent.setAction(NotificationReceiver.ACTION_SHOW_SCHEDULED);
        intent.putExtra(NotificationReceiver.EXTRA_NOTIF_ID, id);
        intent.putExtra(NotificationReceiver.EXTRA_TITLE, title);
        intent.putExtra(NotificationReceiver.EXTRA_MESSAGE, message);
        intent.putExtra(NotificationReceiver.EXTRA_PAYLOAD, payload);
        intent.putExtra(NotificationReceiver.EXTRA_CHANNEL_ID, channelId);

        PendingIntent pi = PendingIntent.getBroadcast(ctx, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!am.canScheduleExactAlarms()) {
                    // Fall back to inexact (fires within ~1-5 min window)
                    am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
                    Log.w("WebToApk", "Exact alarm permission not granted; using inexact alarm");
                    return false;
                }
            }
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            Log.d("WebToApk", "Scheduled notification id=" + id + " at " + triggerAtMillis);
            return true;
        } catch (SecurityException e) {
            Log.e("WebToApk", "Cannot schedule exact alarm", e);
            am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            return false;
        }
    }

    /** Cancel a previously scheduled or shown notification by ID. */
    public static void cancel(Context ctx, int id) {
        // Cancel pending alarm
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(ctx, NotificationReceiver.class);
        intent.setAction(NotificationReceiver.ACTION_SHOW_SCHEDULED);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, id, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
        // Cancel shown notification
        NotificationManagerCompat.from(ctx).cancel(id);
        Log.d("WebToApk", "Cancelled notification id=" + id);
    }

    /** Cancel all notifications from this app. */
    public static void cancelAll(Context ctx) {
        NotificationManagerCompat.from(ctx).cancelAll();
        Log.d("WebToApk", "Cancelled all notifications");
    }
}
