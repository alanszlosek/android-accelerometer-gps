package com.szlosek.gpsing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

// Receives AlarmManager RTC_WAKEUPs ... stops GPS polling
public class GPSTimeoutReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		intent.putExtra("com.szlosek.gpsing.IntentExtra", 1);
		// Pass along the intent
		GPSingService.timeoutGPS(context, intent);
	}
}
