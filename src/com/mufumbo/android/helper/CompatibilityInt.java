package com.mufumbo.android.helper;

import android.os.Build;

public class CompatibilityInt {
	public static Compatibility getCompatibility () {
		if (Build.VERSION.SDK_INT >= 7)
			return new CompatibilityMin7();
		else
			return new CompatibilityMin4();
	}
}
