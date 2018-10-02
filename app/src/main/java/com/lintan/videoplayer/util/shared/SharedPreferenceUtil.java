package com.lintan.videoplayer.util.shared;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v4.content.SharedPreferencesCompat;

import com.lintan.videoplayer.util.log.VideoLog;

/**
 * copied from tct video player.
 * Created by lintan on 9/5/16.
 */
public class SharedPreferenceUtil {
	private static final String TAG = "VideoPlayer/SharedPreferencesUtil";

	public static final String FILE_NAME = "share_data";

	public static void put(Context context, String key, Object object) {

		SharedPreferences sp = context.getSharedPreferences(FILE_NAME,
		                                                    Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sp.edit();
		if (object instanceof String) {
			editor.putString(key, (String) object);
		} else if (object instanceof Integer) {
			editor.putInt(key, (Integer) object);
		} else if (object instanceof Boolean) {
			editor.putBoolean(key, (Boolean) object);
		} else if (object instanceof Float) {
			editor.putFloat(key, (Float) object);
		} else if (object instanceof Long) {
			editor.putLong(key, (Long) object);
		} else {
			editor.putString(key, object.toString());
		}

		SharedPreferencesCompat.EditorCompat.getInstance().apply(editor);
	}

	public static Object get(Context context, String key, Object defaultObject) {
		SharedPreferences sp = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);

		if (defaultObject instanceof String) {
			return sp.getString(key, (String) defaultObject);
		} else if (defaultObject instanceof Integer) {
			return sp.getInt(key, (Integer) defaultObject);
		} else if (defaultObject instanceof Boolean) {
			if(!contains(context, key)){
				VideoLog.d(TAG, "can not fond key(" + key + "), return default value: " + defaultObject);
				return defaultObject;
			}
			return sp.getBoolean(key, (Boolean) defaultObject);
		} else if (defaultObject instanceof Float) {
			return sp.getFloat(key, (Float) defaultObject);
		} else if (defaultObject instanceof Long) {
			return sp.getLong(key, (Long) defaultObject);
		}

		return null;
	}

	public static void remove(Context context, String key) {
		if(!contains(context, key)){
			return;
		}
		SharedPreferences sp = context.getSharedPreferences(FILE_NAME,
		                                                    Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sp.edit();
		editor.remove(key);
		SharedPreferencesCompat.EditorCompat.getInstance().apply(editor);
	}

	public static boolean contains(Context context, String key) {
		SharedPreferences sp = context.getSharedPreferences(FILE_NAME,
		                                                    Context.MODE_PRIVATE);
		return sp.contains(key);
	}
}
