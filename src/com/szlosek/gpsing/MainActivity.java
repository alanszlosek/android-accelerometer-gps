package com.szlosek.gpsing;

import java.util.Calendar;
import java.util.GregorianCalendar;
import android.os.Bundle;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;

public class MainActivity extends Activity {

	

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	
	@Override
    protected void onStart() {
		super.onStart();
		
		Log.d("GPSing","Activity started");
		again();
    }
    
    public void again() {

        AlarmManager mgr = (AlarmManager)getSystemService(ALARM_SERVICE);
        
        Intent i = new Intent(this, GPSingReceiver.class);
        
        Calendar cal = new GregorianCalendar();
        cal.add(Calendar.SECOND, 5);
        
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, i, 0);
        mgr.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
    }

}
