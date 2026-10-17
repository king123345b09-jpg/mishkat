package com.mishkat.anwar;

import com.getcapacitor.JSArray;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "PrayerNotif")
public class PrayerNotifPlugin extends Plugin {
  @PluginMethod
  public void setEvents(PluginCall call) {
    JSArray a = call.getArray("events");
    getContext().getSharedPreferences("mishkat", 0).edit().putString("events", a == null ? "[]" : a.toString()).apply();
    PrayerReceiver.show(getContext());
    PrayerReceiver.scheduleNext(getContext());
    call.resolve();
  }
}
