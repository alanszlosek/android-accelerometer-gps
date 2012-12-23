package com.szlosek.whenmoving;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class MainActivity extends MapActivity {
	MapView myMapView = null;
	protected Drawable myDrawable = null;
	protected MyLocationOverlay myLocation;

	public void Debug(String message) {
		Log.d("WhenMoving", message);
	}
	
	// Activity 
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		myMapView = (MapView) findViewById(R.id.mapview);
		myMapView.setBuiltInZoomControls(true);
		//myOverlays = myMapView.getOverlays();
		
		//myDrawable = this.getResources().getDrawable(R.drawable.marker);
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		
		Debug("Activity started");
	}

	@Override
	protected void onResume() {
		super.onResume();

		MainApplication.onPreferenceChange();
		
		
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
		
		if (myLocation != null) {
			myLocation.disableMyLocation();
			myLocation = null;
		}
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
		Intent intent;
		// Handle item selection
		switch (item.getItemId()) {
			case R.id.menu_refresh:
				showMarkers();
				return true;
			case R.id.menu_settings:
				intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				return true;
			case R.id.show_days:
				intent = new Intent(this, DaysActivity.class);
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
	
	protected void showMarkers() {
		SQLiteOpenHelper dbHelper = new DatabaseHelper(this);
		SQLiteDatabase db = dbHelper.getReadableDatabase();
		// Clear layer/markers
		List<Overlay> myOverlays = myMapView.getOverlays();
		GregorianCalendar cal;
		cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
		
		// Get 10 newest locations, all from GPS, and with better than 40 meters accuracy
		Cursor c = db.query(
			"locations",
			null,
			"day=? and provider='gps' and accuracy < 40.00",
			new String[] {
				DateFormat.format("yyyyMMdd", cal).toString()
			},
			null,
			null,
			"milliseconds DESC",
			"1000"
		);
		int iMilliseconds = c.getColumnIndex("milliseconds");
		int iLongitude = c.getColumnIndex("longitude");
		int iLatitude = c.getColumnIndex("latitude");
		
		GeoPoint gpFirst = null;
		GeoPoint gpPrevious = null;
		
		/*
		myLocation = new MyLocationOverlay(this, myMapView);
		myLocation.enableMyLocation();
		*/

		MyOverlay.projection = myMapView.getProjection();
		myOverlays.clear();
		
		while (c.moveToNext()) {
			Double lo, la;
			la = new Double(c.getFloat(iLatitude) * 1E6);
			lo = new Double(c.getFloat(iLongitude) * 1E6);
			GeoPoint gp = new GeoPoint(
				la.intValue(),
				lo.intValue()
			);
			if (gpFirst == null) gpFirst = gp;
			Date d = new Date( c.getLong(iMilliseconds) );
			
			myOverlays.add( new MyOverlay(gpPrevious, gp) );
			gpPrevious = gp;
		}
		c.close();
		dbHelper.close();
		
		if (gpFirst != null) myMapView.getController().setCenter(gpFirst);
		/*
		// Redraw these GeoPoints on the map, with path lines, and pretty colors
		mapController.setZoom(21);
		*/
	}

}
