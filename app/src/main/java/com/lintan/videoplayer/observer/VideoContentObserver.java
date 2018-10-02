package com.lintan.videoplayer.observer;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.lintan.videoplayer.activity.VideoCenterActivity;
import com.lintan.videoplayer.bean.VideoEntity;
import com.lintan.videoplayer.util.log.VideoLog;
import com.lintan.videoplayer.util.media.MediaAccess;

import java.util.ArrayList;

/**
 * a observer will see the video file is created.
 * Created by lintan on 9/14/16.
 */
public class VideoContentObserver extends ContentObserver {

	private static final String TAG = "VideoPlayer/VideoContentObserver";

	private static final Uri OBSERVER_URI = MediaAccess.VIDEO_URI;

	private Handler mHandler;

	private Context mContext;

	private static int updateTimes;

	public VideoContentObserver(Context context, Handler handler) {
		super(handler);
		mHandler = handler;
		mContext = context;
		updateTimes = 0;
	}


	@Override
	public void onChange(boolean selfChange) {
		VideoLog.w(TAG, "call onChange(), but going to onChange(selfChange, null)");
		onChange(selfChange, null);
	}

	@Override
	public void onChange(boolean selfChange, Uri uri) {
		VideoLog.v(TAG, "onChange(boolean selfChange, Uri uri) selfChange? " + selfChange + ", uri = :" + uri);
		if (null == uri) {
			//mHandler.sendEmptyMessage(VideoCenterActivity.MEDIA_STORE_VIDEO_EMPTY_MESSAGE);
			return;
		}
		String uriString = uri.toString();
		final String mediaString = "media";
		// if uri exists, it must be "content://media/external",
		// "content://media/external/video/media", or
		// "content://media/external/video/media/195".
		// so we split it by 'media' string.
		if (!uriString.contains(mediaString)) {
			return;
		}
		// because the uri contains id will show twice, we ignore the second uri.
		//if (array.length == secondMediaIndex && !mLastUri.equals(uri)) {
		if (OBSERVER_URI.equals(uri) && updateTimes < 2) {
			updateTimes++;
			// if a video file created, this method will be called twice, and the result will be same.
			// so we used a static int value.
			if (updateTimes == 2) {
				updateTimes = 0;
				return;
			}
			VideoLog.d(TAG, "going to query, uri = " + uri);
			//VideoEntity ve = MediaAccess.getVideoInfo(mContext, uri);
			ArrayList<VideoEntity> entities = MediaAccess.getVideosInfo(mContext, uri, 0);
			//Message msg = Message.obtain(mHandler, VideoCenterActivity.MEDIA_STORE_VIDEO_CREATE);
			Message msg = Message.obtain(mHandler, VideoCenterActivity.MEDIA_STORE_VIDEOS_CREATE);
			Bundle bundleData = new Bundle();
			//bundleData.putParcelable(VideoCenterActivity.MEDIA_STORE_VIDEO_CREATE_DATA, ve);
			bundleData.putParcelableArrayList(VideoCenterActivity.MEDIA_STORE_VIDEO_CREATE_DATA_S, entities);
			msg.setData(bundleData);
			mHandler.sendMessage(msg);
		} else {
			VideoLog.v(TAG, "this message will be ignore. uri is :" + uri);
		}
	}
}
