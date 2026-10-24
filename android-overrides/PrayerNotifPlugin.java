package com.mishkat.anwar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.core.app.NotificationManagerCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONArray;

@CapacitorPlugin(name = "PrayerNotif")
public class PrayerNotifPlugin extends Plugin {
  static PrayerNotifPlugin instance;
  static String pendingPrayer = null;

  @Override
  public void load() {
    instance = this;
    if (pendingPrayer != null) {
      String p = pendingPrayer;
      pendingPrayer = null;
      emit("adhanStarted", p);
    }
  }

  static void emit(String ev, String prayer) {
    if (instance == null) {
      if ("adhanStarted".equals(ev)) pendingPrayer = prayer;
      return;
    }
    JSObject o = new JSObject();
    o.put("prayer", prayer);
    instance.notifyListeners(ev, o, true);
  }

  @PluginMethod
  public void setEvents(PluginCall call) {
    JSArray a = call.getArray("events");
    Context c = getContext();
    c.getSharedPreferences("mishkat", 0).edit().putString("events", a == null ? "[]" : a.toString()).apply();
    PrayerReceiver.show(c);
    PrayerReceiver.scheduleNext(c);
    call.resolve();
  }

  @PluginMethod
  public void startAdhan(PluginCall call) {
    Float v = call.getFloat("vol");
    AdhanService.start(getContext(), call.getString("prayer"), call.getString("name"), call.getString("time"), call.getString("file"), v == null ? 1f : v, call.getString("mode", "notif"));
    call.resolve();
  }

  @PluginMethod
  public void stopAdhan(PluginCall call) {
    try { getContext().startService(new Intent(getContext(), AdhanService.class).setAction("STOP")); } catch (Exception e) {}
    call.resolve();
  }

  @PluginMethod
  public void scheduleTest(PluginCall call) {
    Context c = getContext();
    Integer d = call.getInt("delay");
    long when = System.currentTimeMillis() + (d == null ? 10 : d) * 1000L;
    Intent i = new Intent(c, PrayerReceiver.class).setAction("TEST");
    i.putExtra("prayer", call.getString("prayer"));
    i.putExtra("name", call.getString("name"));
    i.putExtra("time", call.getString("time"));
    i.putExtra("file", call.getString("file"));
    i.putExtra("mode", call.getString("mode", "notif"));
    Float v = call.getFloat("vol");
    i.putExtra("vol", v == null ? 1f : v);
    PendingIntent pi = PendingIntent.getBroadcast(c, 5, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    try {
      PendingIntent show = PrayerReceiver.openPi(c, 12);
      if (show != null) am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), pi);
      else am.set(AlarmManager.RTC_WAKEUP, when, pi);
    } catch (Exception e) {
      try { am.set(AlarmManager.RTC_WAKEUP, when, pi); } catch (Exception e2) {}
    }
    call.resolve();
  }

  @PluginMethod
  public void status(PluginCall call) {
    Context c = getContext();
    JSObject r = new JSObject();
    r.put("notif", NotificationManagerCompat.from(c).areNotificationsEnabled());
    r.put("overlay", Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c));
    int n = 0;
    try { n = new JSONArray(c.getSharedPreferences("mishkat", 0).getString("events", "[]")).length(); } catch (Exception e) {}
    PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
    r.put("battery", Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(c.getPackageName()));
    r.put("count", n);
    call.resolve(r);
  }

  @PluginMethod
  public void openOverlaySettings(PluginCall call) {
    try {
      Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getContext().getPackageName()));
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      getContext().startActivity(i);
    } catch (Exception e) {}
    call.resolve();
  }

  @PluginMethod
  public void openBatterySettings(PluginCall call) {
    try {
      Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      getContext().startActivity(i);
    } catch (Exception e) {}
    call.resolve();
  }

  @PluginMethod
  public void openAppSettings(PluginCall call) {
    try {
      Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()));
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      getContext().startActivity(i);
    } catch (Exception e) {}
    call.resolve();
  }
}
