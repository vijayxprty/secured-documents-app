package com.docubuds.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import org.json.*;

public final class ReminderReceiver extends BroadcastReceiver {
  public static void schedule(Context c, JSONArray records) throws Exception {
    JSONArray times = new JSONArray();
    long now = System.currentTimeMillis();
    for (int i = 0; i < records.length(); i++) {
      JSONObject r = records.getJSONObject(i);
      if (r.optBoolean("trash") || r.optBoolean("archived")) continue;
      String date = r.optString("expiry");
      if (date.isEmpty()) continue;
      try {
        java.time.LocalDate d = java.time.LocalDate.parse(date);
        for (int advance : new int[] {7, 0}) {
          long at =
              d.minusDays(advance)
                  .atTime(9, 0)
                  .atZone(java.time.ZoneId.systemDefault())
                  .toInstant()
                  .toEpochMilli();
          if (at > now)
            times.put(new JSONObject().put("id", r.getString("id") + "-" + advance).put("at", at));
        }
      } catch (Exception ignored) {
      }
    }
    var prefs = c.getSharedPreferences("schedules", 0);
    JSONArray old = new JSONArray(prefs.getString("times", "[]"));
    AlarmManager m = c.getSystemService(AlarmManager.class);
    for (int i = 0; i < old.length(); i++)
      m.cancel(pending(c, old.getJSONObject(i).getString("id")));
    prefs.edit().putString("times", times.toString()).apply();
    install(c, times);
  }

  private static PendingIntent pending(Context c, String id) {
    Intent in =
        new Intent(c, ReminderReceiver.class)
            .setAction("docubuds.reminder")
            .setData(android.net.Uri.parse("docubuds:reminder/" + id));
    return PendingIntent.getBroadcast(
        c, 0, in, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private static void install(Context c, JSONArray a) throws Exception {
    AlarmManager m = c.getSystemService(AlarmManager.class);
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.getJSONObject(i);
      long at = o.getLong("at");
      if (at > System.currentTimeMillis())
        m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c, o.getString("id")));
    }
  }

  @Override
  public void onReceive(Context c, Intent in) {
    try {
      if (Intent.ACTION_BOOT_COMPLETED.equals(in.getAction())) {
        install(c, new JSONArray(c.getSharedPreferences("schedules", 0).getString("times", "[]")));
        return;
      }
      if (android.os.Build.VERSION.SDK_INT >= 33
          && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
              != PackageManager.PERMISSION_GRANTED) return;
      NotificationManager nm = c.getSystemService(NotificationManager.class);
      nm.createNotificationChannel(
          new NotificationChannel(
              "vault_reminders", "Private reminders", NotificationManager.IMPORTANCE_DEFAULT));
      PendingIntent pi =
          PendingIntent.getActivity(
              c,
              0,
              new Intent(c, MainActivity.class).setAction("reminders"),
              PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
      nm.notify(
          in.getDataString().hashCode(),
          new Notification.Builder(c, "vault_reminders")
              .setSmallIcon(com.docubuds.app.R.drawable.app_icon)
              .setContentTitle("DocuBuds reminder")
              .setContentText("Open your vault to review an upcoming date.")
              .setVisibility(Notification.VISIBILITY_PRIVATE)
              .setContentIntent(pi)
              .setAutoCancel(true)
              .build());
    } catch (Exception ignored) {
    }
  }
}
