package com.szlosek.gpsing;

import java.lang.Math;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;


public class GPSingService extends Service implements SensorEventListener, LocationListener {
	// Notification-related
	protected int iNotificationId = 123;
	protected int iLocations = 0;
	protected int iSinceMotion = 0;
	private Notification mNotification;

	// Accelerometer-related
	private int iAccelReadings, iAccelSignificantReadings;
	private long iAccelTimestamp;
	private SensorManager mSensorManager;

	// GPS-related
	private static final int LOCATION_BUFFER = 5;
	private static final int BETWEEN_GPS = 30;
	private static final int TWO_MINUTES = 1000 * 60 * 2;
	private Location currentBestLocation;
	private long lGPSTimestamp;
	private LocationManager mLocationManager = null;
	private Location bestLocation = null;
	private PendingIntent pi;
	private LocationCircularBuffer locations;
	private boolean cellOnly = false;

	// Other
	private static volatile PowerManager.WakeLock wakeLock1 = null;
	private static volatile PowerManager.WakeLock wakeLock2 = null;
	private static volatile PowerManager.WakeLock wakeLock3 = null;

	// Messaging
	private final Messenger mServiceMessenger = new Messenger(new IncomingHandler());
	private Messenger mActivityMessenger = null;
	public static final int MSG_HELLO = 1;
	public static final int MSG_EXIT = 2;
	public static final int MSG_LOCATION = 3;

	
	// Called from GPSingReceiver ...
	// Acquires a WakeLock, polls Accelerometer, and then may poll GPS
	public static void requestLocation(Context ctxt, Intent i) {
		Log.d("GPSing","Alarmed");

		getLock(0, ctxt.getApplicationContext()).acquire();

		i.putExtra("com.szlosek.gpsing.IntentExtra", 0);
		i.setClass(ctxt, GPSingService.class); // Not certain I need this anymore
		ctxt.startService(i);
	}

	public static void timeoutGPS(Context ctxt, Intent i) {
		Log.d("GPSing", "Timed out");

		getLock(2, ctxt).acquire();
		i.setClass(ctxt, GPSingService.class); // Not certain I need this anymore
		ctxt.startService(i);
	}
	

	// Returns the requested WakeLock, creating it if necessary
	synchronized private static PowerManager.WakeLock getLock(int i, Context context) {
		PowerManager.WakeLock a;
		if (wakeLock1 == null) {
			PowerManager mgr = (PowerManager)context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
			wakeLock1 = mgr.newWakeLock(
				PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
				"lockAccelerometer"
			);
			wakeLock1.setReferenceCounted(true);
		}
		if (wakeLock2 == null) {
			PowerManager mgr = (PowerManager)context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
			wakeLock2 = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lockGPS");
			wakeLock2.setReferenceCounted(true);
		}
		if (wakeLock3 == null) {
			PowerManager mgr = (PowerManager)context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
			wakeLock3 = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lockGPSTimeout");
			wakeLock3.setReferenceCounted(true);
		}
		if (i == 0) {
			return wakeLock1;
		} else {
			if (i == 1) {
				return wakeLock2;
			} else {
				return wakeLock3;
			}
		}
	}
     
	// Update Notification
 	public synchronized void update(Context context, String type, int i) {
		int accuracy = 9999;
		if (this.currentBestLocation != null) {
			Float f = new Float(this.currentBestLocation.getAccuracy());
			accuracy = f.intValue();
		}
 		iLocations += i;
 		mNotification.setLatestEventInfo(
 			context,
 			"GPSing",
 			String.format("%s x%d to %d meters", type, iLocations, accuracy),
 			mNotification.contentIntent
 		);
		mNotification.when = System.currentTimeMillis();
 		NotificationManager nm = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
		nm.notify(123, mNotification);
 	}


	// ACCELEROMETER METHODS
	public void startAccelerometer() {
		iAccelReadings = 0;
		iAccelSignificantReadings = 0;
		iAccelTimestamp = System.currentTimeMillis();
		// should probably store handles to these earlier, when service is created
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		Sensor mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
	}

	public void stopAccelerometer() {
		mSensorManager.unregisterListener(this);
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		double accel, x, y, z, threshold;
		if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
			return;
		}

