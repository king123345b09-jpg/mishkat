package com.mishkat.anwar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import android.content.Context;

public class AdhanService extends Service {
  static final String CH = "adhan_v1";
  MediaPlayer mp;

  static void start(Context c, String prayer, String name, String file, float vol, String mode) {
    Intent i = new Intent(c, AdhanService.class);
    i.putExtra("prayer", prayer);
    i.putExtra("name", name);
    i.putExtra("file", file);
    i.putExtra("vol", vol);
    i.putExtra("mode", mode);
    try {
      if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    } catch (Exception e) {
      Log.e("AdhanService", "Failed to start service", e);
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
    String file = i.getStringExtra("file");
    String mode = i.getStringExtra("mode");
    float vol = i.getFloatExtra("vol", 1f);
    
    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null) {
      NotificationChannel ch = new NotificationChannel(CH, "Adhan", NotificationManager.IMPORTANCE_HIGH);
      ch.setSound(null, null);
      ch.enableVibration(true);
      nm.createNotificationChannel(ch);
    }
    
    Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
    if (open != null) open.putExtra("adhan_prayer", prayer);
    PendingIntent pOpen = open == null ? null : PendingIntent.getActivity(this, 7, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent pStop = PendingIntent.getService(this, 8, new Intent(this, AdhanService.class).setAction("STOP"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    
    int icon = getResources().getIdentifier("ic_stat_mosque", "drawable", getPackageName());
    
    NotificationCompat.Builder b = new NotificationCompat.Builder(this, CH)
      .setSmallIcon(icon)
      .setContentTitle("حان الآن موعد صلاة " + name)
      .setContentText("اضغط لإغلاق الأذان")
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setOngoing(true)
      .setColor(0xFFD4AF37)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .addAction(0, "إغلاق", pStop);
      
    if (pOpen != null) {
      b.setContentIntent(pOpen);
      if ("full".equals(mode)) b.setFullScreenIntent(pOpen, true);
    }
    
    Notification n = b.build();
    try {
      if (Build.VERSION.SDK_INT >= 29) startForeground(2000, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
      else startForeground(2000, n);
    } catch (Exception e) {
      Log.e("AdhanService", "startForeground failed", e);
      nm.notify(2000, n);
    }
    
    PrayerNotifPlugin.emit("adhanStarted", prayer);
    releasePlayer();
    
    // محاولة تشغيل الصوت من res/raw
    try {
      int rid = file == null ? 0 : getResources().getIdentifier(file, "raw", getPackageName());
      if (rid != 0) {
        AssetFileDescriptor afd = getResources().openRawResourceFd(rid);
        mp = new MediaPlayer();
        mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        afd.close();
        mp.prepare();
        mp.setVolume(vol, vol);
        mp.setOnCompletionListener(m -> stopAll());
        mp.start();
        return START_NOT_STICKY;
      } else {
        Log.w("AdhanService", "Raw file not found: " + file);
      }
    } catch (Exception e) {
      Log.e("AdhanService", "Error playing raw file", e);
    }
    
    // الاحتياطي: نغمة المنبه الافتراضية
    try {
      Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
      if (alert == null) alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
      mp = new MediaPlayer();
      mp.setDataSource(this, alert);
      mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
      mp.prepare();
      mp.setVolume(vol, vol);
      mp.setOnCompletionListener(m -> stopAll());
      mp.start();
      return START_NOT_STICKY;
    } catch (Exception e) {
      Log.e("AdhanService", "Fallback ringtone failed", e);
    }
    
    // إذا فشل كل شيء، أظهر إشعاراً عادياً
    PrayerReceiver.plainNotify(this, name, prayer, mode);
    stopAll();
    return START_NOT_STICKY;
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
