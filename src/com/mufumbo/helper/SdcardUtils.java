package com.mufumbo.helper;

import java.io.File;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;

public class SdcardUtils {
	public static final String DEFAULT_SDCARD_DIR = Environment.getExternalStorageDirectory()+"/data/"+Constants.PACKAGE_NAME;
	public static final String DEFAULT_SDCARD_TMP_DIR = DEFAULT_SDCARD_DIR+"tmp/";
	public static final String TMP_UPLOAD_FILE = "tmp_"+Constants.TAG+".jpg";

	public static File getTmpDir() {
		final File dir = new File (DEFAULT_SDCARD_TMP_DIR);
		if (!dir.exists())
			dir.mkdirs();
		return dir;
	}

	public static File getWebCacheDir() {
		try {
			final File dir = new File (DEFAULT_SDCARD_TMP_DIR+"/webcache/");
			if (!dir.exists())
				dir.mkdirs();
			return dir;
		}
		catch (final Exception e) {
			Log.e(Constants.TAG, "error on getWebCacheDir", e);
		}
		return null;
	}

	// Put on the root of the sdcard to make it easier for the user to cleanup
	public static File getUploadTmpFile () {
		//File dir = SdcardUtils.getTmpDir();
		final File dir = Environment.getExternalStorageDirectory();
		final File file = new File( dir, TMP_UPLOAD_FILE);
		return file;
	}

	public static void cleanupOldUploadTmpFile() {
		final File f = getUploadTmpFile();
		if (f.exists()) f.delete();
	}

	public static String getImageMediaPath(final Activity c, final Uri uri) {
		final String[] projection = { MediaColumns.DATA };
		final Cursor cursor = c.managedQuery(uri, projection, null, null, null);
		final int column_index = cursor.getColumnIndexOrThrow(MediaColumns.DATA);
		cursor.moveToFirst();
		return cursor.getString(column_index);
	}
}
