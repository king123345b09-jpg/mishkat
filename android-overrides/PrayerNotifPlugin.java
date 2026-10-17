package com.mishkat.anwar;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

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

  static JSObject res(boolean ok) {
    JSObject o = new JSObject();
    o.put("ok", ok);
    return o;
  }

  @PluginMethod
  public void setEvents(PluginCall call) {
    JSArray a = call.getArray("events");
    Context c = getContext();
    c.getSharedPreferences("mishkat", 0).edit().putString("events", a == null ? "[]" : a.toString()).apply();
    PrayerReceiver.show(c);
    PrayerReceiver.scheduleNext(c);
    AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    JSObject r = new JSObject();
    r.put("exact", Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms());
    call.resolve(r);
  }

  @PluginMethod
  public void startAdhan(PluginCall call) {
    Float v = call.getFloat("vol");
    AdhanService.start(getContext(), call.getString("prayer"), call.getString("name"), call.getString("file"), v == null ? 1f : v, call.getString("mode", "notif"));
    call.resolve();
  }

  @PluginMethod
  public void stopAdhan(PluginCall call) {
    try { getContext().startService(new Intent(getContext(), AdhanService.class).setAction("STOP")); } catch (Exception e) {}
    call.resolve();
  }

  @PluginMethod
  public void downloadAdhan(PluginCall call) {
    final String url = call.getString("url");
    final String name = call.getString("name");
    new Thread(() -> {
      try {
        HttpURLConnection cn = (HttpURLConnection) new URL(url).openConnection();
        cn.setConnectTimeout(15000);
        cn.setReadTimeout(30000);
        cn.setInstanceFollowRedirects(true);
        String ct = cn.getContentType();
        if (cn.getResponseCode() != 200 || (ct != null && ct.startsWith("text"))) { call.resolve(res(false)); return; }
        File tmp = new File(getContext().getFilesDir(), name + ".tmp");
        try (InputStream in = cn.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
          byte[] buf = new byte[16384];
          int n;
          while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        if (tmp.length() < 50000) { tmp.delete(); call.resolve(res(false)); return; }
        File dst = new File(getContext().getFilesDir(), name);
        if (dst.exists()) dst.delete();
        tmp.renameTo(dst);
        call.resolve(res(true));
      } catch (Exception e) {
        call.resolve(res(false));
      }
    }).start();
  }

  @PluginMethod
  public void saveAdhan(PluginCall call) {
    try {
      byte[] d = Base64.decode(call.getString("data"), Base64.DEFAULT);
      try (FileOutputStream o = new FileOutputStream(new File(getContext().getFilesDir(), call.getString("name")))) {
        o.write(d);
      }
      call.resolve(res(true));
    } catch (Exception e) {
      call.resolve(res(false));
    }
  }

  @PluginMethod
  public void openExactSettings(PluginCall call) {
    try {
      if (Build.VERSION.SDK_INT >= 31) {
        Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getContext().getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
      }
    } catch (Exception e) {}
    call.resolve();
  }
}
