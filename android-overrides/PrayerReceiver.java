package com.mishkat.anwar;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;

public class PrayerReceiver extends BroadcastReceiver {
  static final String CH = "persist_v3";

  @Override
  public void onReceive(Context c, Intent i) {
    String act = i == null ? null : i.getAction();
    if ("TEST".equals(act)) {
      AdhanService.start(c, i.getStringExtra("prayer"), i.getStringExtra("name"), i.getStringExtra("time"), i.getStringExtra("file"), i.getFloatExtra("vol", 1f), i.getStringExtra("mode"), false);
      return;
    }
    if (act == null) fire(c);
    show(c);
    scheduleNext(c);
  }

  static PendingIntent openPi(Context c, int code) {
    Intent li = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
    return li == null ? null : PendingIntent.getActivity(c, code, li, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
        if (o.optBoolean("pre", false) && tp > lastPre && tp <= now + 3000 && now - tp < 300000) {
          if (tp > maxP) maxP = tp;
          preNotify(c, o.optString("preBody"));
        }
        if (t > last && t <= now + 3000 && now - t < 300000) {
          if (t > maxT) maxT = t;
          if (o.optBoolean("adhan", false)) {
            AdhanService.start(c, o.optString("p"), o.optString("name"), o.optString("time"), o.optString("file"), (float) o.optDouble("vol", 1.0), o.optString("mode", "notif"), false);
          }
        }
      }
      SharedPreferences.Editor ed = sp.edit();
      if (maxT > last) ed.putLong("lastFired", maxT);
      if (maxP > lastPre) ed.putLong("lastPre", maxP);
      ed.apply();
    } catch (Exception e) {}
  }

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
        .setContentText(body)
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

  static void fillViews(Context c, RemoteViews rv, JSONObject e, long t, boolean big) throws Exception {
    rv.setTextViewText(rid(c, "n_hijri", "id"), e.optString("hijri"));
    String city = e.optString("city", "");
    rv.setTextViewText(rid(c, "n_city", "id"), city);
    int vis = city.length() > 0 ? View.VISIBLE : View.GONE;
    rv.setViewVisibility(rid(c, "n_city", "id"), vis);
    rv.setViewVisibility(rid(c, "n_sep", "id"), vis);
    rv.setTextViewText(rid(c, "n_name", "id"), "صلاة " + e.getString("name"));
    rv.setTextViewText(rid(c, "n_time", "id"), e.getString("time"));
    int cid = rid(c, "n_chrono", "id");
    if (Build.VERSION.SDK_INT >= 24) rv.setChronometerCountDown(cid, true);
    rv.setChronometer(cid, SystemClock.elapsedRealtime() + (t - System.currentTimeMillis()), null, true);
    try {
      String bg = e.optString("bg", "");
      if (bg.length() > 0) rv.setInt(rid(c, "n_root", "id"), "setBackgroundColor", Color.parseColor(bg));
    } catch (Exception ex) {}
    try {
      String fg = e.optString("fg", "");
      if (fg.length() > 0) {
        int col = Color.parseColor(fg);
        rv.setTextColor(cid, col);
        rv.setInt(rid(c, "n_sep", "id"), "setBackgroundColor", (col & 0x00FFFFFF) | 0x99000000);
      }
    } catch (Exception ex) {}
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
      String pkg = c.getPackageName();
      long t = e.getLong("t");
      RemoteViews rv = new RemoteViews(pkg, rid(c, "notif_prayer", "layout"));
      RemoteViews rvBig = new RemoteViews(pkg, rid(c, "notif_prayer_big", "layout"));
      fillViews(c, rv, e, t, false);
      fillViews(c, rvBig, e, t, true);
      Intent li = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
      PendingIntent pi = li == null ? null : PendingIntent.getActivity(c, 0, li, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      PendingIntent pDel = PendingIntent.getBroadcast(c, 3, new Intent(c, PrayerReceiver.class).setAction("REPOST"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      int icon = rid(c, "ic_stat_mosque", "drawable");
      NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH)
        .setSmallIcon(icon)
        .setContentTitle("الصلاة القادمة: " + e.getString("name") + " " + e.getString("time"))
        .setContentText(e.getString("hijri"))
        .setCustomContentView(rv)
        .setCustomBigContentView(rvBig)
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
      PendingIntent show = openPi(c, 11);
      if (show != null) am.setAlarmClock(new AlarmManager.AlarmClockInfo(best, show), pi);
      else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
    } catch (Exception ex) {
      try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi); } catch (Exception e2) {}
    }
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
}
