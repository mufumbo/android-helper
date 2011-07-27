package com.mufumbo.android.helper;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Log;
import android.widget.ListView;

/**
 * Fast helper to download and display images asynchronously.
 * 
 * First consumes the <b>last</b> images added to the queue and kill the queue tail.
 * It's useful when you are displaying thumbnails on a {@link ListView} and you don't want to loose time
 * downloading the old elements of the list. Instead, you want to show the current elements that were last shown.
 * 
 * Example of usage: 
 * Create a {@link ListView} ViewHolder that implements {@link ThumbEvent} and then 
 * when populating the ViewHolder:
 * thumbLoaderInstance.pushEvent(viewHolderInstanceThatImplementsThumbEvent, true);  
 * 
 * @author mufumbo
 */
public class ThumbLoader {
	public static final int DEFAULT_QUEUE_HARD_LIMIT = 10;
	public static final int DEFAULT_CONSUMER_CONCURRENT = 2;
	public static final int DEFAULT_CACHE_MAXSIZE = 15;

	public static LinkedHashMap<String, SoftReference<FastBitmapDrawable>> cache = new LinkedHashMap<String, SoftReference<FastBitmapDrawable>>();

	// It's a map of URL per ThumbEvent's in order to handle the fact that each URL can be inside different events,
	// otherwise it could be a direct relation. (faster)
	public Hashtable<String, ArrayList<ThumbEvent>> toBeProcessedEvents = new Hashtable<String, ArrayList<ThumbEvent>>();
	public Hashtable<ThumbEvent, String> toBeProcessedEventsUrls = new Hashtable<ThumbEvent, String>();

	ExecutorService tpeThumbs;
	final ExecutorService tpeConsumer = Executors.newSingleThreadExecutor();

	ArrayList<String> queue = new ArrayList<String>();

	Consumer consumer = new Consumer();

	private boolean isConsumerRunning;
	Handler handler;

	private BitmapConverter bitmapConverter;

	int queueHardLimit;
	int consumerConcurrent;

	boolean isCachingEnabled = true;

	public ThumbLoader(final Handler handler, final int queueHardLimit, final int consumerConcurrent) {
		this.handler = handler;
		this.tpeThumbs = Executors.newFixedThreadPool(consumerConcurrent);
		this.queueHardLimit = queueHardLimit;
		this.consumerConcurrent = consumerConcurrent;
	}

	public ThumbLoader(final Handler handler) {
		this(handler, DEFAULT_QUEUE_HARD_LIMIT, DEFAULT_CONSUMER_CONCURRENT);
	}

	public ThumbLoader setBitmapConverter(final BitmapConverter bitmapConverter) {
		this.bitmapConverter = bitmapConverter;
		return this;
	}

	public BitmapConverter getBitmapConverter() {
		return bitmapConverter;
	}

	public ThumbLoader setCachingEnabled(final boolean isCachingEnabled) {
		this.isCachingEnabled = isCachingEnabled;
		return this;
	}

	public void destroy() {
		try {
			if (!this.tpeConsumer.isTerminated()) {
				Log.w(Constants.TAG, "terminating thumb consumer");
				this.tpeConsumer.shutdownNow();
			}

			if (!this.tpeThumbs.isTerminated()) {
				this.tpeThumbs.shutdownNow();
				Log.w(Constants.TAG, "terminating thumb executor");
			}
		}
		catch (final Exception e) {
			Log.e(Constants.TAG, "Error destrying thumb loader: ", e);
		}

		try {
			toBeProcessedEventsUrls.clear();
			toBeProcessedEvents.clear();
		}
		catch (final Exception e) {
			Log.e(Constants.TAG, "Error clearing history ", e);
		}
	}

	BitmapFactory.Options[] options;
	int curOption=0;

	/**
	 * Using RGB565 for it's supposed native support on android. Using a pool of {@link BitmapFactory.Options}.
	 * http://www.rbgrn.net/content/286-how-to-test-android-performance-using-fps
	 * 
	 * http://www.curious-creature.org/2010/12/08/bitmap-quality-banding-and-dithering/
	 * 
	 * @return
	 */
	public synchronized BitmapFactory.Options getOptions() {
		if (options == null) {
			options = new BitmapFactory.Options[consumerConcurrent];
			for (int i=0;i<consumerConcurrent;i++) {
				final BitmapFactory.Options opts = new BitmapFactory.Options();
				opts.inPreferredConfig = Bitmap.Config.RGB_565;
				opts.inTempStorage = new byte[4096]; // 16kb buffer for jpg small.

				//opts.inSampleSize = 1;
				Compatibility.getCompatibility().setPurgeableOptions(opts);
				options[i] = opts;
			}
		}
		final BitmapFactory.Options result = options[curOption];

		curOption++;
		if (curOption >= consumerConcurrent)
			curOption = 0;

		return result;
	}

