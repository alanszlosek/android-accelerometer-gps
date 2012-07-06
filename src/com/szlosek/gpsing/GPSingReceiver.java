package com.szlosek.gpsing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Receives AlarmManager RTC_WAKEUPs
public class GPSingReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		// Pass along the intent
		GPSingService.requestLocation(context, intent);
	}
}
