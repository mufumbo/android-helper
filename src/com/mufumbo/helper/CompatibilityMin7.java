package com.mufumbo.helper;

import java.io.File;

import android.webkit.WebSettings;

public class CompatibilityMin7 extends CompatibilityMin4 {
	@Override
	public void setWebSettingsCache(final WebSettings settings) {
		final File cacheDir = SdcardUtils.getWebCacheDir();
		if (cacheDir != null && cacheDir.exists()) {
			if (Dbg.IS_DEBUG) Dbg.debug("Setting WebSettings Cache to "+cacheDir.getAbsolutePath());
			settings.setAppCacheEnabled(true);
			settings.setAppCacheMaxSize(1024*1024); // 1MB in the sdcard
			settings.setAppCachePath(cacheDir.getAbsolutePath());
			settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
		}
	}

	@Override
	public boolean isC2DMEnabled() {
		return true;
	}

}
