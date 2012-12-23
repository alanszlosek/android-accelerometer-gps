package com.szlosek.whenmoving;


import android.app.ListActivity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import android.os.Bundle;
import android.widget.ListAdapter;
import android.widget.SimpleCursorAdapter;


public class DaysActivity extends ListActivity {

	SQLiteOpenHelper dbHelper;
	SQLiteDatabase db;

	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);

		// We'll define a custom screen layout here (the one shown above), but
		// typically, you could just use the standard ListActivity layout.
		//setContentView(R.layout.days_list_item);
		
		SQLiteOpenHelper dbHelper = new DatabaseHelper(this);
		SQLiteDatabase db = dbHelper.getReadableDatabase();
		// Get each day
		Cursor c = db.rawQuery("select day as _id, count(milliseconds) as points from locations group by day order by day desc", null);
		startManagingCursor(c);

		// Now create a new list adapter bound to the cursor.
		// SimpleListAdapter is designed for binding to a Cursor.
		ListAdapter adapter = new SimpleCursorAdapter(
			this,
			R.layout.days_list_item,
			c,
			// Array of cursor columns to bind to.
			new String[] {
				"_id",
				"points"
			},
			// corresponding fields in template
			new int[] {R.id.text1, R.id.text2}
		);
		setListAdapter(adapter);
	}
/*	
	@Override
	protected void onListItemClick (ListView l, View v, int position, long id) {
		
	}
	*/
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (db != null) db.close();
	}
}