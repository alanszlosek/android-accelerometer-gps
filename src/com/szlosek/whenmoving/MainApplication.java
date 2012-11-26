package com.szlosek.whenmoving;

import android.app.Application;

class MainApplication extends Application {
	public static MainApplication mInstance;
	
	public static boolean currentState = false; // not running
	
	@Override
	public void onCreate() {
		super.onCreate();
		mInstance = this;
	}
	@Override
	public void onLowMemory() {
	}

}