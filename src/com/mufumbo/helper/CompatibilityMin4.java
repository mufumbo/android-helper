package com.mufumbo.helper;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.TabHost.TabSpec;

public class CompatibilityMin4 extends Compatibility {

	@Override
	public void setPurgeableOptions(final BitmapFactory.Options opts) {
		// Which kind of reference will be used to recover the Bitmap data after being clear, when it will be used in the future
		opts.inInputShareable = true;

		// Tell to gc that whether it needs free memory, the Bitmap can be cleared
		opts.inPurgeable = true;
	}

	@Override
	public int getSDKVersion() {
		return Build.VERSION.SDK_INT;
	}

	@Override
	public void setWebSettingsCache(final WebSettings settings) {
		settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
	}

	@Override
	public void setTabSpecStyle(final Context c, final TabSpec ts, final String text, final int background, final int minHeightDip) {
		final Button t = new Button(c);
		t.setText(text);
		t.setBackgroundResource(background);

		final int minHeight = ImageHelper.dipToPixel(c, minHeightDip);

		t.setHeight(minHeight);
		t.setTextColor(Color.WHITE);
		ts.setIndicator(t);
	}

	@Override
	public boolean isC2DMEnabled() {
		return false;
	}
}
