package com.lintan.videoplayer.interf;

/**
 * create this because some activity do not
 * want to implements all method as below.
 *
 * Created by Administrator on 2016/9/5.
 */
public interface PermissionHandleInterface {
	void showEmpty();
	void showList();
	void showCustomPermissionDialog();
	void permissionGranted();
}
