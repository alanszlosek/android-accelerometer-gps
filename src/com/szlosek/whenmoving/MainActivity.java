package com.szlosek.whenmoving;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;

import java.lang.Thread;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class MainActivity extends MapActivity {
	SharedPreferences sharedPreferences;
	MapView myMapView = null;
	MainItemizedOverlay myItemizedOverlays = null;
	List<Overlay> myOverlays = null;
	protected Drawable myDrawable = null;
	
	public static int prefInterval = 0;
	public static int prefTimeout = 0;

	public void Debug(String message) {
		Log.d("WhenMoving", message);
	}


	// Called by service when it's up, so this activity can bind to it
	public static void ready() {
		// This needs to wait for the service to come online
		//bindService(new Intent(MainActivity.this, MainService.class), MainActivity.this.mConnection, Context.BIND_AUTO_CREATE);
	}
	
	protected void toggleState(boolean newState) {
		Debug(String.format("New state: %s", (newState == true ? "on" : "off")));
		if (MainApplication.trackingOn == true) {
			if (newState == false) {
				MainApplication.trackingOn = false;
				// Alarms are active. Service may be running right now
				// if so, let it finish, then simply skip the call to re-schedule
				//stopGPSing();
			}
		} else {
			if (newState == true) {
				// Schedule an alarm
				startGPSing(); // will replace this later
			}
		}
	}

	// Activity 
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		myMapView = (MapView) findViewById(R.id.mapview);
		myMapView.setBuiltInZoomControls(true);
		myOverlays = myMapView.getOverlays();
		
		myDrawable = this.getResources().getDrawable(R.drawable.marker);
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		
		Debug("Activity started");
	}

	@Override
	protected void onResume() {
		super.onResume();

		preferenceChange2();
		
		
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Debug("Creating options menu");
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return true;
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	// Maps Methods
	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
			case R.id.menu_refresh:
				showMarkers();
				return true;
			case R.id.menu_settings:
				Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}


	private void startGPSing() {
		MainApplication.trackingOn = true;
		
		MainApplication.getInstance().startup();
	}
	
/*
	public static void preferenceChange() {
		MainActivity.this.preferenceChange2();
	}
*/
	public void preferenceChange2() {
		//  Get latest settings, and update accordingly
		boolean newState = sharedPreferences.getBoolean("pref_onoff", false); // false is off/not-running
		prefInterval = Integer.parseInt( sharedPreferences.getString("pref_interval", "60") );
		prefTimeout = Integer.parseInt( sharedPreferences.getString("pref_timeout", "30") );
		
		// If we turned off the service, handle that change
		toggleState( newState );
	}
	
	protected void showMarkers() {
		SQLiteOpenHelper dbHelper = new DatabaseHelper(this);
		SQLiteDatabase db = dbHelper.getReadableDatabase();
		
		// Clear layer/markers
		myOverlays.clear();
		myItemizedOverlays = new MainItemizedOverlay(myDrawable);
		
		// Get 10 newest locations
		Cursor c = db.query("locations", null, null, null, null, null, "milliseconds DESC", "10");
		int iMilliseconds = c.getColumnIndex("milliseconds");
		int iLongitude = c.getColumnIndex("longitude");
		int iLatitude = c.getColumnIndex("latitude");
		
		while (c.moveToNext()) {
			Double lo, la;
			la = new Double(c.getFloat(iLatitude) * 1E6);
			lo = new Double(c.getFloat(iLongitude) * 1E6);
			GeoPoint gp = new GeoPoint(
				la.intValue(),
				lo.intValue()
			);
			Date d = new Date( c.getLong(iMilliseconds) );
			
			OverlayItem overlayItem = new OverlayItem(
				gp,
				DateFormat.format("MM/d h:mm:ss aa", d).toString(),
				String.format("Lon: %f\nLat: %f", c.getFloat(iLongitude), c.getFloat(iLatitude))
			);
			myItemizedOverlays.addOverlay(overlayItem);
		}
		
		myOverlays.add(myItemizedOverlays);
		/*
		// Redraw these GeoPoints on the map, with path lines, and pretty colors
		mapController.setZoom(21);
		mapController.setCenter(gp);


		OverlayItem overlayItem = new OverlayItem(gp, "Hola, Mundo!", "I'm in Mexico City!");
		myItemizedOverlays.addOverlay(overlayItem);
		myOverlays.add(myItemizedOverlays);
	*/
	}

}