		iAccelReadings++;
		x = event.values[0];
		y = event.values[1];
		z = event.values[2];
		threshold = 0.6;
		
		accel = Math.abs(
			Math.sqrt(
				Math.pow(x,2)
				+
				Math.pow(y,2)
				+
				Math.pow(z,2)
			)
			-
			9.8
		);
		// Was 0.6. Lowered to 0.3 to account for smooth motion from Portland Streetcar
		if (accel > 0.3) {
			iAccelSignificantReadings++;
		}
		
		Log.d("GPSing", String.format("event: %f %f %f %f %f", x, y, z, accel, 0.600));
		
		// Get readings for 1 second
		// Maybe we should sample for longer given that I've lowered the threshold
		if ( (System.currentTimeMillis() - iAccelTimestamp) < 2000) return;
		
		stopAccelerometer();
					
		Log.d("GPSing", String.format("readings: %d significant: %d", iAccelReadings, iAccelSignificantReadings));

		// Appeared to be moving 30% of the time?
		// If the bar is this low, why not report motion at the first significant reading and be done with it?
		if (((1.0*iAccelSignificantReadings) / iAccelReadings) > 0.30) {
			iSinceMotion = 0;
			update(GPSingService.this, "Moving", 1);
			Log.d("GPSing", "Moving");
			
			// Get new lock for GPS so we can turn off screen
			getLock(1, GPSingService.this.getApplicationContext()).acquire();
			getLock(0, GPSingService.this.getApplicationContext()).release();
			
			// Start GPS
			startGPS();
			
		} else {
			iSinceMotion++;
			update(GPSingService.this, "Stationary", 0);
			Log.d("GPSing", "Stationary");
			sleep(0);
			getLock(0, GPSingService.this.getApplicationContext()).release();
 		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// can be safely ignored for this demo
	}
 		
  	

