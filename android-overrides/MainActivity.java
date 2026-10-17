package com.mishkat.anwar;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(PrayerNotifPlugin.class);
    super.onCreate(savedInstanceState);
  }

  @Override
  public void onResume() {
    super.onResume();
    try { getBridge().getWebView().getSettings().setTextZoom(100); } catch (Exception e) {}
  }
}
