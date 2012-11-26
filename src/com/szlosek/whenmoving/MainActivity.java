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
	public static Context myContext;
	SharedPreferences sharedPreferences;
	MapView myMapView = null;
	MainItemizedOverlay myItemizedOverlays = null;
	List<Overlay> myOverlays = null;
	protected Drawable myDrawable = null;
	
	//public static boolean currentState = false; // not running
	
	public static int prefInterval = 0;
	public static int prefTimeout = 0;


	// Messaging
	Messenger mServiceMessenger = null;
	boolean mBound = false;
	private final Messenger mActivityMessenger = new Messenger(new IncomingHandler());

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the object we can use to
			// interact with the service.  We are communicating with the
			// service using a Messenger, so here we get a client-side
			// representation of that from the raw IBinder object.
			mServiceMessenger = new Messenger(service);
			mBound = true;

			Message msg = Message.obtain();
			msg.what = MainService.MSG_HELLO;
			msg.replyTo = mActivityMessenger;
			try {
				mServiceMessenger.send(msg);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			mServiceMessenger = null;
			mBound = false;
		}
	};

	// Message Handler
	class IncomingHandler extends Handler {
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case MainService.MSG_LOCATION:
					break;
				default:
					super.handleMessage(msg);
			}
		}
	}

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
		if (MainApplication.currentState == true) {
			if (newState == false) {
				// Alarms are active. Service may be running right now
				// if so, let it finish, then simply skip the call to re-schedule
				stopGPSing();
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
		myContext = this;
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
		//stopGPSing();
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
				Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
				startActivity(intent);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}


	private void startGPSing() {
		MainApplication.currentState = true;
		Intent i = new Intent(getApplicationContext(), MainService.class);
		i.putExtra("com.szlosek.whenmoving.IntentExtra", 0);
		bindService(i, MainActivity.this.mConnection, Context.BIND_AUTO_CREATE);
	}

	private void stopGPSing() {
		MainApplication.currentState = false;
		if (mServiceMessenger != null) {
			Message msg = Message.obtain();
			msg.what = MainService.MSG_EXIT;
			try {
				mServiceMessenger.send(msg);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		unbindService(mConnection);
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
