package com.mishkat.anwar;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(PrayerNotifPlugin.class);
    super.onCreate(savedInstanceState);
    handleAdhan(getIntent());
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
