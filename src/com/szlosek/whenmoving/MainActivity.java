package com.szlosek.whenmoving;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;

import java.lang.Thread;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends MapActivity {
	SharedPreferences sharedPreferences;
	MapView mapView = null;
	MapController mapController = null;
	List<Overlay> mapOverlays = null;
	Drawable drawable = null;
	MainOverlay gpsingOverlay = null;
	boolean paused = false;
	
	private CircularBuffer mRecentLocations;
	
	public static boolean currentState = false; // not running
	
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
					// Don't update when paused
					if (paused == true) {
						return;
					}
					Location l = (Location) msg.obj;
					Double lo, la;
					la = new Double(l.getLatitude() * 1E6);
					lo = new Double(l.getLongitude() * 1E6);
					GeoPoint gp = new GeoPoint(
						la.intValue(),
						lo.intValue()
					);
					
					//mRecentLocations.insert(gp);
					
					// Redraw these GeoPoints on the map, with path lines, and pretty colors
					
					/*
					mapController.setZoom(21);
					mapController.setCenter(gp);
					*/

					OverlayItem overlayItem = new OverlayItem(gp, "Hola, Mundo!", "I'm in Mexico City!");
					gpsingOverlay.clear();
					gpsingOverlay.addOverlay(overlayItem);
					mapOverlays.clear();
					mapOverlays.add(gpsingOverlay);
					//mapView.postInvalidate();

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
		TextView tv;
		Debug(String.format("New state: %s", (newState == true ? "on" : "off")));
		if (currentState == true) {
			if (newState == false) {
				// Alarms are active. Service may be running right now
				// if so, let it finish, then simply skip the call to re-schedule
				tv = (TextView) findViewById(R.id.statusState);
				tv.setText("Not tracking");
				stopGPSing();
			}
		} else {
			if (newState == true) {
				tv = (TextView) findViewById(R.id.statusState);
				tv.setText("Tracking");
				
				// Schedule an alarm
				startGPSing(); // will replace this later
			}
		}
	}

	// Activity 
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_main);
		
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		/*
		CheckBox cb = (CheckBox) findViewById(R.id.checkBox1);
		cb.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (((CheckBox) v).isChecked()) {
					startGPSing();

				} else {
					stopGPSing();
				}
			}
		});
		*/
		
		ImageButton buttonSettings = (ImageButton) findViewById(R.id.settings_button);
		buttonSettings.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// start activity
				Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
				startActivity(intent);
			}
		});

		mapView = (MapView) findViewById(R.id.mapview);
		mapView.setBuiltInZoomControls(true);
		mapController = mapView.getController();

		mapOverlays = mapView.getOverlays();
		gpsingOverlay = new MainOverlay(
			this.getResources().getDrawable(R.drawable.marker),
			getApplicationContext()
		);
		//mapOverlays.add(gpsingOverlay);
		
		mRecentLocations = new CircularBuffer(10);
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		
		Debug("Activity started");
		//again();
	}

	@Override
	protected void onResume() {
		super.onResume();
		paused = false;

		/*
		CheckBox cb = (CheckBox) findViewById(R.id.checkBox1);
		cb.setChecked( MainActivity.serviceRunning );
		*/
		
		//  Get latest settings, and update accordingly
		boolean newState = sharedPreferences.getBoolean("pref_onoff", false); // false is off/not-running
		prefInterval = Integer.parseInt( sharedPreferences.getString("pref_interval", "60") );
		prefTimeout = Integer.parseInt( sharedPreferences.getString("pref_timeout", "30") );
		
		// If we turned off the service, handle that change
		toggleState( newState );
	}

	@Override
	protected void onPause() {
		super.onPause();
		paused = true;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		stopGPSing();
	}

	// Maps Methods
	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}


	private void startGPSing() {
		currentState = true;
		Intent i = new Intent(getApplicationContext(), MainService.class);
		i.putExtra("com.szlosek.whenmoving.IntentExtra", 0);
		//MainService.requestLocation(this, i);
		bindService(i, MainActivity.this.mConnection, Context.BIND_AUTO_CREATE);
	}

	private void stopGPSing() {
		currentState = false;
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

}
