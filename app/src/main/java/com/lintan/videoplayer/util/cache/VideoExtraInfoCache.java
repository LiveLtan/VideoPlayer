/******************************************************************************/
/*                                                             Date:12/24/2014*/
/*                                PRESENTATION                                */
/*                                                                            */
/*       Copyright 2013 TCL Communication Technology Holdings Limited.        */
/*                                                                            */
/* This material is company confidential, cannot be reproduced in any form    */
/* without the written permission of TCL Communication Technology Holdings    */
/* Limited.                                                                   */
/*                                                                            */
/* -------------------------------------------------------------------------- */
/* Author :  tao.li                                                           */
/* Email  :  tao.li@tcl.com                                                   */
/* Role   :  VideoPlay                                                        */
/* Reference documents :                                                      */
/* -------------------------------------------------------------------------- */
/* Comments :                                                                 */
/* File     :../src/com/tct/videoplayer/VideoExtraInfoCache.java                  */
/* Labels   :                                                                 */
/* -------------------------------------------------------------------------- */
/* ========================================================================== */
/*     Modifications on Features list / Changes Request / Problems Report     */
/* ----------|----------------------|----------------------|----------------- */
/*    date   |        Author        |         Key          |     comment      */
/* ----------|----------------------|----------------------|----------------- */
/*           |                      |                      |                  */
/* ----------|----------------------|----------------------|----------------- */
/* ----------|----------------------|----------------------|----------------- */
/******************************************************************************/

package com.lintan.videoplayer.util.cache;

import android.content.Context;
import android.text.format.Formatter;

import com.lintan.videoplayer.util.CommonUtil;
import com.lintan.videoplayer.util.log.VideoLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * copied from tct video player.
 *
 * @author lintan on 3/09/16
 */
public class VideoExtraInfoCache {
	private static final String TAG = "VideoPlayer/VideoExtraInfoCache";
	private static final String COLON = ":";
	private static final String ZERO = "0";
	private static final int THOUSAND = 1000;
	private static final int ONE_MINUTE = 60;
	private static final int ONE_HOUR = 3600;
	private static final int TEN = 10;
	
	private Locale mLocale;
	private final HashMap<Long, String> mDurations = new HashMap<Long, String>();
	private final HashMap<Long, String> mDateTimes = new HashMap<Long, String>();
	private final HashMap<Long, String> mFileSizes = new HashMap<Long, String>();
	private boolean mCanOptimized;
	private final ArrayList<Locale> mCanOptimizedLocales = new ArrayList<Locale>();
	
	public VideoExtraInfoCache() {
		mCanOptimizedLocales.add(Locale.ENGLISH);
		mCanOptimizedLocales.add(Locale.CHINA);
		mCanOptimizedLocales.add(Locale.TAIWAN);
		mCanOptimizedLocales.add(Locale.UK);
		mCanOptimizedLocales.add(Locale.US);
		mCanOptimizedLocales.add(Locale.FRANCE);
		mCanOptimizedLocales.add(Locale.GERMANY);
		mCanOptimizedLocales.add(Locale.ITALY);
		setLocale(Locale.getDefault());
	}
	
	public synchronized void setLocale(final Locale locale) {
		VideoLog.v(TAG, "setLocale(" + locale + ") mLocale=" + mLocale + ", mCanOpitmized=" + mCanOptimized);
		if (locale == null) {
			mDateTimes.clear();
			mDurations.clear();
			mFileSizes.clear();
		} else {
			if (!locale.equals(mLocale)) {
				mLocale = locale;
				mDateTimes.clear();
				mFileSizes.clear();
				boolean newOptimized = false;
				if (mCanOptimizedLocales.contains(mLocale)) {
					newOptimized = true;
				}
				if (!mCanOptimized || !newOptimized) {
					mCanOptimized = newOptimized;
					mDurations.clear();
				}
			}
		}//optimization
		VideoLog.v(TAG, "setLocale() mCanOptimized = " + mCanOptimized);
	}
	
	public synchronized String getFileSize(final Context context, final Long size) {
		String fileSize = mFileSizes.get(size);
		if (fileSize == null) {
			fileSize = Formatter.formatFileSize(context, size);
			mFileSizes.put(size, fileSize);
		}
		return fileSize;
	}
	
	public synchronized String getTime(final Long millis) {
		String time = mDateTimes.get(millis);
		if (time == null) {
			time = CommonUtil.localTime(millis);
			mDateTimes.put(millis, time);
		}
		return time;
	}

	public synchronized String getDuration(final Long millis) {
		String duration = mDurations.get(millis);
		if (duration == null) {
			if (mCanOptimized) {
				duration = stringForDurationOptimized(millis);
			} else {
				duration = CommonUtil.stringForTime(millis);
			}
			mDurations.put(millis, duration);
		}
		return duration;
	}
	
	private String stringForDurationOptimized(final long millis) {
		final int totalSeconds = (int) millis / THOUSAND;
		final int seconds = totalSeconds % ONE_MINUTE;
		final int minutes = (totalSeconds / ONE_MINUTE) % ONE_MINUTE;
		final int hours = totalSeconds / ONE_HOUR;
		// optimize format time, but not support special language
		final StringBuilder builder = new StringBuilder(10);
		if (hours > 0) {
			builder.append(hours).append(COLON);
		}
		if (minutes < TEN) {
			builder.append(ZERO);
		}
		builder.append(minutes);
		builder.append(COLON);
		if (seconds < TEN) {
			builder.append(ZERO);
		}
		builder.append(seconds);
		return builder.toString();
	}
}
