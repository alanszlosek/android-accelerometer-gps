package com.szlosek.whenmoving;

import android.app.ListActivity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import java.util.String;


public class CalibrationActivity extends Activity {

	protected EditText mThreshold;

	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.calibration_activity);
		mThreshold = (MapView) findViewById(R.id.calibration_threshold);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		mThreshold.setText( String.format("%0.2f", MainApplication.prefThreshold) );
	}
	
	@Override
	protected void onPause() {
		super.onPuase();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
}