package com.mufumbo.android.helper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

public class ImageHelper {
	static float DENSITY;
	
	public static int dipToPixel(final Context ctx, final int dips) {
		if (DENSITY == 0)
			DENSITY = ctx.getResources().getDisplayMetrics().density;
		return (int) (DENSITY * dips + 0.5f);
	}

	public static int pixelToDip(final Context ctx, final int pixels) {
		if (DENSITY == 0)
			DENSITY = ctx.getResources().getDisplayMetrics().density;
		return (int) ( pixels / DENSITY - 0.5f);
	}

	public static void releaseBackground(final View background) {
		if (background != null)
		{
			if (Dbg.IS_DEBUG) Dbg.debug("releaseBackground background not null");
			if (background.isDrawingCacheEnabled())
			{
				if (Dbg.IS_DEBUG) Dbg.debug( "releaseBackground Recycling background" );
				background.getDrawingCache().recycle();
			}

			if (background.getBackground() != null)
			{
				background.getBackground().setCallback(null);
			}

			if (ImageView.class.isInstance(background)) {
				if (Dbg.IS_DEBUG) Dbg.debug("releaseBackground ImageView");

				final ImageView img = (ImageView) background;
				if (img.getDrawable() != null)
				{
					if (BitmapDrawable.class.isInstance(img.getDrawable()))
					{
						if (Dbg.IS_DEBUG) Dbg.debug("releaseBackground ImageView recycle");
						final BitmapDrawable bitd = (BitmapDrawable) img.getDrawable();
						bitd.getBitmap().recycle();
						img.setImageBitmap(null);
					}
					img.getDrawable().setCallback(null);
				}
				img.setImageDrawable(null);
			}

			background.setBackgroundDrawable(null);
		}
	}

	public static Drawable processDrawableFromBitmap(final Context context, final Bitmap immutableBitmap, final int color) {
		final Bitmap mutableBitmap = immutableBitmap.copy(Bitmap.Config.ARGB_4444, false);

		final Drawable d = new BitmapDrawable(mutableBitmap);

		//mutate it
		//d.setColorFilter(new LightingColorFilter(Color.BLUE, color));
		d.setColorFilter(color, Mode.SRC_ATOP);
		return d;
	}

	public static ImageView processDrawableFromBitmapForView(final ImageView view, final Bitmap immutable, final int color) {
		if (view.getDrawable() != null)
			view.getDrawable().invalidateSelf();
		view.setImageDrawable( processDrawableFromBitmap(view.getContext(), immutable, color) );
		//view.getDrawable().mutate();
		return view;
	}

	public static void processTransparentColor(final ImageView foreground, final ImageView background,
			final int drawableId, final int color, final int width, final int height)
	{
		final Bitmap immutable = ImageHelper.decodeResource(foreground.getResources(), drawableId, width, height);
		final BitmapDrawable drawable = new BitmapDrawable(immutable);
		drawable.setDither(true);
		drawable.setAntiAlias(true);
		drawable.setFilterBitmap(true);
		foreground.setImageDrawable(drawable);

		if (Dbg.IS_DEBUG) Dbg.debug("processTransparentColor c: " + color + " w:" + width +
				" h:" + height + " rw:" + immutable.getWidth() + " rh:" + immutable.getHeight());

		foreground.setAlpha(190);

		if (Compatibility.getCompatibility().getSDKVersion() > 7)
		{
			processDrawableFromBitmapForView(background, immutable, color);
		}
		else
		{
			final Drawable backgroundDrawable = processDrawableFromBitmap(background.getContext(), immutable, color);
			background.setImageDrawable(backgroundDrawable);
			// set filter in the ImageView instead of Bitmap
			background.setColorFilter(color, Mode.SRC_ATOP);
		}
	}

	public static Bitmap getRoundedCornerBitmap(final Bitmap bitmap, final int pixels) {
		try
		{
			final Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap
					.getHeight(), Config.ARGB_8888);
			final Canvas canvas = new Canvas(output);

			final int color = 0xff424242;
			final Paint paint = new Paint();
			final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
			final RectF rectF = new RectF(rect);
			final float roundPx = pixels;

			paint.setAntiAlias(true);
			canvas.drawARGB(0, 0, 0, 0);
			paint.setColor(color);
			canvas.drawRoundRect(rectF, roundPx, roundPx, paint);

			paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
			canvas.drawBitmap(bitmap, rect, rect, paint);
			return output;
		}
		catch (final Exception e) {
			Log.e(Constants.TAG, "Error rounding corners", e);
		}
		return null;
	}

	public static class RoundedCornerBitmapConverter
	implements ThumbLoader.BitmapConverter {
		int pixels;

		public RoundedCornerBitmapConverter(final int pixels) {
			this.pixels = pixels;
		}

		@Override
		public Bitmap convert(final Bitmap input) {
			return getRoundedCornerBitmap(input, pixels);
		}
	}

	//decodes image and scales it to reduce memory consumption
	public static Bitmap decodeFile(final File f, final int requiredSize) {
		try {
			//Decode image size
			final BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeStream(new FileInputStream(f),null,o);

			//Find the correct scale value. It should be the power of 2.
			int width_tmp=o.outWidth, height_tmp=o.outHeight;
			int scale=1;
			while(true){
				if(width_tmp/2<requiredSize || height_tmp/2<requiredSize)
					break;
				width_tmp/=2;
				height_tmp/=2;
				scale*=2;
			}

			//Decode with inSampleSize
			final BitmapFactory.Options o2 = new BitmapFactory.Options();
			o2.inSampleSize=scale;
			return BitmapFactory.decodeStream(new FileInputStream(f), null, o2);
		} catch (final FileNotFoundException e) {
			Log.e(Constants.TAG, "Error with "+f, e);
		}
		return null;
	}

	//decodes image and scales it to reduce memory consumption
	public static Bitmap decodeResource(final Resources res, final int id, final int requiredWidth, final int requiredHeight) {
		try {
			//Decode image size
			final BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeResource(res, id, o);

			//Find the correct scale value. It should be the power of 2.
			int width_tmp=o.outWidth, height_tmp=o.outHeight;
			int scale=1;
			while(true){
				if(width_tmp/2<requiredWidth || height_tmp/2<requiredHeight)
					break;
				width_tmp/=2;
				height_tmp/=2;
				scale*=2;
			}

			//Decode with inSampleSize
			final BitmapFactory.Options o2 = new BitmapFactory.Options();
			o2.inSampleSize=scale;

			if (Compatibility.getCompatibility().getSDKVersion() > 4)
			{
				// Don't use the automatic scalling
				// TODO: PUT THIS BACK!!!!!!!!!! THE QUALITY IS MUCH BETTER
				// DISABLED FOR TESTING ON FORCE CLOSE FIXES
				o2.inScaled = false;

				//o2.inPurgeable = true;
			}
			o2.outWidth = requiredWidth;
			o2.outHeight = requiredHeight;
			o2.inDither = true;
			//o2.inPreferQualityOverSpeed = true;
			final Bitmap bit = BitmapFactory.decodeResource(res, id, o2);

			final Bitmap scaled = Bitmap.createScaledBitmap(bit, requiredWidth, requiredHeight, false);

			bit.recycle();

			return scaled;
		} catch (final Exception e) {
			Log.e(Constants.TAG, "Error with resource "+id, e);
		}
		return null;
	}
}
