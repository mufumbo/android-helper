package com.mufumbo.helper;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.webkit.WebSettings;
import android.widget.TabHost.TabSpec;

public abstract class Compatibility {
	protected static Compatibility instance;

	public static Compatibility getCompatibility () {
		if (instance == null) {
			if (Dbg.IS_DEBUG) Dbg.debug("Compatibility on "+Build.VERSION.SDK);
			if ("3".equals(Build.VERSION.SDK)) {
				instance = new CompatibilityMin3();
			}
			else {
				instance = CompatibilityInt.getCompatibility();
			}
		}
		return instance;
	}

	public abstract int getSDKVersion();
	public abstract void setPurgeableOptions(BitmapFactory.Options opts);
	public abstract void setWebSettingsCache(WebSettings settings);
	public abstract void setTabSpecStyle(final Context c, final TabSpec ts, final String text, int background, int minHeightDip);
	public abstract boolean isC2DMEnabled();
}