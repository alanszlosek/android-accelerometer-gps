package com.szlosek.whenmoving;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public class MainApplication extends Application {
	protected static MainApplication mInstance;
	public static MainService mServiceInstance;
	
	public static boolean trackingOn = false; // not running
	
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
	
	public static MainApplication getInstance() {
		return mInstance;
	}
	
	public void startup() {
		wakeLock1(true);
		Intent i = new Intent(this, MainService.class);
		startService(i);
	}
	
	public static void wakeLock1(boolean up) {
		if (up) {
			mWakeLock1 = mPowerManager.newWakeLock(
				PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
				"WakeLock:Accelerometer"
			);
		} else {
			if (mWakeLock1 != null) {
				if (mWakeLock1.isHeld()) {
					mWakeLock1.release();
					mWakeLock1 = null;
				}
			}
		}
	}
	public static void wakeLock2(boolean up) {
		if (up) {
			mWakeLock2 = mPowerManager.newWakeLock(
				PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
				"WakeLock:GPS"
			);
		} else {
			if (mWakeLock2 != null) {
				if (mWakeLock2.isHeld()) {
					mWakeLock2.release();
					mWakeLock2 = null;
				}
			}
		}
	}

}