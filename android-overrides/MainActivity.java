package com.mishkat.anwar;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import androidx.activity.OnBackPressedCallback;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(PrayerNotifPlugin.class);
    super.onCreate(savedInstanceState);
    handleAdhan(getIntent());
    requestAllPermissions();

    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        try {
          getBridge().getWebView().evaluateJavascript(
            "(function(){try{return window.__hardBack?window.__hardBack():false}catch(e){return false}})()",
            value -> {
              if (!"true".equals(value)) {
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
              }
            });
        } catch (Exception e) {
          setEnabled(false);
          getOnBackPressedDispatcher().onBackPressed();
          setEnabled(true);
        }
      }
    });
  }

  private void requestAllPermissions() {
    // 1. صلاحية الإشعارات (أندرويد 13+)
    if (Build.VERSION.SDK_INT >= 33) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
      }
    }
    // 2. صلاحية الموقع
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 102);
    }
    // 3. استثناء من تحسين البطارية
    PrayerReceiver.requestIgnoreBatteryOptimizations(this);
    
    // 4. صلاحية المنبهات الدقيقة (أندرويد 12+)
    if (Build.VERSION.SDK_INT >= 31) {
      try {
        android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        if (!am.canScheduleExactAlarms()) {
          Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()));
          startActivity(i);
        }
      } catch (Exception e) {}
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    handleAdhan(intent);
  }

  private void handleAdhan(Intent i) {
    if (i != null && i.hasExtra("adhan_prayer")) {
      if (Build.VERSION.SDK_INT >= 27) {
        setShowWhenLocked(true);
        setTurnScreenOn(true);
      }
      PrayerNotifPlugin.emit("adhanStarted", i.getStringExtra("adhan_prayer"));
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    try { getBridge().getWebView().getSettings().setTextZoom(100); } catch (Exception e) {}
  }
}
