package com.mishkat.anwar;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;

public class PrayerReceiver extends BroadcastReceiver {
  static final String CH = "persist_v3";

  @Override
  public void onReceive(Context c, Intent i) {
    if (i == null || i.getAction() == null) fire(c);
    show(c);
    scheduleNext(c);
  }

  static void fire(Context c) {
    try {
      SharedPreferences sp = c.getSharedPreferences("mishkat", 0);
      JSONArray a = new JSONArray(sp.getString("events", "[]"));
      long now = System.currentTimeMillis();
      long last = sp.getLong("lastFired", 0);
      long lastPre = sp.getLong("lastPre", 0);
      long maxT = last;
      long maxP = lastPre;
      for (int k = 0; k < a.length(); k++) {
        JSONObject o = a.getJSONObject(k);
        long t = o.getLong("t");
        long tp = t - 1800000L;
        // إشعار التنبيه المسبق (30 دقيقة)
        if (o.optBoolean("pre", false) && tp > lastPre && tp <= now + 3000 && now - tp < 300000) {
          if (tp > maxP) maxP = tp;
          preNotify(c, o.optString("preBody"));
        }
        // إشعار الأذان
        if (t > last && t <= now + 3000 && now - t < 300000) {
          if (t > maxT) maxT = t;
          if (o.optBoolean("adhan", false)) {
            AdhanService.start(c, o.optString("p"), o.optString("name"), o.optString("file"), (float) o.optDouble("vol", 1.0), o.optString("mode", "notif"));
          }
        }
      }
      SharedPreferences.Editor ed = sp.edit();
      if (maxT > last) ed.putLong("lastFired", maxT);
      if (maxP > lastPre) ed.putLong("lastPre", maxP);
      ed.apply();
    } catch (Exception e) {}
  }

  // إشعار التنبيه المسبق (يظهر كإشعار عادي)
  static void preNotify(Context c, String body) {
    try {
      NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
      if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("prealert_v1") == null) {
        nm.createNotificationChannel(new NotificationChannel("prealert_v1", "Pre-prayer alert", NotificationManager.IMPORTANCE_HIGH));
      }
      Intent li = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
      PendingIntent pi = li == null ? null : PendingIntent.getActivity(c, 10, li, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      NotificationCompat.Builder b = new NotificationCompat.Builder(c, "prealert_v1")
        .setSmallIcon(rid(c, "ic_stat_mosque", "drawable"))
        .setContentTitle("مشكاة الأنوار")
        .setContentText(body) // النص: متبقي 30 دقيقة على صلاة...
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setColor(0xFFD4AF37);
      if (pi != null) b.setContentIntent(pi);
      nm.notify(3000, b.build());
    } catch (Exception e) {}
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

  static int rid(Context c, String name, String type) {
    return c.getResources().getIdentifier(name, type, c.getPackageName());
  }

  // الإشعار الدائم (الذي يظهر فيه العد التنازلي)
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
      String pkg = c.getPackageName();
      long t = e.getLong("t");
      RemoteViews rv = new RemoteViews(pkg, rid(c, "notif_prayer", "layout"));
      rv.setTextViewText(rid(c, "n_name", "id"), e.getString("name") + "  " + e.getString("time"));
      rv.setTextViewText(rid(c, "n_sub", "id"), e.getString("hijri"));
      
      int cid = rid(c, "n_chrono", "id");
      if (Build.VERSION.SDK_INT >= 24) rv.setChronometerCountDown(cid, true);
      rv.setChronometer(cid, SystemClock.elapsedRealtime() + (t - System.currentTimeMillis()), null, true);
      
      Intent li = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
      PendingIntent pi = li == null ? null : PendingIntent.getActivity(c, 0, li, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      PendingIntent pDel = PendingIntent.getBroadcast(c, 3, new Intent(c, PrayerReceiver.class).setAction("REPOST"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      
      int icon = rid(c, "ic_stat_mosque", "drawable");
      NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH)
        .setSmallIcon(icon)
        .setContentTitle("الصلاة القادمة: " + e.getString("name") + " " + e.getString("time"))
        .setContentText(e.getString("hijri"))
        .setCustomContentView(rv)
        .setCustomBigContentView(rv)
        .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setAutoCancel(false)
        .setColor(0xFFD4AF37)
        .setDeleteIntent(pDel)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
      if (pi != null) b.setContentIntent(pi);
      nm.notify(1000, b.build());
    } catch (Exception ex) {}
  }

  static void scheduleNext(Context c) {
    AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    PendingIntent pi = PendingIntent.getBroadcast(c, 1, new Intent(c, PrayerReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    long best = Long.MAX_VALUE;
    long now = System.currentTimeMillis();
    
    try {
      JSONArray a = new JSONArray(c.getSharedPreferences("mishkat", 0).getString("events", "[]"));
      for (int k = 0; k < a.length(); k++) {
        JSONObject o = a.getJSONObject(k);
        long t = o.getLong("t");
        if (t > now + 1000 && t < best) best = t;
        if (o.optBoolean("pre", false)) {
          long tp = t - 1800000L;
          if (tp > now + 1000 && tp < best) best = tp;
        }
      }
    } catch (Exception e) {}
    
    if (best == Long.MAX_VALUE) { am.cancel(pi); return; }
    
    try {
      if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
      else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
    } catch (Exception ex) {}
  }

  static void plainNotify(Context c, String name, String prayer, String mode) {
    try {
      NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
      if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("adhan_plain") == null) {
        nm.createNotificationChannel(new NotificationChannel("adhan_plain", "Adhan alert", NotificationManager.IMPORTANCE_HIGH));
      }
      Intent li = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
      if (li != null) li.putExtra("adhan_prayer", prayer);
      PendingIntent pi = li == null ? null : PendingIntent.getActivity(c, 9, li, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      
      NotificationCompat.Builder b = new NotificationCompat.Builder(c, "adhan_plain")
        .setSmallIcon(rid(c, "ic_stat_mosque", "drawable"))
        .setContentTitle("حان الآن موعد صلاة " + name)
        .setContentText("حيّ على الصلاة")
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setAutoCancel(true)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setColor(0xFFD4AF37);
      if (pi != null) {
        b.setContentIntent(pi);
        if ("full".equals(mode)) b.setFullScreenIntent(pi, true);
      }
      nm.notify(2001, b.build());
    } catch (Exception e) {}
  }

  public static void requestIgnoreBatteryOptimizations(Context c) {
    if (Build.VERSION.SDK_INT >= 23) {
      PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
      if (!pm.isIgnoringBatteryOptimizations(c.getPackageName())) {
        try {
          Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
          intent.setData(Uri.parse("package:" + c.getPackageName()));
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          c.startActivity(intent);
        } catch (Exception e) {}
      }
    }
  }
}
