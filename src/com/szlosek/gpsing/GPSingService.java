package com.szlosek.gpsing;

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
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;


public class GPSingService extends Service {
	protected int iNotificationId = 123;
	protected int iLocations = 0;
	protected int iSinceMotion = 0;
	private Notification mNotification;

	private static volatile PowerManager.WakeLock wakeLock1 = null;
	private static volatile PowerManager.WakeLock wakeLock2 = null;
	
	// Called from GPSingReceiver ...
	// Acquires a WakeLock, polls Accelerometer, and then may poll GPS
	public static void requestLocation(Context ctxt, Intent i) {
		Log.d("GPSing","Alarmed");

		getLock(0, ctxt.getApplicationContext()).acquire();
		i.setClass(ctxt, GPSingService.class); // Not certain I need this anymore
		ctxt.startService(i);
	}
	

	// Returns the requested WakeLock, creating it if necessary
	synchronized private static PowerManager.WakeLock getLock(int i, Context context) {
		PowerManager.WakeLock a;
		if (wakeLock1 == null) {
			PowerManager mgr = 
					(PowerManager)context.getApplicationContext()
					.getSystemService(Context.POWER_SERVICE);

			wakeLock1 = mgr.newWakeLock(
				PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
				"lockAccelerometer"
			);
			wakeLock1.setReferenceCounted(true);
		}
		if (wakeLock2 == null) {
			PowerManager mgr = 
					(PowerManager)context.getApplicationContext()
					.getSystemService(Context.POWER_SERVICE);

			wakeLock2 = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lockGPS");
			wakeLock2.setReferenceCounted(true);
		}
		if (i == 0) return wakeLock1;
		return wakeLock2;
	}
     
	// Update Notification
 	public synchronized void update(Context context, String type, int i) {
 		iLocations += i;
 		mNotification.setLatestEventInfo(
 			context,
 			"GPSing",
 			String.format("%s x%d", type, iLocations),
 			mNotification.contentIntent
 		);
 		NotificationManager nm = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
 			nm.notify(123, mNotification);
 	}

     
  	private class FunTimes2 implements SensorEventListener {
 		private int iReadings, iSignificant;
 		private long iTimestamp;
 		SensorManager mSensorManager = null;

 		
 		@Override
 		public void onSensorChanged(SensorEvent event) {
 			if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
 				readAccelerometer(event);
 			}
 		}
 		@Override
 		public void onAccuracyChanged(Sensor sensor, int accuracy) {
 			// can be safely ignored for this demo
 		}
 		
