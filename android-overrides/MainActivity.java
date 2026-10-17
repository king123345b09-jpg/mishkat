package com.mishkat.anwar;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onResume() {
    super.onResume();
    try { getBridge().getWebView().getSettings().setTextZoom(100); } catch (Exception e) {}
  }
}
