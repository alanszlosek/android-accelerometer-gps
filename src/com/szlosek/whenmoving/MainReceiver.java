package com.szlosek.whenmoving;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Receives AlarmManager RTC_WAKEUPs
public class MainReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		// Pass along the intent
		MainService.requestLocation(context, intent);
	}
}
