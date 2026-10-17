package com.mishkat.anwar;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;

public class PrayerReceiver extends BroadcastReceiver {
  static final String CH = "persist_v2";

  @Override
  public void onReceive(Context c, Intent i) {
    show(c);
    scheduleNext(c);
  }

  static JSONObject nextEvent(Context c) {
    try {
      JSONArray a = new JSONArray(c.getSharedPreferences("mishkat", 0).getString("events", "[]"));
      long now = System.currentTimeMillis();
      for (int k = 0; k < a.length(); k++) {
        JSONObject o = a.getJSONObject(k);
        if (o.getLong("t") > now + 1000) return o;
      }
    } catch (Exception e) {}
    return null;
  }

  static void show(Context c) {
    NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    JSONObject e = nextEvent(c);
    if (e == null) { nm.cancel(1000); return; }
    if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null) {
      NotificationChannel ch = new NotificationChannel(CH, "Next prayer", NotificationManager.IMPORTANCE_LOW);
      ch.setShowBadge(false);
      nm.createNotificationChannel(ch);
    }
    try {
      Intent li = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
      PendingIntent pi = li == null ? null : PendingIntent.getActivity(c, 0, li, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      int icon = c.getResources().getIdentifier("ic_stat_mosque", "drawable", c.getPackageName());
      NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH)
        .setSmallIcon(icon)
        .setContentTitle("الصلاة القادمة: " + e.getString("name") + " " + e.getString("time"))
        .setContentText(e.getString("hijri"))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(true)
        .setWhen(e.getLong("t"))
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        .setColor(0xFFD4AF37)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
      if (pi != null) b.setContentIntent(pi);
      nm.notify(1000, b.build());
    } catch (Exception ex) {}
  }

  static void scheduleNext(Context c) {
    AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    PendingIntent pi = PendingIntent.getBroadcast(c, 1, new Intent(c, PrayerReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    JSONObject e = nextEvent(c);
    if (e == null) { am.cancel(pi); return; }
    try {
      long t = e.getLong("t") + 1500;
      if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
      else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
    } catch (Exception ex) {}
  }
}
