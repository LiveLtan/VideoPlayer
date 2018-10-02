package com.lintan.videoplayer.util;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.Application;
import android.content.Context;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

/**
 * common utils.
 *
 * Created by Administrator on 2016/9/3.
 */
public class CommonUtil {
	public static String stringForTime(final long millis) {
		final int totalSeconds = (int) millis / 1000;
		final int seconds = totalSeconds % 60;
		final int minutes = (totalSeconds / 60) % 60;
		final int hours = totalSeconds / 3600;
		if (hours > 0) {
			return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
		} else {
			return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
		}
	}

	private static Date sDate = new Date();// cause lots of CPU

	public static String localTime(final long millis) {
		sDate.setTime(millis);
		Calendar calendar = new GregorianCalendar(Locale.getDefault());
		calendar.setTimeInMillis(millis);
		return calendar.toString();
		//return sDate.toLocaleString();
	}

	/**
	 * check the service status by special name.
	 * @param application app
	 * @param serviceClassName special name.
	 * @return true if running.
	 */
	public static boolean isServiceRunning(Application application, String serviceClassName){
		final ActivityManager activityManager = (ActivityManager) application.getSystemService(
				Context.ACTIVITY_SERVICE);
		final List<RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);

		for (RunningServiceInfo runningServiceInfo : services) {
			if (runningServiceInfo.service.getClassName().equals(serviceClassName)){
				return true;
			}
		}
		return false;
	}
}
