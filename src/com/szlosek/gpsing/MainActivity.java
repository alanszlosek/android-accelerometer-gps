
package com.szlosek.gpsing;
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
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.Toast;

public class MainActivity extends MapActivity {
	MapView mapView = null;
	MapController mapController = null;
	List<Overlay> mapOverlays = null;
	Drawable drawable = null;
	GPSingOverlay gpsingOverlay = null;
	boolean paused = false;

	private CheckBox cb = null;


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
			msg.what = GPSingService.MSG_HELLO;
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
				case GPSingService.MSG_LOCATION:
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


	// Called by service when it's up, so this activity can bind to it
	public static void ready() {
		// This needs to wait for the service to come online
		//bindService(new Intent(MainActivity.this, GPSingService.class), MainActivity.this.mConnection, Context.BIND_AUTO_CREATE);
	}

	// Activity 
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		cb = (CheckBox) findViewById(R.id.checkBox1);
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

		mapView = (MapView) findViewById(R.id.mapview);
		mapView.setBuiltInZoomControls(true);
		mapController = mapView.getController();

		mapOverlays = mapView.getOverlays();
		gpsingOverlay = new GPSingOverlay(
			this.getResources().getDrawable(R.drawable.green_dot_12x20),
			getApplicationContext()
		);
		//mapOverlays.add(gpsingOverlay);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
			case R.id.menu_exit:
				stopGPSing();
				finish();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		
		Log.d("GPSing","Activity started");
		//again();
	}

	@Override
	protected void onResume() {
		super.onResume();
		paused = false;
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

	// no longer used ...
	void again() {

		AlarmManager mgr = (AlarmManager)getSystemService(ALARM_SERVICE);

		Intent i = new Intent(this, GPSingReceiver.class);

		Calendar cal = new GregorianCalendar();
		cal.add(Calendar.SECOND, 1);

		PendingIntent pi = PendingIntent.getBroadcast(this, 0, i, 0);
		mgr.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
	}


	private void startGPSing() {
		Intent i = new Intent(getApplicationContext(), GPSingService.class);
		i.putExtra("com.szlosek.gpsing.IntentExtra", 0);
		//GPSingService.requestLocation(this, i);
		bindService(i, MainActivity.this.mConnection, Context.BIND_AUTO_CREATE);
	}

	private void stopGPSing() {
		if (mServiceMessenger != null) {
			Message msg = Message.obtain();
			msg.what = GPSingService.MSG_EXIT;
			try {
				mServiceMessenger.send(msg);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		unbindService(mConnection);
	}

}
