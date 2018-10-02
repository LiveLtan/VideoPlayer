package com.lintan.videoplayer.util.media;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.BaseColumns;
import android.provider.MediaStore;

import com.lintan.videoplayer.bean.VideoEntity;
import com.lintan.videoplayer.util.log.VideoLog;

import java.util.ArrayList;

/**
 * storage util
 * Created by lintan on 8/23/16.
 */
public class MediaAccess {

	private static final String TAG = "VideoPlayer/MediaUtil";

	public static final Uri VIDEO_URI = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
	public static final Uri VIDEO_URI_THUMBNAIL = MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI;

	private static final String ORDER_COLUMN = MediaStore.Video.VideoColumns.DATE_TAKEN + " DESC, " +
	                                           BaseColumns._ID + " DESC ";

	public static final int INDEX_ID = 0;
	public static final int INDEX_TAKEN_DATE = INDEX_ID + 1;
	public static final int INDEX_DISPLAY_NAME = INDEX_TAKEN_DATE + 1;
	public static final int INDEX_MIME_TYPE = INDEX_DISPLAY_NAME + 1;
	public static final int INDEX_DATA = INDEX_MIME_TYPE + 1;
	public static final int INDEX_FILE_SIZE = INDEX_DATA + 1;
	public static final int INDEX_DURATION = INDEX_FILE_SIZE + 1;
	public static final int INDEX_DATE_MODIFIED = INDEX_DURATION + 1;
	// no orientation column
	public static final int INDEX_ORIENTATION = INDEX_DATE_MODIFIED + 1;
	public static final int INDEX_WIDTH = INDEX_DATE_MODIFIED + 1;
	public static final int INDEX_HEIGHT = INDEX_WIDTH + 1;
	//public static final int INDEX_IS_DRM = 9;

	private static String[] THUMBNAIL_PROJECTION = new String[]{
			MediaStore.Video.Thumbnails.DATA,
			MediaStore.Video.Thumbnails.VIDEO_ID
	};

	private static final String[] VIDEO_PROJECTION = new String[]{
			MediaStore.Video.VideoColumns._ID,
			MediaStore.Video.VideoColumns.DATE_TAKEN,
			MediaStore.MediaColumns.DISPLAY_NAME,
			MediaStore.MediaColumns.MIME_TYPE,
			MediaStore.MediaColumns.DATA,
			MediaStore.MediaColumns.SIZE,
			/*Media.IS_DRM,*/
			MediaStore.Video.VideoColumns.DURATION,
			MediaStore.MediaColumns.DATE_MODIFIED,
			/*"orientation",*/
			MediaStore.Files.FileColumns.WIDTH,
			MediaStore.Files.FileColumns.HEIGHT

	};

