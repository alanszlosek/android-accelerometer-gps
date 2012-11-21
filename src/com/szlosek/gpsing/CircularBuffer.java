package com.szlosek.gpsing;


// From: http://www.cs.utsa.edu/~wagner/CS2213/queue/queue.html
public class CircularBuffer {
	private int qMaxSize;// max queue size
	private int fp = 0;  // front pointer
	private int rp = 0;  // rear pointer
	private int qs = 0;  // size of queue
	private Object[] q;    // actual queue

	public CircularBuffer(int size) {
		qMaxSize = size;
		fp = -1;
		rp = 0;
		qs = 0;
		q = new Object[qMaxSize];
	}

	public Object delete() {
		if (!emptyq()) {
			qs--;
			fp = (fp + 1)%qMaxSize;
			return q[fp];
		} else {
			System.err.println("Underflow");
			return null;
		}
	}

	public void insert(Object c) {
		fp++;
		if (fp == qMaxSize) {
			fp = 0;
		}
		q[ fp ] = c;
		if (qs < qMaxSize) {
			qs++;
		}
	}

	public boolean emptyq() {
		return qs == 0;
	}

	public int size() {
		return qs;
	}
	
	public Object get(int i) {
		if (qs == 0) {
			return null;
		}
		// offset from fp
		int j = fp + i;
		if (j >= qMaxSize) {
			j -= qMaxSize;
		}
		return q[ j ];
	}

	public boolean fullq() {
		return qs == qMaxSize;
	}
}
