package com.lintan.videoplayer.util.permission;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.RequiresApi;

import com.lintan.videoplayer.BuildConfig;
import com.lintan.videoplayer.util.log.VideoLog;

/**
 * Android M permission, dynamic apply must.
 * Created by lintan on 9/5/16.
 */
public class MPermissionUtil {

	public static boolean hasPermissionsAll(Activity activity, String... permissions) {
		if (!isNeedDynamicApp()) {
			return true;
		}
		for (String permission : permissions) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (PackageManager.PERMISSION_GRANTED != activity.checkSelfPermission(permission)) {
                    return false;
                }
			}
		}
		return true;
	}

	/*public static boolean havePermissionsAll(Activity activity, String[] permissions) {
		if (!isNeedDynamicApp()) {
			return true;
		}
		int length = permissions.length;
		hasPermissionsAll(activity, String)
		for (String permission : permissions) {
			if (PackageManager.PERMISSION_GRANTED != activity.checkSelfPermission(permission)) {
				return false;
			}
		}
		return true;
	}*/

	public static boolean isDeniedByRemember(Activity activity, String[] permissions) {
		for (String permission : permissions) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (!activity.shouldShowRequestPermissionRationale(permission)) {
                    VideoLog.d("VideoPlayer/MPermissionUtil", "permission is " + permission);
                    return true;
                }
			}
		}
		return false;
	}

	private static boolean isNeedDynamicApp() {
		return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
	}
}
