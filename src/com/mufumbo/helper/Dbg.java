package com.mufumbo.helper;

import android.util.Log;

public class Dbg {
	public static boolean IS_DEBUG = Constants.IS_DEBUG;

	public static void debug (final String s) {
		if (IS_DEBUG)
			Log.e(Constants.TAG, s);
	}

	public static void debugError (final String s, final Throwable e) {
		if (IS_DEBUG)
			Log.e(Constants.TAG, s, e);
	}
}