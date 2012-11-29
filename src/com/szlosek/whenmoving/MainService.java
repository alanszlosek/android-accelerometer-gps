/*
Think I'm going to do away with this as a service. If I want the UI to be more responsive while polling, this should run in another thread. Easiest way to do that is to stop using it as a service. Instead, it'll be instantiated when the broadcast receiver gets triggered, and stored in the MainActivity.

I'm not interested in having a long-running notification, though I may create one whenever the state is RUNNING. If the app gets killed, the alarm may persist and start things up again. Not certain.
*/

package com.szlosek.whenmoving;

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
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;


public class MainService extends Service implements SensorEventListener, LocationListener {
	// NOTIFICATION RELATED
	// The PendingIntent to launch our activity if the user selects this notification
	protected PendingIntent mPendingIntent;
	protected int iNotificationId = 123;
	protected int iLocations = 0;
	protected float fSince = 0;
	protected int iIntervals = 0;
	protected Notification mNotification;

	// INTERNAL STATE
	private boolean moving = false;

	// Accelerometer-related
	private int iAccelReadings, iAccelSignificantReadings;
	private long iAccelTimestamp;
	private SensorManager mSensorManager;

	// GPS-related
	private static final int LOCATION_BUFFER = 5;
	//private static final int BETWEEN_GPS = 30;
	private static final int TWO_MINUTES = 1000 * 60 * 2;
	public static Location currentBestLocation;
	private long lGPSTimestamp;
	private LocationManager mLocationManager = null;
	private Location bestLocation = null;
	private PendingIntent pi;
	private CircularBuffer locations;
	private boolean cellOnly = false;
	private float lowestAccuracy;

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
		double accel, x, y, z;
		if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
			return;
		}

		iAccelReadings++;
		x = event.values[0];
		y = event.values[1];
		z = event.values[2];
		
		accel = Math.abs(
			Math.sqrt(
				Math.pow(x,2)
				+
				Math.pow(y,2)
				+
				Math.pow(z,2)
			)
		);
		// Was 0.6. Lowered to 0.3 (plus gravity) to account for smooth motion from Portland Streetcar
		if (accel > 10.1) {
			iAccelSignificantReadings++;
		}
		
		//Debug(String.format("event: %f %f %f %f %f", x, y, z, accel, 0.600));
		
		// Get readings for 1 second
		// Maybe we should sample for longer given that I've lowered the threshold
		if ( (System.currentTimeMillis() - iAccelTimestamp) < 2000) return;
		
		stopAccelerometer();

		Debug(String.format("Accelerometer readings: %d Significant: %d", iAccelReadings, iAccelSignificantReadings));

		// Appeared to be moving 30% of the time?
		// If the bar is this low, why not report motion at the first significant reading and be done with it?
		if (((1.0*iAccelSignificantReadings) / iAccelReadings) > 0.30) {
			setMoving(true);
			Debug("Moving");
			
			// Get new lock for GPS so we can turn off screen
			MainApplication.wakeLock2(true);
			MainApplication.wakeLock1(false);
			
			// Start GPS
			startGPS();
			
		} else {
			setMoving(false);
			Debug("Stationary");
			sleep();
			MainApplication.wakeLock1(false);
 		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// can be safely ignored
	}


	// GPS METHODS
	public void startGPS() {
		// Set timeout for 30 seconds
		AlarmManager mgr = null;
		Intent i = null;
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
			Debug("No providers available");
			sleep();
			MainApplication.wakeLock2(false);
			return;
		}

		lGPSTimestamp = System.currentTimeMillis();
		lowestAccuracy = 9999;
		mgr = (AlarmManager)getSystemService(ALARM_SERVICE);
		cal = new GregorianCalendar();
		i = new Intent(this, TimeoutReceiver.class);
		this.pi = PendingIntent.getBroadcast(this, 0, i, 0);
		cal.add(Calendar.SECOND, MainActivity.prefTimeout);
		mgr.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), this.pi);

		locations = new CircularBuffer(MainService.LOCATION_BUFFER);
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
		float accuracyDiff = 0;

		Debug(String.format(
			"lat: %f lon: %f acc: %f provider: %s",
			location.getLatitude(),
			location.getLongitude(),
			location.getAccuracy(),
			location.getProvider()
		));

		// Determine whether to discard the location or not ...
		// hope this ends up being a quick calculation
		if (isBetterLocation(location, MainService.currentBestLocation)){
			MainService.currentBestLocation = location;
		}

		locations.insert( location );


		// Only care about circular buffer max/min compare if using gps
		// or maybe only if we have 5
		if (cellOnly == false) {

			// Can't do much without significant measurement
			if (locations.size() < MainService.LOCATION_BUFFER) {
				return;
			}

			/*
			lowestAccuracy = Math.min(lowestAccuracy, location.getAccuracy() );
			accuracyDiff = Math.abs( lowestAccuracy - location.getAccuracy() );
			// Getting worse?
			accuracyDiff < 5
			*/

			float minAccuracy, maxAccuracy;
			minAccuracy = 9999;
			maxAccuracy = 0;
			for (i = 0; i < locations.size(); i++) {
				Location l = (Location) locations.get(i);
				if (l == null) continue;
				minAccuracy = Math.min(minAccuracy, l.getAccuracy());
				maxAccuracy = Math.max(maxAccuracy, l.getAccuracy());
			}
			if (maxAccuracy - minAccuracy > 3) {
				return;
			}

		
			Debug(String.format("Not much change in last %d accuracies", MainService.LOCATION_BUFFER));
		}
		stopGPS();

		// THINGS I'D LIKE TO LOG
		// Compared to current millis, how old is this location?
		// How long does it take to get location

		saveLocation();

		// Don't like this being hardcoded here ... need a better scheme for handling this
		// Would rather wait a minute between GPS attempts
		sleep();
		MainApplication.wakeLock2(false);
	}

	protected void saveLocation() {
		ContentValues data;
		Location l = MainService.currentBestLocation;
		if (l == null) {
			return;
		}
		SQLiteOpenHelper dbHelper = new DatabaseHelper(MainService.this);
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		data = new ContentValues();
		data.put("milliseconds", l.getTime());
		data.put("longitude", l.getLongitude());
		data.put("latitude", l.getLatitude());
		data.put("altitude", l.getAltitude());
		data.put("gpsStart", lGPSTimestamp);
		data.put("accuracy", l.getAccuracy());
		data.put("bearing", l.getBearing());
		data.put("speed", l.getSpeed());
		data.put("provider", l.getProvider());
		db.insert("locations", null, data);
		db.close();
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		Debug(String.format("onStatusChanged: %s status: %d", provider, status));
	}

	@Override
	public void onProviderEnabled(String provider) {
		Debug(String.format("onProviderEnabled: %s", provider));
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
		Debug(String.format("onProviderDisabled: %s", provider));

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
	public void sleep() {
		// Check desired state
		if (MainApplication.trackingOn == false) {
			Debug("Tracking has been toggled off. Not scheduling any more wakeup alarms");
			stopSelf();
			// Tracking has been turned off, don't schedule any new alarms
			return;
		}
		AlarmManager mgr = (AlarmManager)getSystemService(ALARM_SERVICE);
		Intent i = new Intent(this, MainReceiver.class);
		Calendar cal = new GregorianCalendar();

		Debug(String.format("Waiting %d seconds", MainActivity.prefInterval));
		cal.add(Calendar.SECOND, MainActivity.prefInterval);

		this.pi = PendingIntent.getBroadcast(this, 0, i, 0);
		mgr.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), this.pi);
	}

	public void gpsTimeout() {
		Debug("GPS timeout");
		stopGPS();
		saveLocation();
		sleep();
		MainApplication.wakeLock2(false);
	}
	
	
	@Override
	public void onCreate() {
		super.onCreate();
		MainApplication.mServiceInstance = this;
		Debug("Service.onCreate");
		
		mPendingIntent = PendingIntent.getActivity(
			this,
			0,
			new Intent(this, MainActivity.class),
			0
		);

		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		
		setMoving(false);
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
		Debug("Service.onStartCommand");
		startAccelerometer();
		return START_REDELIVER_INTENT;
		//return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		/*
		Debug("Service.onBind");
		getLock(0, getApplicationContext()).acquire();
		handleIntent(intent);
		return mServiceMessenger.getBinder();
		*/
		return null;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		//mActivityMessenger = null;
		return true;
	}

	@Override
	public void onDestroy() {
		Debug("Service.onDestroy");
		MainApplication.mServiceInstance = null;
	}


	public static void Debug(String message) {
		Log.d("WhenMoving.Service", message);
	}
	
	protected void setMoving(boolean newState) {
		Context mContext;
		String s, fuzzy;
		NotificationManager nm;
		
		// Reset counts
		if (moving == newState) {
			iIntervals++;
		} else {
			// New state. Reset intervals
			iIntervals = 0;
			fSince = System.currentTimeMillis();
		}
		
		// Prepare new Notification string
		/*
		if (newState == true) {
			s = String.format("On the move");
		} else {
			s = String.format("Sitting still");
		}
		*/
		
		// Could detect long periods of motion here and give a badge or say something funny
		
		// UPDATE NOTIFICATION
		if (mNotification != null && moving == newState) { // Update existing notification
			// No update
			// mNotification.setLatestEventInfo(this, "WhenMoving.Service", s, mPendingIntent);
		
		} else { // New notification
			/*
			Notification mNotification = new NotificationCompat.Builder(mContext)
				.setContentTitle( (moving ? "Moving" : "Stationary") )
				.setContentText(t)
				.setSmallIcon( (moving ? R.drawable.moving : R.drawable.status) )
				.setContentIntent(contentIntent)
				.build();
			*/
			if (newState == true) {
				s = String.format("You're on the move");
			} else {
				s = String.format("You're a sitting duck");
			}
		
			// don't keep creating this if state hasn't transitioned
			mNotification = new Notification(
				(newState ? R.drawable.moving : R.drawable.stationary),
				s,
				System.currentTimeMillis()
			);
			mNotification.setLatestEventInfo(this, "When Moving", s, mPendingIntent);
		}
		
		nm = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);
		nm.notify(123, mNotification);
		
		
		// TELL UI TO UPDATE
		
		moving = newState;
		
		/*
		int accuracy = 9999;
		if (this.currentBestLocation != null) {
			Float f = new Float(this.currentBestLocation.getAccuracy());
			accuracy = f.intValue();
		}
		*/
		
	}
	
}
