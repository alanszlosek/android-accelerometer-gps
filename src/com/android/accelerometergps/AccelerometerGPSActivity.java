package com.android.accelerometergps;

import java.util.ArrayList;

import android.app.Activity;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.TextView;

public class AccelerometerGPSActivity extends Activity {
	private float fMinimum = (float) 0.6;
	
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	private LocationManager mLocationManager;
	
	private Handler hMain = new Handler();
	private Accel selAccel;
	private FunTimes rFunTimes;
	private myLocationListener llGPS;
	
	private TextView textViewStatus, textViewMoving;
	private SQLiteOpenHelper dbHelper;
	private SQLiteDatabase db;
	
	
	private class Accel implements SensorEventListener {
		private int iCount;
		private float x, y, z;
		
		public void onSensorChanged(SensorEvent event) {
			if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
				readAccelerometer(event);
			}
		}
		
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			// can be safely ignored for this demo
		}
		
		public void start() {
			iCount = 0;
			textViewStatus.setText("GetAccel");
			mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		}
		public void stop() {
			textViewStatus.setText("NoAccel");
			mSensorManager.unregisterListener(this);
		}
		
		
		/*
		 * Thinking I don't want delta, but absolutes
		 */
		// Think i need to keep timestamps, so I can throw out very recent readings
		protected void readAccelerometer(SensorEvent event) {
			TextView tv;
			double total;
			
			tv = (TextView)findViewById(R.id.longitude);
			tv.setText( String.format("%d", iCount) );
			
			if (iCount == 0) {
				x = event.values[0];
				y = event.values[1];
				z = event.values[2];
			}
			if (iCount == 3) {
				this.stop();
				
				total = Math.abs(
					(Math.abs(x) + Math.abs(y) + Math.abs(z))
					-
					(Math.abs(event.values[0]) + Math.abs(event.values[1]) + Math.abs(event.values[2]))
				);
				
				tv = (TextView)findViewById(R.id.latitude);
				tv.setText( String.format("%f", total) );
				
				if (total > 0.6) {
					textViewMoving.setText("Moving");
					llGPS.start();
					
				} else {
					textViewMoving.setText("Stationary");
					// Not moving, wait another 5 seconds
					rFunTimes.start();
				}
				// Stop
			}
			iCount++;
		}
	}
	
	// Every 5 seconds, get accelerometer reading
	private class FunTimes implements Runnable {
		public void run() {
			/*
			long millis = System.currentTimeMillis();
			int seconds = (int) (millis / 1000);
			*/		
			selAccel.start();
		}
		
		public void start() {
			textViewStatus.setText("5sec");
			hMain.postDelayed(rFunTimes, 5000);			
		}
		public void stop() {
			textViewStatus.setText("");
			hMain.removeCallbacks(rFunTimes);			
		}
	}
	
	private class myLocationListener implements LocationListener {
		private int iCount;
		private static final int TWO_MINUTES = 1000 * 60 * 2;
		private Location currentBestLocation;
		private ArrayList<Location> locations;
		
		
		public void start() {
			iCount = 0;
			locations = new ArrayList<Location>();
			mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 400, 0, this);
		}
		public void stop() {
			mLocationManager.removeUpdates(this);
		}
		
		public void onLocationChanged(Location location) {
			TextView tv;
			ContentValues data;

			locations.add(location);

			if (iCount > 3) return;
			
			iCount++;
			
			this.stop();
			
			textViewStatus.setText("GPS");
			
			/*
			
			if (isBetterLocation(location, currentBestLocation)){
				currentBestLocation = location;
				
				tv = (TextView)findViewById(R.id.latitude);
				tv.setText( String.format("%f", location.getLatitude()) );
				tv = (TextView)findViewById(R.id.longitude);
				tv.setText( String.format("%f", location.getLongitude()) );
			} else {
			*/
				//location = currentBestLocation;
		
			// Choose best from current list
				tv = (TextView)findViewById(R.id.latitude);
				tv.setText( String.format("%f", location.getLatitude()) );
				tv = (TextView)findViewById(R.id.longitude);
				tv.setText( String.format("%f", location.getLongitude()) );
			//}
			data = new ContentValues();
			data.put("timestamp", location.getTime());
			data.put("longitude", location.getLongitude());
			data.put("latitude", location.getLatitude());
			db.insert("locations", null, data);
		
			
			rFunTimes.start();
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
	        super(context, "locations", null, 1);
	    }

	    @Override
	    public void onCreate(SQLiteDatabase db) {
	        db.execSQL("create table locations (timestamp integer, latitude real, longitude real);");
	    }

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			// TODO Auto-generated method stub
			
		}
	}
	
	/** Called when the activity is first created. */
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		textViewMoving = (TextView)findViewById(R.id.moving);
		textViewStatus = (TextView)findViewById(R.id.status);
		
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);		
		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		
		dbHelper = new LocationsOpenHelper(this);
		db = dbHelper.getWritableDatabase();

		rFunTimes = new FunTimes();
		selAccel = new Accel();
		llGPS = new myLocationListener();
		selAccel.start();
	}
	
	/*
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.game_menu, menu);
	    return true;
	}
	*/
	
	protected void onResume() {
		super.onResume();
		//registerListener();
		//rFunTimes.start();
	}
	protected void onPause() {
		super.onPause();
		//unregisterListener();
		//rFunTimes.stop();
		//mLocationManager.removeUpdates(llGPS);
	}
	protected void onStop() {
		//tFiver.cancel();
		super.onStop();
		//rFunTimes.stop();
		//mLocationManager.removeUpdates(llGPS);
	}
	public void stopApp(View view) {
		rFunTimes.stop();
		selAccel.stop();
		llGPS.stop();
	}
	/*
	public void saveNoise(View view) {
		EditText e;
		String in;
		e = (EditText)findViewById(R.id.editNoise);
		in = e.getText().toString();
		fNoise = Float.parseFloat( in );
		e = (EditText)findViewById(R.id.editGravity);
		in = e.getText().toString();
		fGravity = Float.parseFloat( in );
		e = (EditText)findViewById(R.id.editMinimum);
		in = e.getText().toString();
		fMinimum = Float.parseFloat( in );
	}
	*/
}