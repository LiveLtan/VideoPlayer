package com.lintan.videoplayer.util.cache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.util.LruCache;

import com.lintan.videoplayer.R;

/**
 * cache the bitmap by wrap drawable.
 * Created by Administrator on 2016/9/1.
 */
public class ImageDrawableCache {

	private static final String TAG = "VideoPlayer/ImageDrawableCache";
	private static ImageDrawableCache sImageDrawableCache;

	private static final float WIDTH_SCALE = 2.5f;
	private static final float HEIGHT_SCALE = 1.5f;
	private static final float CACHE_RATE_OF_WHOLE_MEM = 1.0f / 8;
	private static int sMemoryCacheMaxSize = (int) (Runtime.getRuntime().maxMemory() * CACHE_RATE_OF_WHOLE_MEM);

	private LruCache<String, BitmapDrawable> mLruCache;
	private int defaultWidth;
	private int defaultHeight;

	private ImageDrawableCache(Context context) {

		Bitmap tmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_video_default);
		defaultHeight = tmp.getHeight();
		defaultWidth = tmp.getWidth();
		tmp.recycle();
		mLruCache = new LruCache<String, BitmapDrawable>(sMemoryCacheMaxSize) {
			@Override
			protected int sizeOf(String key, BitmapDrawable value) {
				if (null == value || null == value.getBitmap()) {
					return 0;
				}
				return value.getBitmap().getByteCount();
			}
		};
	}

	public static ImageDrawableCache getInstance(Context context) {
		if (null == sImageDrawableCache) {
			synchronized (ImageDrawableCache.class) {
				if (null == sImageDrawableCache) {
					sImageDrawableCache = new ImageDrawableCache(context);
				}
			}
		}
		return sImageDrawableCache;
	}

	/**
	 * add bitmap to cache.
	 *
	 * @param key      key
	 * @param drawable bitmap
	 */
	public synchronized void addBitmapToMemoryCache(String key, BitmapDrawable drawable) {
		if (null == getBitmapFromMemoryCache(key)) {
			mLruCache.put(key, drawable);
		}
	}

	/**
	 * get a bitmap from cache.
	 *
	 * @param key key
	 * @return bitmap
	 */
	public synchronized BitmapDrawable getBitmapFromMemoryCache(String key) {
		return mLruCache.get(key);
	}

	/**
	 * remove the special key you want to delete.
	 *
	 * @param key key of BitmapDrawable.
	 */
	public synchronized void removeFromMemoryCache(String key) {
		mLruCache.remove(key);
	}

	public int getDefaultWidth() {
		return (int) (defaultWidth * WIDTH_SCALE);
	}

	public int getDefaultHeight() {
		return (int) (defaultHeight * HEIGHT_SCALE);
	}
}
