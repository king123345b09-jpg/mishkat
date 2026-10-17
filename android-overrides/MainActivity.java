package com.mishkat.anwar;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(PrayerNotifPlugin.class);
    super.onCreate(savedInstanceState);
    handleAdhan(getIntent());
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
