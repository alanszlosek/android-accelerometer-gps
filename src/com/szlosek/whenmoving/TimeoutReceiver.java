package com.szlosek.whenmoving;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

// Receives AlarmManager RTC_WAKEUPs ... stops GPS polling
public class TimeoutReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		intent.putExtra("com.szlosek.whenmoving.IntentExtra", 1);
		// Pass along the intent
		MainService.timeoutGPS(context, intent);
	}
}
