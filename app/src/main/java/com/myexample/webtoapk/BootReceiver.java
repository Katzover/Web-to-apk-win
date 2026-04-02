package com.myexample.webtoapk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Re-schedules any pending notifications after device reboot,
 * since AlarmManager alarms are cleared on boot.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;

        Log.d("WebToApk", "Boot/update detected — re-scheduling pending notifications");
        SharedPreferences prefs = context.getSharedPreferences("scheduled_notifications", Context.MODE_PRIVATE);
        String json = prefs.getString("list", "[]");

        try {
            JSONArray list = new JSONArray(json);
            long now = System.currentTimeMillis();
            JSONArray surviving = new JSONArray();

            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                long triggerAt = item.getLong("triggerAt");
                if (triggerAt > now) {
                    // Still in the future — re-arm it
                    NotificationHelper.schedule(context,
                            item.getInt("id"),
                            triggerAt,
                            item.optString("title"),
                            item.optString("message"),
                            item.optString("payload"),
                            item.optString("channelId", NotificationHelper.CHANNEL_DEFAULT));
                    surviving.put(item);
                    Log.d("WebToApk", "Re-scheduled notification id=" + item.getInt("id"));
                }
            }

            // Persist only the surviving ones
            prefs.edit().putString("list", surviving.toString()).apply();
        } catch (Exception e) {
            Log.e("WebToApk", "Failed to restore scheduled notifications", e);
        }
    }
}
