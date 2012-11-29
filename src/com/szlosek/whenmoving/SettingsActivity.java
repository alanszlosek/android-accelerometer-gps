package com.szlosek.whenmoving;

import android.preference.PreferenceActivity;
import android.os.Bundle;
import android.util.Log;

public class SettingsActivity extends PreferenceActivity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.layout.settings);
	}
	
	// Is there an onPause ... some other way I can update the MainActivity if this activity goes away
	@Override
	protected void onPause() {
		super.onPause();
		MainApplication.onPreferenceChange();
	}
}
