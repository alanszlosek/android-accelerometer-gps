package com.szlosek.whenmoving;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class MainApplication extends Application {
	protected static MainApplication mInstance;
	public static MainService mServiceInstance;
	
	// Status and Preferences
	public static boolean trackingOn = false; // not running
	public static int prefInterval = 0;
	public static int prefTimeout = 0;
	
	// Wake Locks
	protected static PowerManager mPowerManager;
	protected static PowerManager.WakeLock mWakeLock1;
	protected static PowerManager.WakeLock mWakeLock2;
	
	@Override
	public void onCreate() {
		super.onCreate();
		mInstance = this;
		mServiceInstance = null;
		mPowerManager = (PowerManager)getApplicationContext().getSystemService(Context.POWER_SERVICE);
	}
	@Override
	public void onLowMemory() {
	}
	
	public static void Debug(String message) {
		Log.d("WhenMoving", message);
	}
	
	public static MainApplication getInstance() {
		return mInstance;
	}
	
	public void startup() {
		wakeLock1(true);
		Debug("startup");
		Intent i = new Intent(this, MainService.class);
		startService(i);
	}
	
	// Accelerometer Wake Lock
	public static void wakeLock1(boolean up) {
		if (up) {
			mWakeLock1 = mPowerManager.newWakeLock(
				PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
				"WakeLock:Accelerometer"
			);
			mWakeLock1.acquire();
		} else {
			if (mWakeLock1 != null) {
				if (mWakeLock1.isHeld()) {
					mWakeLock1.release();
				}
				mWakeLock1 = null;
			}
		}
	}
	// GPS Wake Lock
	public static void wakeLock2(boolean up) {
		if (up) {
			mWakeLock2 = mPowerManager.newWakeLock(
				PowerManager.PARTIAL_WAKE_LOCK,
				"WakeLock:GPS"
			);
			mWakeLock2.acquire();
		} else {
			if (mWakeLock2 != null) {
				if (mWakeLock2.isHeld()) {
					mWakeLock2.release();
				}
				mWakeLock2 = null;
			}
		}
	}
	
	public static void onPreferenceChange() {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainApplication.getInstance());
		//  Get latest settings, and update accordingly
		boolean newState = sp.getBoolean("pref_onoff", false); // false is off/not-running
		
		prefInterval = Integer.parseInt( sp.getString("pref_interval", "60") );
		prefTimeout = Integer.parseInt( sp.getString("pref_timeout", "30") );
		
		// If we turned off the service, handle that change
		toggleState( newState );
	}
	
	public static void toggleState(boolean newState) {
		Debug(String.format("New state: %s", (newState == true ? "on" : "off")));
		if (MainApplication.trackingOn == true) {
			if (newState == false) {
				MainApplication.trackingOn = false;
				// Graceful shutdown in progress
				Toast.makeText(getInstance(), String.format("Gracefully stopping in %ds", MainApplication.prefInterval), Toast.LENGTH_SHORT).show();
			}
		} else {
			if (newState == true) {
				// Schedule an alarm
				MainApplication.trackingOn = true;
				MainApplication.getInstance().startup();
			}
		}
	}

}
