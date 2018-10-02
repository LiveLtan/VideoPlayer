package com.lintan.videoplayer.util;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by lintan on 9/19/16.
 */
public class SelectManager {
	private static HashMap<String, Boolean> itemSelectMap = new HashMap<>();

	public static HashMap<String, Boolean> getItemSelectMap() {
		return itemSelectMap;
	}

	public static boolean isItemSelect(String filePath) {
		return itemSelectMap.containsKey(filePath) ? itemSelectMap.get(filePath) : false;
	}

	public static void setItemSelect(String filePath) {
		setItemSelect(filePath, true);
	}

	/**
	 * set item checked state.
	 * @param filePath filePath of the video.
	 * @param  checked checked state.
	 */
	public static void setItemSelect(String filePath, boolean checked) {
		if (itemSelectMap.containsKey(filePath)) {
			itemSelectMap.put(filePath, checked);
		} else {
			itemSelectMap.put(filePath, checked);
		}
	}

	/**
	 *
	 * @return item select count.
	 */
	public static int getSelectItemCount() {
		int count = 0;
		for (String strKey : itemSelectMap.keySet()) {
			if (itemSelectMap.get(strKey)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * to select all, the key is from you adapter
	 * @param keys the data path of videos.
	 */
	public static void setSelectAll(ArrayList<String> keys) {
		for (String key : keys) {
			itemSelectMap.put(key, true);
		}
	}

	/**
	 * cancel select all.
	 */
	public static void cancelSelectAll() {
		itemSelectMap.clear();
	}

	public static void removeKey(String key) {
		if (itemSelectMap.containsKey(key)) {
			itemSelectMap.remove(key);
		}
	}

	public static void invisibleCheckBox() {

	}
}

