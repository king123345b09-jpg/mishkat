package com.mishkat.anwar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;

public class AdhanService extends Service {
  static final String CH = "adhan_v2";
  MediaPlayer mp;
  AudioManager am;
  int prevAlarmVol = -1;

  static void start(Context c, String prayer, String name, String time, String file, float vol, String mode, boolean inapp) {
    Intent i = new Intent(c, AdhanService.class);
    i.putExtra("prayer", prayer);
    i.putExtra("name", name);
    i.putExtra("time", time);
    i.putExtra("file", file);
    i.putExtra("vol", vol);
    i.putExtra("mode", mode);
    i.putExtra("inapp", inapp);
    try {
      if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    } catch (Exception e) {
      PrayerReceiver.plainNotify(c, name, prayer, mode);
    }
  }

  @Override
  public IBinder onBind(Intent i) { return null; }

  @Override
  public int onStartCommand(Intent i, int flags, int startId) {
    if (i == null || "STOP".equals(i.getAction())) {
      stopAll();
      return START_NOT_STICKY;
    }
    String prayer = i.getStringExtra("prayer");
    String name = i.getStringExtra("name");
    String time = i.getStringExtra("time");
    String file = i.getStringExtra("file");
    String mode = i.getStringExtra("mode");
    float vol = i.getFloatExtra("vol", 1f);
    boolean inapp = i.getBooleanExtra("inapp", false);
    if (name == null) name = "";
    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null) {
      NotificationChannel ch = new NotificationChannel(CH, "Adhan", NotificationManager.IMPORTANCE_HIGH);
      ch.setSound(null, null);
      ch.enableVibration(false);
      ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
      nm.createNotificationChannel(ch);
    }
    Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
    if (open != null) open.putExtra("adhan_prayer", prayer);
    PendingIntent pOpen = open == null ? null : PendingIntent.getActivity(this, 7, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent pStop = PendingIntent.getService(this, 8, new Intent(this, AdhanService.class).setAction("STOP"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    int icon = PrayerReceiver.rid(this, "ic_stat_mosque", "drawable");
    RemoteViews rv = new RemoteViews(getPackageName(), PrayerReceiver.rid(this, "notif_adhan", "layout"));
    rv.setTextViewText(PrayerReceiver.rid(this, "a_name", "id"), "صلاة " + name);
    rv.setTextViewText(PrayerReceiver.rid(this, "a_time", "id"), time == null ? "" : time);
    rv.setOnClickPendingIntent(PrayerReceiver.rid(this, "a_stop", "id"), pStop);
    NotificationCompat.Builder b = new NotificationCompat.Builder(this, CH)
      .setSmallIcon(icon)
      .setContentTitle("حان الآن موعد صلاة " + name)
      .setContentText("حيّ على الصلاة")
      .setCustomContentView(rv)
      .setCustomBigContentView(rv)
      .setCustomHeadsUpContentView(rv)
      .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setOngoing(true)
      .setColor(0xFFD4AF37)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .addAction(0, "إيقاف", pStop);
    if (pOpen != null) {
      b.setContentIntent(pOpen);
      if ("full".equals(mode) && !inapp) b.setFullScreenIntent(pOpen, true);
    }
    Notification n = b.build();
    try {
      if (Build.VERSION.SDK_INT >= 29) startForeground(2000, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
      else startForeground(2000, n);
    } catch (Exception e) {
      nm.notify(2000, n);
    }
    if ("full".equals(mode) && !inapp && open != null && (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this))) {
      try {
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(open);
      } catch (Exception e) {}
    }
    PrayerNotifPlugin.emit("adhanStarted", prayer);
    releasePlayer();
    try {
      int rid = file == null ? 0 : getResources().getIdentifier(file, "raw", getPackageName());
      if (rid != 0) {
        AssetFileDescriptor afd = getResources().openRawResourceFd(rid);
        mp = new MediaPlayer();
        mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        afd.close();
        mp.prepare();
        mp.setVolume(1f, 1f);
        applyAlarmVolume(vol);
        mp.setOnCompletionListener(m -> stopAll());
        mp.start();
        return START_NOT_STICKY;
      }
    } catch (Exception e) {}
    PrayerReceiver.plainNotify(this, name, prayer, mode);
    stopAll();
    return START_NOT_STICKY;
  }

  void applyAlarmVolume(float vol) {
    try {
      am = (AudioManager) getSystemService(AUDIO_SERVICE);
      int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
      if (prevAlarmVol < 0) prevAlarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM);
      am.setStreamVolume(AudioManager.STREAM_ALARM, Math.max(1, Math.round(max * Math.max(0f, Math.min(1f, vol)))), 0);
    } catch (Exception e) {}
  }

  void restoreAlarmVolume() {
    try {
      if (am != null && prevAlarmVol >= 0) am.setStreamVolume(AudioManager.STREAM_ALARM, prevAlarmVol, 0);
    } catch (Exception e) {}
    prevAlarmVol = -1;
  }

  void releasePlayer() {
    if (mp != null) {
      try { mp.stop(); } catch (Exception e) {}
      try { mp.release(); } catch (Exception e) {}
      mp = null;
    }
  }

  void stopAll() {
    releasePlayer();
    restoreAlarmVolume();
    PrayerNotifPlugin.emit("adhanEnded", "");
    try { stopForeground(true); } catch (Exception e) {}
    stopSelf();
  }

  @Override
  public void onDestroy() {
    releasePlayer();
    super.onDestroy();
  }
}
