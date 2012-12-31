package com.szlosek.whenmoving;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;




public class CalibrationActivity extends Activity implements AdapterView.OnItemSelectedListener, SensorEventListener {
	private TextView mStatus;
	private ArrayAdapter<CharSequence> mAdapter;
	private Spinner mThreshold;
	private SensorManager mSensorManager;
	private int iAccelReadings, iAccelSignificantReadings;
	private long iAccelTimestamp;
	private SharedPreferences sp;

	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setContentView(R.layout.calibration_activity);
		
		/*
		setContentView(R.layout.calibration_activity);
		mThreshold = (MapView) findViewById(R.id.calibration_threshold);
		
		mThreshold.setMinValue(1);
		mThreshold.setMaxValue(100);
		*/
		
		sp = PreferenceManager.getDefaultSharedPreferences(MainApplication.getInstance());
		mStatus = (TextView) findViewById(R.id.calibration_status);
		
		Spinner spinner = (Spinner) findViewById(R.id.calibration_threshold);
		if (spinner == null) return;
		// Create an ArrayAdapter using the string array and a default spinner layout
		mAdapter = ArrayAdapter.createFromResource(
			this,
			R.array.pref_thresholdValues,
			android.R.layout.simple_spinner_item
		);
		// Specify the layout to use when the list of choices appears
		mAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		// Apply the adapter to the spinner
		spinner.setAdapter(mAdapter);
		spinner.setOnItemSelectedListener(this);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		Spinner spinner = (Spinner) findViewById(R.id.calibration_threshold);
		String threshold = sp.getString("pref_threshold", "0.30");
		CharSequence threshold2 = threshold.subSequence(0, threshold.length() );
		Log.d("WM", String.format("pos %d", mAdapter.getPosition( threshold2 ) ) );
		spinner.setSelection( mAdapter.getPosition( threshold2 ) );
		
		startAccelerometer();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		stopAccelerometer();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
	
	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
		// An item was selected. You can retrieve the selected item using
		String s;
		String threshold;
		SharedPreferences.Editor spe;
		
		s = (String) parent.getItemAtPosition(pos);
		threshold = s.substring(0,4);

		spe = sp.edit();
		spe.putString("pref_threshold", threshold);
		spe.commit();
		
		MainApplication.onPreferenceChange();
    }
	public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }
	
	
	
	public void startAccelerometer() {
		iAccelReadings = 0;
		iAccelSignificantReadings = 0;
		iAccelTimestamp = System.currentTimeMillis();
		// should probably store handles to these earlier, when service is created
		mSensorManager = (SensorManager) getSystemService(this.SENSOR_SERVICE);
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
		if (accel > MainApplication.prefThreshold2) {
			iAccelSignificantReadings++;
		}
		
		//Log.d("When", String.format("event: %f %f %f %f %f", x, y, z, accel, 0.600));
		
		// Get readings for 2 seconds
		if ( (System.currentTimeMillis() - iAccelTimestamp) < 2000) return;
		
		// Appeared to be moving 30% of the time?
		// If the bar is this low, why not report motion at the first significant reading and be done with it?
		if (((1.0*iAccelSignificantReadings) / iAccelReadings) > 0.30) {
			mStatus.setText("Moving");
			
			
		} else {
			mStatus.setText("Stationary");
 		}
		iAccelTimestamp = System.currentTimeMillis();
		iAccelReadings = 0;
		iAccelSignificantReadings = 0;
	}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// can be safely ignored
	}
}
