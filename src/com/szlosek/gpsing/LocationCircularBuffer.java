package com.szlosek.gpsing;
import android.location.Location;


// From: http://www.cs.utsa.edu/~wagner/CS2213/queue/queue.html
public class LocationCircularBuffer {
	private int qMaxSize;// max queue size
	private int fp = 0;  // front pointer
	private int rp = 0;  // rear pointer
	private int qs = 0;  // size of queue
	private Location[] q;    // actual queue

	public LocationCircularBuffer(int size) {
		qMaxSize = size;
		fp = -1;
		rp = 0;
		qs = 0;
		q = new Location[qMaxSize];
	}

	public Location delete() {
		if (!emptyq()) {
			qs--;
			fp = (fp + 1)%qMaxSize;
			return q[fp];
		} else {
			System.err.println("Underflow");
			return null;
		}
	}

	public void insert(Location c) {
		fp++;
		if (fp == qMaxSize) {
			fp = 0;
		}
		q[ fp ] = c;
		qs++;
	}

	public boolean emptyq() {
		return qs == 0;
	}

	public int size() {
		return qs;
	}
	
	public Location get(int i) {
		if (qs == 0) {
			return null;
		}
		return q[ i ];
	}

	public boolean fullq() {
		return qs == qMaxSize;
	}
}