	/**
	 * Method used to push a {@link ThumbEvent} to the queue.
	 * 
	 * @param event the {@link ThumbEvent} instance
	 * @param isHighPriority if true will be inserted to the front of the queue.
	 */
	public void pushEvent (final ThumbEvent event, final boolean isHighPriority) {
		final String url = event.getUrl();
		SoftReference<FastBitmapDrawable> fc = null;

		synchronized (cache) {
			fc = cache.get(url);
		}

		FastBitmapDrawable result = null;
		if (fc != null)
			result = fc.get();

		if (result == null) {
			ArrayList<ThumbEvent> events = toBeProcessedEvents.get(url);
			if (events == null)
				events = new ArrayList<ThumbEvent>();
			events.add(event);
			this.toBeProcessedEvents.put(url, events);
			this.toBeProcessedEventsUrls.put(event, url);
			if (this.queue.contains(url)) {
				synchronized (this.queue) {
					this.queue.remove(url);
				}
				this.addToQueue(url, true);
				return;
			}

			this.addToQueue(url, isHighPriority);
		}
		else
			event.thumbnailLoaded(result);
	}

	/**
	 * Add a URL to the queue.
	 * 
	 * @param url the URL to the download
	 * @param isHighPriority if true will be inserted to the front of the queue
	 */
	public void addToQueue (final String url, final boolean isHighPriority) {
		if (isHighPriority)
			this.queue.add(0, url);
		else
			this.queue.add(url);

		if (!this.isConsumerRunning()) {
			try {
				// It may happen that something is added to the queue after someone destroyed the activity.
				if (this.tpeConsumer != null && !this.tpeConsumer.isShutdown() && !tpeConsumer.isTerminated())
					this.tpeConsumer.execute(new Consumer());
			}
			catch (final Exception e) {
				Log.e(Constants.TAG, "Error executing new consumer", e);
			}
		}
	}

	/**
	 * Remove an {@link ThumbEvent} from the queue.
	 * 
	 * @param event the {@link ThumbEvent} element
	 */
	public void removeEvent (final ThumbEvent event) {
		final String url = this.toBeProcessedEventsUrls.get(event);
		if (url != null) {
			final ArrayList<ThumbEvent> events = this.toBeProcessedEvents.get(url);
			if (events != null) {
				events.remove(event);
				if (events.size() == 0) {
					this.toBeProcessedEvents.remove(url);
				}
			}
		}
	}

	/**
	 * @return true if the consumer is running
	 */
	public boolean isConsumerRunning() {
		return isConsumerRunning;
	}

	protected void cleanupCache() {
		synchronized (cache) {
			final Set<String> cacheKeys = cache.keySet();
			while (cache.size() > DEFAULT_CACHE_MAXSIZE) {
				final String firstKey = cacheKeys.iterator().next();
				if (Dbg.IS_DEBUG) Dbg.debug("ThumbLoader.cleanupCache Purging: " + firstKey);
				forceCleanup(firstKey);
			}
		}
	}

	public void forceCleanup(final String url) {
		SoftReference<FastBitmapDrawable> item = null;
		synchronized (cache) {
			item = cache.get(url);
		}

		if (item != null) {
			final FastBitmapDrawable image = item.get();
			if (image != null) {
				item.clear();

				// TODO: figure out how to do this. here it crashed:
				// 06-24 04:55:30.036: ERROR/AndroidRuntime(21377): java.lang.RuntimeException: Canvas: trying to use a recycled bitmap android.graphics.Bitmap@408ab840
				// image.getBitmap().recycle();

				if (Dbg.IS_DEBUG) Dbg.debug("ThumbLoader.cleanupCache Recycled: " + url);
			}
			else if (Dbg.IS_DEBUG) {
				Dbg.debug("ThumbLoader.cleanupCache GC'ed: " + url);
			}
		}

		synchronized (cache) {
			cache.remove(url);
		}
	}

	/**
	 * Thumbnail loader that downloads the thumbnail from a URL and consumes it.
	 * 
	 * @author rsanches
	 */
	class Loader implements Runnable {
		public boolean isExecuting = true;
		String url;
		public Loader(final String url) {
			this.url = url;
		}