	// GPS METHODS
	public void startGPS() {
		// Set timeout for 30 seconds
		AlarmManager mgr = null;
		Intent i = null;;
		Calendar cal = null;
		int iProviders = 0;

		// Make sure at least one provider is available
		if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, this);
			iProviders++;
		}
		if (mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
			mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 500, 0, this);
			iProviders++;
			if (iProviders == 1) {
				cellOnly = true;
			}
		}

		if (iProviders == 0) {
			Log.d("GPSing", "No providers available");
			sleep(GPSingService.BETWEEN_GPS);
			getLock(1, GPSingService.this.getApplicationContext()).release();
			return;
		}

		lGPSTimestamp = System.currentTimeMillis();
		mgr = (AlarmManager)getSystemService(ALARM_SERVICE);
		cal = new GregorianCalendar();
		i = new Intent(this.getApplicationContext(), GPSTimeoutReceiver.class);
		this.pi = PendingIntent.getBroadcast(this.getApplicationContext(), 0, i, 0);
		cal.add(Calendar.SECOND, 30);
		mgr.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), this.pi);

		locations = new LocationCircularBuffer(GPSingService.LOCATION_BUFFER);
	}

	public void stopGPS() {
		lGPSTimestamp = 0;
		if (this.pi != null) {
			AlarmManager mgr = (AlarmManager)getSystemService(ALARM_SERVICE);
			mgr.cancel(this.pi);
			this.pi = null;
		}
		mLocationManager.removeUpdates(this);
	}
 		
	@Override
	public void onLocationChanged(Location location) {
		int a = 10;
		int i;

		Log.d("GPSing", String.format(
			"lat: %f lon: %f acc: %f provider: %s",
			location.getLatitude(),
			location.getLongitude(),
			location.getAccuracy(),
			location.getProvider()
		));

		// Determine whether to discard the location or not ...
		// hope this ends up being a quick calculation
		if (isBetterLocation(location, currentBestLocation)){
			currentBestLocation = location;
		}

		if (mActivityMessenger != null) {
			Message msg = Message.obtain();
			msg.what = GPSingService.MSG_LOCATION;
			if (currentBestLocation != null) {
				msg.obj = currentBestLocation;
				try {
					mActivityMessenger.send(msg);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
		}

		locations.insert( location );

		update(GPSingService.this, "Moving", 0);

		if (cellOnly == false && locations.size() < GPSingService.LOCATION_BUFFER) {
			return;
		}

		float minAccuracy, maxAccuracy;
		minAccuracy = 9999;
		maxAccuracy = 0;
		// for (i = 0; i < GPSingService.LOCATION_BUFFER; i++) {
		// size for network only case, if buffer isn't full
		for (i = 0; i < locations.size(); i++) {
			Location l = locations.get(i);
			minAccuracy = Math.min(minAccuracy, l.getAccuracy());
			maxAccuracy = Math.max(maxAccuracy, l.getAccuracy());
		}
		if (maxAccuracy - minAccuracy > 3) {
			return;
		}

	
		Log.d("GPSing", String.format("Not much change in last %d accuracies", GPSingService.LOCATION_BUFFER));
		stopGPS();
				
		// THINGS I'D LIKE TO LOG
		// Compared to current millis, how old is this location?
		// How long does it take to get location

		saveLocation();

		// Don't like this being hardcoded here ... need a better scheme for handling this
		// Would rather wait a minute between GPS attempts
		sleep(GPSingService.BETWEEN_GPS);
		getLock(1, GPSingService.this.getApplicationContext()).release();
	}

	protected void saveLocation() {
		ContentValues data;
		SQLiteOpenHelper dbHelper = new LocationsOpenHelper(GPSingService.this);
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		data = new ContentValues();
		data.put("milliseconds", currentBestLocation.getTime());
		data.put("longitude", currentBestLocation.getLongitude());
		data.put("latitude", currentBestLocation.getLatitude());
		data.put("altitude", currentBestLocation.getAltitude());
		data.put("gpsStart", lGPSTimestamp);
		data.put("accuracy", currentBestLocation.getAccuracy());
		data.put("bearing", currentBestLocation.getBearing());
		data.put("speed", currentBestLocation.getSpeed());
		data.put("provider", currentBestLocation.getProvider());
		data.put("best", 0);
		db.insert("locations", null, data);
		db.close();

		//MainActivity.this.updateLocation( this.currentBestLocation );
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		Log.d("GPSing", String.format("onStatusChanged: %s status: %d", provider, status));
	}

	@Override
	public void onProviderEnabled(String provider) {
		Log.d("GPSing", String.format("onProviderEnabled: %s", provider));
		if (lGPSTimestamp == 0) {
			// Not currently interested
			return;
		}
		// If it's a provider we care about, and we're listening, listen!
		if (provider == LocationManager.GPS_PROVIDER || provider == LocationManager.NETWORK_PROVIDER) {
			mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, this);
		}
	}

	@Override
	public void onProviderDisabled(String provider) {
		Log.d("GPSing", String.format("onProviderDisabled: %s", provider));

		if (lGPSTimestamp == 0) {
			// Not currently interested
			return;
		}
		// If it's a provider we care about, and we're listening, listen!
		if (provider == LocationManager.GPS_PROVIDER && mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
			mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 500, 0, this);
		} else if (provider == LocationManager.NETWORK_PROVIDER && mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, this);
		}
	}
	
	/** Determines whether one Location reading is better than the current Location fix
	  * @param location  The new Location that you want to evaluate
	  * @param currentBestLocation  The current Location fix, to which you want to compare the new one
	  */
	protected boolean isBetterLocation(Location location, Location currentBestLocation) {

		if (currentBestLocation == null) {
			// A new location is always better than no location
			return true;
		}

		// Check whether the new location fix is newer or older
		long timeDelta = location.getTime() - currentBestLocation.getTime();
		boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
		boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
		boolean isNewer = timeDelta > 0;

		// If it's been more than two minutes since the current location, use the new location
		// because the user has likely moved
		if (isSignificantlyNewer) {
			return true;
		// If the new location is more than two minutes older, it must be worse
		} else if (isSignificantlyOlder) {
			return false;
		}

		// Check whether the new location fix is more or less accurate
		int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
		boolean isLessAccurate = accuracyDelta > 0;
		boolean isMoreAccurate = accuracyDelta < 0;
		boolean isSignificantlyLessAccurate = accuracyDelta > 200;

		// Check if the old and new location are from the same provider
		boolean isFromSameProvider = isSameProvider(location.getProvider(), currentBestLocation.getProvider());

		// Determine location quality using a combination of timeliness and accuracy
		if (isMoreAccurate) {
			return true;
		} else if (isNewer && !isLessAccurate) {
			return true;
		} else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
			return true;
		}
		return false;
	}

	/** Checks whether two providers are the same */
	private boolean isSameProvider(String provider1, String provider2) {
		if (provider1 == null) {
			return provider2 == null;
		}
		return provider1.equals(provider2);
	}


	// OTHER
 	
 	public class LocationsOpenHelper extends SQLiteOpenHelper {
		public LocationsOpenHelper(Context context) {
			super(context, "locations.db", null, 1);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("create table locations (milliseconds integer, latitude real, longitude real, altitude real, gpsStart integer, accuracy real, bearing real, speed real, provider text, best integer);");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			// TODO Auto-generated method stub
		}
 	}
 	
 	
 	public void sleep(int w) {
		AlarmManager mgr = (AlarmManager)getSystemService(ALARM_SERVICE);
		Intent i = new Intent(this, GPSingReceiver.class);
		Calendar cal = new GregorianCalendar();

		if (w == 0) {
			if (iSinceMotion < 3) {
				Log.d("GPSing", "Waiting 30 seconds");
				cal.add(Calendar.SECOND, 30);
			} else {

				Log.d("GPSing", "Waiting 60 seconds");
				cal.add(Calendar.SECOND, 60);
			}
		} else {
			cal.add(Calendar.SECOND, w);
		}

		this.pi = PendingIntent.getBroadcast(this, 0, i, 0);
		mgr.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), this.pi);
	}

	private void handleIntent(Intent intent) {
		Bundle b = intent.getExtras();

		int a = b.getInt("com.szlosek.gpsing.IntentExtra");

		if (a == 0) { // Start GPSing
			// Which type of intent have we received?
			Log.d("GPSing", "Service.onStartCommand=StartGPS");
			// Will this be destroyed?
			startAccelerometer();

		} else { // GPS timeout, so stop
			Log.d("GPSing", "Service.onStartCommand=StopGPS");
			stopGPS();
			saveLocation();
			sleep(30);
			if (getLock(1, GPSingService.this.getApplicationContext()).isHeld()) {
				getLock(1, GPSingService.this.getApplicationContext()).release();
			}
			getLock(2, GPSingService.this.getApplicationContext()).release();
		}
		
		
	}




	// Message Handler
	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				// Client is sending us it's message handler
				case GPSingService.MSG_HELLO:
					mActivityMessenger = msg.replyTo;
					break;
				case GPSingService.MSG_EXIT:
					Toast.makeText(getApplicationContext(), "exit", Toast.LENGTH_SHORT).show();
					// Cancel all alarms
					if (GPSingService.this.pi != null) {
						AlarmManager mgr = (AlarmManager)getSystemService(ALARM_SERVICE);
						mgr.cancel(GPSingService.this.pi);
						GPSingService.this.pi = null;
					}
					// Might be nice to have a state machine state variable so it's easier to know how to transition out of the current state
					stopSelf();
					break;
				default:
					super.handleMessage(msg);
			}
		}
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		Log.d("GPSing", "Service.onCreate");

		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		
		// Set the icon, scrolling text and timestamp
		mNotification = new Notification(R.drawable.stationary, "Stationary", System.currentTimeMillis());
		// The PendingIntent to launch our activity if the user selects this notification
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, MainActivity.class), 0);

		// Set the info for the views that show in the notification panel.
		mNotification.setLatestEventInfo(this, "GPSing", "Stationary", contentIntent);
		startForeground(iNotificationId, mNotification);
		
		/*


		//HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
		HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
		thread.setDaemon(true);
		thread.start();

		// Get the HandlerThread's Looper and use it for our Handler 
		mServiceLooper = thread.getLooper();
		mServiceHandler = new ServiceHandler(mServiceLooper);
		*/
	}

	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d("GPSing", "Service.onStartCommand");
		handleIntent(intent);
		return START_REDELIVER_INTENT;
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.d("GPSing", "Service.onBind");
		getLock(0, getApplicationContext()).acquire();
		handleIntent(intent);
		return mServiceMessenger.getBinder();
	}

	@Override
	public boolean onUnbind(Intent intent) {
		mActivityMessenger = null;
		return true;
	}

	@Override
	public void onDestroy() {
		Log.d("GPSing", "Service.onDestroy");
	}
}
