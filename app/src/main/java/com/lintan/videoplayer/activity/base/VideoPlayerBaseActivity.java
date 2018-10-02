package com.lintan.videoplayer.activity.base;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.lintan.videoplayer.R;
import com.lintan.videoplayer.activity.VideoPlayActivity;
import com.lintan.videoplayer.interf.PermissionHandleInterface;
import com.lintan.videoplayer.util.log.VideoLog;
import com.lintan.videoplayer.util.permission.MPermissionUtil;
import com.lintan.videoplayer.util.shared.SharedPreferenceUtil;

/**
 * abstract permission request.
 * <p/>
 * Created by Administrator on 2016/9/5.
 */
public class VideoPlayerBaseActivity extends AppCompatActivity implements PermissionHandleInterface {

	public static final String ENTITY_OBJ_TAG = "entity";
	public static final String ENTITY_OBJ_BUNDLE_TAG = "bundle";
	private static final String TAG = "VideoPlayer/VideoPlayerBaseActivity";

	/**
	 * permission
	 */
	public static final String FIRST_REQUESTED_DENY_REMEMBER_KEY = "isRemembered";
	private static final int M_STORAGE_PERMISSION_REQUEST = 0x0001;

	private static final String READ_STORAGE_PERMISSION = Manifest.permission.READ_EXTERNAL_STORAGE;
	private static final String WRITE_STORAGE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE;

	private long mLastBackKeyTimeClock = 0;
	private final int SPACE_TIME = 2000;

	public void showEmpty() {

	}

	public void showList() {

	}

	public void showCustomPermissionDialog() {

	}

	public void permissionGranted() {

	}

	protected void startActivity(Class<?> destActivity) {
		startActivity(destActivity, null);
	}

	/**
	 * it is used to translate object over a application.
	 *
	 * @param destActivity the target of intent.
	 * @param objEntity    you value entity.
	 */
	protected void startActivity(Class<?> destActivity, Parcelable objEntity) {
		startActivity(destActivity, objEntity, false);
	}

	/**
	 * it is used to translate object over a application.
	 *
	 * @param destActivity the target of intent.
	 * @param objEntity    you value entity.
	 * @param isNeedFinish true is you want to finish the activity who sent command, otherwise false.
	 */
	protected void startActivity(Class<?> destActivity, Parcelable objEntity, boolean isNeedFinish) {

		startActivity(destActivity, objEntity, isNeedFinish, this.getClass().getName());
	}

	protected void startActivity(Class<?> destActivity, Parcelable objEntity, boolean isNeedFinish, String from) {
		Intent intent = new Intent(this, destActivity);
		Bundle bundle = new Bundle();
		bundle.putParcelable(ENTITY_OBJ_TAG, objEntity);
		intent.putExtra(ENTITY_OBJ_BUNDLE_TAG, bundle);
		intent.putExtra(VideoPlayActivity.INTENT_FROM, from);
		startActivity(intent);
		if (isNeedFinish) {
			finish();
		}
	}

	protected void startService(Class<?> destService) {
		startService(destService, null);
	}

	protected void stopService(Class<?> destService) {
		Intent intentService = new Intent(getApplicationContext(), destService);
		stopService(intentService);
	}

	/**
	 * please to view startActivity().
	 *
	 * @param destService the target of intent.
	 * @param objEntity   you value entity.
	 */
	protected void startService(Class<?> destService, Parcelable objEntity) {
		startService(destService, objEntity, false);
	}

	/**
	 * please to view startActivity().
	 *
	 * @param destService  the target of intent.
	 * @param objEntity    you value entity.
	 * @param isNeedFinish true is you want to finish the activity who sent command, otherwise false.
	 */
	protected void startService(Class<?> destService, Parcelable objEntity, boolean isNeedFinish) {
		Intent intent = new Intent(getApplicationContext(), destService);
		Bundle bundle = new Bundle();
		bundle.putParcelable(ENTITY_OBJ_TAG, objEntity);
		stopService(intent);
		intent.putExtra(ENTITY_OBJ_BUNDLE_TAG, bundle);
		startService(intent);
		if (isNeedFinish) {
			finish();
		}
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@RequiresApi(api = Build.VERSION_CODES.M)
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
	                                       @NonNull int[] grantResults) {

		switch (requestCode) {
			case M_STORAGE_PERMISSION_REQUEST:
				VideoLog.d(TAG, "requestCode = " + requestCode + "");
				if (allPermissionIsGranted(grantResults)) {
					//new VideoListQueryTask(this).execute();
					SharedPreferenceUtil.remove(this, FIRST_REQUESTED_DENY_REMEMBER_KEY);
					permissionGranted();
				} else {

					// do not get permission
					VideoLog.i(TAG, "onRequestPermissionsResult(), permission is denied. " +
					                shouldShowRequestPermissionRationale(READ_STORAGE_PERMISSION));

					// show my dialog
					if (MPermissionUtil.isDeniedByRemember(this, permissions) &&
					    !(boolean) (SharedPreferenceUtil.get(this, FIRST_REQUESTED_DENY_REMEMBER_KEY, false))) {
						SharedPreferenceUtil.put(this, FIRST_REQUESTED_DENY_REMEMBER_KEY, true);
					} else if ((boolean) SharedPreferenceUtil.get(this, FIRST_REQUESTED_DENY_REMEMBER_KEY, false)) {
						showCustomPermissionDialog();
					} else {
						VideoLog.i(TAG, "it's common denied");
						showEmpty();
					}
				}
				break;
			default:
				break;
		}
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	private boolean allPermissionIsGranted(int[] grantResults) {
		for (int tmp : grantResults) {
			if (PackageManager.PERMISSION_GRANTED != tmp) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void onBackPressed() {
		long nowClock = System.currentTimeMillis();
		if (nowClock - mLastBackKeyTimeClock <= SPACE_TIME) {
			finish();
		} else {
			Toast.makeText(this, R.string.app_exit_confirm, Toast.LENGTH_SHORT).show();
			mLastBackKeyTimeClock = nowClock;
		}
	}
}