	public static boolean isMediaMounted(final Context context) {
		boolean mounted = false;
		String defaultStoragePath = "";
		String defaultStorageState = "";
		final String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)
		    || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			mounted = true;
		} else {
			final StorageManager storageManager = (StorageManager) context
					.getSystemService(Context.STORAGE_SERVICE);
			if (storageManager != null) {
				// TODO
				// defaultStoragePath = StorageManagerEx.getDefaultPath();
				// defaultStorageState =
				// storageManager.getVolumeState(defaultStoragePath);
				if (Environment.MEDIA_MOUNTED.equals(defaultStorageState)
				    || Environment.MEDIA_MOUNTED_READ_ONLY
						    .equals(defaultStorageState)) {
					mounted = true;
				}
			}
		}
		VideoLog.v(TAG, "isMediaMounted() return " + mounted + ", state = " + state
		                + ", defaultStoragePath=" + defaultStoragePath
		                + ", defaultStorageState=" + defaultStorageState);
		return mounted;
	}

	public static boolean isMediaScanning(final Context context) {
		boolean result = false;
		final Cursor cursor = query(context, MediaStore.getMediaScannerUri(),
		                            new String[]{MediaStore.MEDIA_SCANNER_VOLUME}, null, null,
		                            null);
		if (cursor != null) {
			if (cursor.moveToFirst()) {
				final String scanVolumne = cursor.getString(0);
				result = "external".equals(scanVolumne);
				VideoLog.v(TAG, "isMediaScanning() scanVolumne=" + scanVolumne);
			}
			cursor.close();
		}
		VideoLog.d(TAG, "isMediaScanning() cursor = " + cursor + ", result = " + result);
		return result;
	}

	public static Cursor query(final Context context, final Uri uri,
	                           final String[] projection, final String selection,
	                           final String[] selectionArgs, final String sortOrder) {
		return query(context, uri, projection, selection, selectionArgs,
		             sortOrder, 0);
	}

	public static Cursor query(final Context context, Uri uri,
	                           final String[] projection, final String selection,
	                           final String[] selectionArgs, final String sortOrder,
	                           final int limit) {
		try {
			final ContentResolver resolver = context.getContentResolver();
			if (resolver == null) {
				return null;
			}
			if (limit > 0) {
				uri = uri.buildUpon().appendQueryParameter("limit", "" + limit).build();
			}
			return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
		} catch (final UnsupportedOperationException ex) {
			return null;
		}
	}

	/**
	 *
	 * @param context cotext
	 * @param uri uri for query
	 * @param limit limit used to pages
	 * @return entities
	 */
	public static ArrayList<VideoEntity> getVideosInfo(Context context, Uri uri, int limit) {
		//Uri uri = VIDEO_URI;
		Cursor cursor = null;
		ArrayList<VideoEntity> videoEntities = null;
		try {
			final ContentResolver resolver = context.getContentResolver();
			if (resolver == null) {
				return null;
			}
			if (limit > 0) {
				uri = uri.buildUpon().appendQueryParameter("limit", "" + limit).build();
			}
			cursor = resolver.query(uri, VIDEO_PROJECTION, null, null, ORDER_COLUMN, null);
			if (null == cursor) {
				return null;
			}
			VideoEntity videoEntity;

			if (cursor.moveToFirst()) {
				videoEntities = new ArrayList<>();
				do {
					videoEntity = new VideoEntity();
					setEntityFromCursor(videoEntity, cursor);
					VideoLog.v(TAG, "query a entity, name is: " + videoEntity.getName());
					videoEntities.add(videoEntity);
				} while (cursor.moveToNext());
			}
		} catch (final UnsupportedOperationException ex) {
			return null;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return videoEntities;
	}
	/**
	 * @param context context
	 * @param limit   page limit
	 * @return array list of contains all available video in sdCard.
	 */
	public static ArrayList<VideoEntity> getVideosInfo(Context context, int limit){
		return getVideosInfo(context, VIDEO_URI, limit);
	}

	public static VideoEntity getVideoInfo(Context context, Uri uri) {
		VideoEntity entity = null;
		ContentResolver cr;
		Cursor cursor = null;
		try {
			cr = context.getContentResolver();
			if (cr == null) {
				return null;
			}
			cursor = cr.query(uri, VIDEO_PROJECTION, null, null, ORDER_COLUMN, null);
			if (null == cursor) {
				return null;
			}
			if (cursor.moveToFirst()) {
				VideoLog.i(TAG, "when app is sleep, camera catch some photo, count is " + cursor.getCount());
				do {
					VideoLog.i(TAG, "when app is sleep, camera catch some photo,"
					                + "item name = " + cursor.getString(INDEX_DISPLAY_NAME));
					entity = new VideoEntity();
					setEntityFromCursor(entity, cursor);
				} while (cursor.moveToNext());
			}
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return entity;
	}

	private static void setEntityFromCursor(VideoEntity entity, Cursor cursor) {
		entity.setIdDb(cursor.getLong(INDEX_ID));
		entity.setTimeToken(cursor.getLong(INDEX_TAKEN_DATE));

		entity.setName(cursor.getString(INDEX_DISPLAY_NAME));
		entity.setMimeType(cursor.getString(INDEX_MIME_TYPE));
		entity.setData(cursor.getString(INDEX_DATA));
		entity.setSize(cursor.getLong(INDEX_FILE_SIZE));
		entity.setDuring(cursor.getLong(INDEX_DURATION));
		entity.setLastModify(cursor.getLong(INDEX_DATE_MODIFIED));
		/*entity.setOrientation(cursor.getInt(INDEX_ORIENTATION));*/
		entity.setWidth(cursor.getInt(INDEX_WIDTH));
		entity.setHeight(cursor.getInt(INDEX_HEIGHT));
	}
}