		@Override
		public void run() {
			FastBitmapDrawable result = null;
			try {
				final String url = this.url;
				final ArrayList<ThumbEvent> events = toBeProcessedEvents.get(url);

				//Log.i(Constants.TAG, "executing loading: "+queue.size()+" url:"+url);
				if (events != null && events.size() > 0) {
					final DownloadResult dr = events.get(0).download(url);

					if (dr != null) {
						final byte[] b = dr.bytes;

						Bitmap bit = null;
						if (b != null) {
							final long start = System.currentTimeMillis();
							final BitmapFactory.Options options = getOptions();

							bit = BitmapFactory.decodeByteArray(b, 0, b.length, options);
							if (bit != null) {
								if (Dbg.IS_DEBUG) Dbg.debug("decodeBytes thumbnail " + bit.getWidth() + " took "+(System.currentTimeMillis()-start));
								if (bitmapConverter != null) {
									final Bitmap old = bit;
									bit = bitmapConverter.convert(bit);
									// force free native bitmap link
									old.recycle();
								}
							}
							else {
								Log.e(Constants.TAG, "Downloaded thumbnail " + b.length + " but couldn't make a bitmap.");
							}
						}
						else {
							Log.w(Constants.TAG, "image for "+url+" was invalid");
						}

						if (bit != null || dr.isNotRetryable) {
							result = new FastBitmapDrawable(bit);
							if (isCachingEnabled)
							{
								final SoftReference<FastBitmapDrawable> sr = new SoftReference<FastBitmapDrawable>(result);
								synchronized (cache) {
									cache.put(url, sr);
								}
							}
						}
					}
				}
				//else
				//Log.i(Constants.TAG, "event for "+url+" was skipped");
				cleanupCache();
			}
			catch (final Exception e) {
				Log.e(Constants.TAG, "Error loading thumbnail: "+this.url+" -> "+ e.getMessage());
				if (Dbg.IS_DEBUG) Dbg.debugError("error loading"+ url, e);
			}
			finally {
				this.isExecuting = false;
				final ArrayList<ThumbEvent> events = toBeProcessedEvents.get(this.url);
				if (events != null && events.size() > 0) {
					final FastBitmapDrawable t = result;
					ThumbLoader.this.handler.post(new Runnable() {
						@Override
						public void run() {
							for (final ThumbEvent event : events)
								event.thumbnailLoaded(t);
						}
					});
				}
				else
					if (Dbg.IS_DEBUG) Dbg.debug("thumbnail event was removed for "+this.url);
			}
		}
	}

	/**
	 * Thread that consumes the queue.
	 * 
	 * @author rsanches
	 */
	class Consumer implements Runnable {
		@Override
		public void run() {
			try {
				isConsumerRunning = true;
				while (!ThumbLoader.this.queue.isEmpty()) {
					final ArrayList<String> lst = new ArrayList<String> ();

					int size = consumerConcurrent;
					if (consumerConcurrent > ThumbLoader.this.queue.size())
						size = ThumbLoader.this.queue.size();

					lst.addAll(ThumbLoader.this.queue.subList(0, size));
					synchronized (ThumbLoader.this.queue) {
						ThumbLoader.this.queue.removeAll(lst);
					}

					final int queueSize = queue.size();
					if (queueSize > queueHardLimit) {
						final ArrayList<String> tmp = new ArrayList<String>(ThumbLoader.this.queue.subList(0, queueHardLimit));
						ThumbLoader.this.queue.clear();
						ThumbLoader.this.queue.addAll(tmp);

						if (Dbg.IS_DEBUG) Dbg.debug("Thumbs hardlimit! killing queue exceed.");
						//final ArrayList<String> toDiscard = new ArrayList<String>(ThumbLoader.this.queue.subList(queueHardLimit, queueSize));
					}

					//Log.i(Constants.TAG, "created chunck:"+lst.size()+" remained:"+queue.size());

					final int len = lst.size();
					for (int i=0;i<len;i++) {
						final String url = lst.get(i);
						ThumbLoader.this.tpeThumbs.submit(new Loader(url));
					}

					ThumbLoader.this.tpeThumbs.awaitTermination(1000, TimeUnit.MILLISECONDS);
				}
			} catch (final Exception e) {
				//Log.e(Constants.TAG, "uow", e);
			}
			finally {
				isConsumerRunning = false;
			}
		}
	}

	/**
	 * Interface to be implemented in order to return the URL and to download a url.
	 * 
	 * @author rsanches
	 */
	public static interface ThumbEvent {
		public String getUrl();
		public void thumbnailLoaded (FastBitmapDrawable thumbnail);

		//HttpUtils.getFromUrl(this.url, null, null, false);
		public DownloadResult download(String url);

	}

	/**
	 * Simple drawable that does less calculations.
	 * 
	 * @author rsanches
	 */
	public static class FastBitmapDrawable extends Drawable {
		private final Bitmap mBitmap;
		int width;
		int height;

		public FastBitmapDrawable(final Bitmap b) {
			this.mBitmap = b;
			if (this.mBitmap != null) {
				this.width = this.mBitmap.getWidth();
				this.height = this.mBitmap.getHeight();
			}
		}

		@Override
		public void draw(final Canvas canvas) {
			if (this.mBitmap != null)
				canvas.drawBitmap(this.mBitmap, 0.0f, 0.0f, null);
		}

		@Override
		public int getOpacity() {
			return PixelFormat.TRANSLUCENT;
		}

		@Override
		public void setAlpha(final int alpha) {
		}

		@Override
		public void setColorFilter(final ColorFilter cf) {
		}

		@Override
		public int getIntrinsicWidth() {
			return this.width;
		}

		@Override
		public int getIntrinsicHeight() {
			return this.height;
		}

		@Override
		public int getMinimumWidth() {
			return this.width;
		}

		@Override
		public int getMinimumHeight() {
			return this.height;
		}

		public Bitmap getBitmap() {
			return this.mBitmap;
		}
	}

	public static interface BitmapConverter {
		public Bitmap convert(Bitmap input);
	}

	public static class DownloadResult {
		public boolean isNotRetryable;
		public byte[] bytes;
	}
}
