package com.szlosek.whenmoving;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
	public DatabaseHelper(Context context) {
		super(context, "locations.db", null, 4);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("create table locations (day integer, hour integer, milliseconds integer, latitude real, longitude real, altitude real, gpsStart integer, accuracy real, bearing real, speed real, provider text, best integer);");
		db.execSQL("create index day_hour on locations (day, hour);");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion < 4 && newVersion == 4) {
			db.execSQL("drop index day_hour;");
			
			db.execSQL("create table newLocations (day integer, hour integer, milliseconds integer, latitude real, longitude real, altitude real, gpsStart integer, accuracy real, bearing real, speed real, provider text, best integer);");
			
			db.execSQL("insert into newLocations (day, hour, milliseconds, latitude, longitude, altitude, gpsStart, accuracy, bearing, speed, provider, best) select strftime('%Y%m%d', date(milliseconds/1000, 'unixepoch')) as day, strftime('%H', datetime(milliseconds/1000, 'unixepoch')) as hour, milliseconds, latitude, longitude, altitude, gpsStart, accuracy, bearing, speed, provider, best from locations;");

			db.execSQL("alter table locations rename to oldLocations;");
			db.execSQL("alter table newLocations rename to locations;");
			db.execSQL("create index day_hour on locations (day, hour);");
			db.execSQL("drop table oldLocations;");
		}
	}
 }