 		public void start() {
 			iReadings = 0;
 			iSignificant = 0;
 			iTimestamp = System.currentTimeMillis();
 			// Not sure which one to use here
 			mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
 			Sensor mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
 			mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
 		}
 		public void stop() {
 			mSensorManager.unregisterListener(this);
 		}
 		
 		
 		/*
 		 * Thinking I don't want delta, but absolutes
 		 */
 		// Think i need to keep timestamps, so I can throw out very recent readings
 		protected void readAccelerometer(SensorEvent event) {
 			double accel, x, y, z, threshold;
 			iReadings++;
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
 			if (accel > 0.6) {
 				Log.d("GPSing", "sig");
 				iSignificant++;
 			}
 			
 			Log.d("GPSing", String.format("event: %f %f %f %f %f",
 					x, y, z, accel, 0.600));
 			
 			// Get readings for 1 second
 			if ( (System.currentTimeMillis() - iTimestamp) < 1000) return;
 			
 			this.stop();
 			 			
 			Log.d("GPSing", String.format("readings: %d significant: %d",
 					iReadings, iSignificant));

 			if (((1.0*iSignificant) / iReadings) > 0.30) {
 			
 			// Appeared to be moving 50% of the time?
 				iSinceMotion = 0;
 				update(GPSingService.this, "Moving", 1);
 				Log.d("GPSing", "Moving");
 				
 				// Get new lock for GPS so we can turn off screen
 				getLock(1, GPSingService.this.getApplicationContext()).acquire();
 				getLock(0, GPSingService.this.getApplicationContext()).release();
 				
 				// Start GPS
 				FunTimes3 rFunTimes3 = new FunTimes3();
 				rFunTimes3.start();
 				
 			} else {
 				iSinceMotion++;
 				update(GPSingService.this, "Stationary", 0);
 				Log.d("GPSing", "Stationary");
 				sleep(0);
 				getLock(0, GPSingService.this.getApplicationContext()).release();
 			}
 		}
 	}

  	
  	private class FunTimes3 implements LocationListener {
 		private static final int TWO_MINUTES = 1000 * 60 * 2;
 		//private Location currentBestLocation;
 		private ArrayList<Location> locations;
 		private long gpsStart;
 		
 		private LocationManager mLocationManager = null;
 		
 		public void start() {
 			locations = new ArrayList<Location>();
 			gpsStart = System.currentTimeMillis();
 			mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
 			// Will hopefully report location updates every 400 milliseconds
 			mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 400, 0, this);
 		}
 		public void stop() {
 			mLocationManager.removeUpdates(this);
 		}
 		
 		public void onLocationChanged(Location location) {
 			ContentValues data;
 			Location l, currentBestLocation;

 			//if (location.getAccuracy() < 100) {
 				locations.add(location);
 			//}
 			// Only process when we've got 3 locations
 			if (locations.size() < 6) return;
 			if (locations.size() > 6) return;
 			
 			this.stop();
 					
 			// THINGS I'D LIKE TO LOG
 			// Compared to current millis, how old is this location?
 			// How long does it take to get location
 		
 			// Choose best from current list
 				
 			//}
 			currentBestLocation = locations.get(0);
 			for (int i = 1; i < locations.size(); i++) {
 				l = locations.get(i);
 				if (isBetterLocation(l, currentBestLocation)){
 					currentBestLocation = l;
 				}
 			}
 			
 			// How do we know if GPS fails altogether?
 			SQLiteOpenHelper dbHelper = new LocationsOpenHelper(GPSingService.this);
 			SQLiteDatabase db = dbHelper.getWritableDatabase();
 			for (int i = 0; i < locations.size(); i++) {
 				l = locations.get(i);
 				data = new ContentValues();
 				data.put("milliseconds", l.getTime());
 				data.put("longitude", l.getLongitude());
 				data.put("latitude", l.getLatitude());
 				data.put("altitude", l.getAltitude());
 				data.put("gpsStart", gpsStart);
 				data.put("accuracy", l.getAccuracy());
 				data.put("best", (l == currentBestLocation ? 1: 0));
 				db.insert("locations", null, data);
 			}
 			db.close();
 			sleep(30);
 			getLock(1, GPSingService.this.getApplicationContext()).release();
 		}

 		public void onStatusChanged(String provider, int status, Bundle extras) {}

 		public void onProviderEnabled(String provider) {}

 		public void onProviderDisabled(String provider) {}
 		
 		
 		

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
 		    boolean isFromSameProvider = isSameProvider(location.getProvider(),
 		            currentBestLocation.getProvider());

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
 	}
 	
 	public class LocationsOpenHelper extends SQLiteOpenHelper {
 	    public LocationsOpenHelper(Context context) {
 	        super(context, "locations.db", null, 1);
 	    }

 	    @Override
 	    public void onCreate(SQLiteDatabase db) {
 	        db.execSQL("create table locations (milliseconds integer, latitude real, longitude real, altitude real, gpsStart integer, accuracy real, best integer);");
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

        PendingIntent pi = PendingIntent.getBroadcast(this, 0, i, 0);
        mgr.set(
        		AlarmManager.RTC_WAKEUP,
        		cal.getTimeInMillis(),
        		pi);
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		Log.d("Service","created");
		
		// Set the icon, scrolling text and timestamp
		mNotification = new Notification(R.drawable.stationary, "Stationary", System.currentTimeMillis());
		// The PendingIntent to launch our activity if the user selects this notification
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, GPSingService.class), 0);

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
		Log.d("GPSing","Service.StartCommand");
		// Will this be destroyed?
		FunTimes2 ft = new FunTimes2();
		ft.start();
		
		
		return START_REDELIVER_INTENT;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		Toast.makeText(getApplicationContext(), "binding", Toast.LENGTH_SHORT).show();
        //return mMessenger.getBinder();
		return null;
	}

	@Override
	public void onDestroy() {
		Log.d("Service", "Destroyed");
	}
}
