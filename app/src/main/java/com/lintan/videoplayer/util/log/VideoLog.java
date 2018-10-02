package com.lintan.videoplayer.util.log;

/**
 * wrap the log if need to disable the log some time.
 * Created by Administrator on 2016/8/20.
 */
public final class VideoLog {

	public static final boolean DEBUG = true;
	private static final int RETURN_DEFAULT = -1;

	public static int v(String tag, String message){
		if (DEBUG)
			return android.util.Log.v(tag, message);
		return RETURN_DEFAULT;
	}

	public static int d(String tag, String message) {
		if (DEBUG)
			return android.util.Log.d(tag, message);
		return RETURN_DEFAULT;
	}

	public static int d(String tag, String message, Throwable tr) {
		if (DEBUG)
			return android.util.Log.d(tag, message, tr);
		return RETURN_DEFAULT;
	}

	public static int i(String tag, String message) {
		if (DEBUG)
			return android.util.Log.i(tag, message);
		return RETURN_DEFAULT;
	}

	public static int i(String tag, String message, Throwable tr) {
		if (DEBUG)
			return android.util.Log.i(tag, message, tr);
		return RETURN_DEFAULT;
	}

	public static int w(String tag, String message) {
		if (DEBUG)
			return android.util.Log.w(tag, message);
		return RETURN_DEFAULT;
	}

	public static int w(String tag, String message, Throwable tr) {
		if (DEBUG)
			return android.util.Log.w(tag, message, tr);
		return RETURN_DEFAULT;
	}

	public static int e(String tag, String message) {
		if (DEBUG)
			return android.util.Log.e(tag, message);
		return RETURN_DEFAULT;
	}

	public static int e(String tag, String message, Throwable tr) {
		if (DEBUG)
			return android.util.Log.e(tag, message, tr);
		return RETURN_DEFAULT;
	}

}
