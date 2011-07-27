package com.mufumbo.helper;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.webkit.WebSettings;
import android.widget.TabHost.TabSpec;

public class CompatibilityMin3 extends Compatibility {

	@Override
	public void setPurgeableOptions(final BitmapFactory.Options opts) {
	}

	@Override
	public int getSDKVersion() {
		return 3;
	}

	@Override
	public void setWebSettingsCache(final WebSettings settings) {
		settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
	}

	@Override
	public void setTabSpecStyle(final Context c, final TabSpec ts, final String text, final int background, final int minHeightDip) {
		ts.setIndicator(text);
	}

	@Override
	public boolean isC2DMEnabled() {
		return false;
	}

}
