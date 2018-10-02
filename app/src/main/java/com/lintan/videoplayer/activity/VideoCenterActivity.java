package com.lintan.videoplayer.activity;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.lintan.videoplayer.R;
import com.lintan.videoplayer.activity.base.VideoPlayerBaseActivity;
import com.lintan.videoplayer.adapter.ListImageAdapter;
import com.lintan.videoplayer.bean.VideoEntity;
import com.lintan.videoplayer.observer.VideoContentObserver;
import com.lintan.videoplayer.service.FloatingWindowService;
import com.lintan.videoplayer.util.CommonUtil;
import com.lintan.videoplayer.util.SelectManager;
import com.lintan.videoplayer.util.cache.ImageDrawableCache;
import com.lintan.videoplayer.util.log.VideoLog;
import com.lintan.videoplayer.util.media.MediaAccess;
import com.lintan.videoplayer.util.media.MediaFile;
import com.lintan.videoplayer.util.permission.MPermissionUtil;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class VideoCenterActivity extends VideoPlayerBaseActivity implements View.OnClickListener,
                                                                            AbsListView
		                                                                            .OnItemClickListener,
                                                                            AbsListView
		                                                                            .OnItemLongClickListener {
	private static final String TAG = "VideoPlayer/VideoCenterActivity";

	// handler what declare
	public static final int MEDIA_STORE_VIDEO_CREATE = 0x0010;
	public static final int MEDIA_STORE_VIDEOS_CREATE = 0x0011;
	public static final int MEDIA_STORE_VIDEO_EMPTY_MESSAGE = 0x0012;
	public static final String MEDIA_STORE_VIDEO_CREATE_DATA = "video_entity";
	public static final String MEDIA_STORE_VIDEO_CREATE_DATA_S = "video_entities";

	private static final int M_STORAGE_PERMISSION_REQUEST = 0x0001;
	//private static final int M_OTHER_PERMISSION_REQUEST = 0x0002;
	private static final String VIDEO_FILE_URI_PREFIX = "file://";
	private static final String VIDEO_FILE_SHARE_TYPE = "video/*";
	private static final String VIDEO_FILE_SHARE_TO = "Share To";
	private static final String PACKAGE_URI_PREFIX = "package:";
	private static final String READ_STORAGE_PERMISSION = Manifest.permission.READ_EXTERNAL_STORAGE;
	private static final String WRITE_STORAGE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE;
	private static final String[] VIDEO_PLAYER_NEED_PERMISSIONS = new String[]{
			READ_STORAGE_PERMISSION,
			WRITE_STORAGE_PERMISSION};

	public static final int LIST_VIEW_NORMAL = 0;
	public static final int SELECT_TO_DELETE = 1;
	public static final int ALL_SELECTED = 2;
	public static final int ALL_SELECT_CANCELED = 3;
	public static final int LIST_VIEW_DELETING = 4;
	private static int mMenuMode;
	private ProgressDialog mProgressDialog;
	private StorageListener mStorageListener;

	private AlertDialog dialog;
	private ProgressBar deleteProgressBar;
	private MenuItem mSelectMenuItem;

	private LinearLayout mSelectBottomLinear;
	private Button mSelectAllButton;
	private Button mDeleteSelectButton;
	//private int deleteCount = 0;

	private ListView mListView;
	private LinearLayout mEmptyVideoLinearLay;
	private ListImageAdapter mListImageAdapter;
	private VideoContentObserver mVideoContentObserver;

	private static final int DELETE_SINGLE_ITEM = 0x0001;
	private static final int DELETE_ALL_DONE = 0x0002;
	private static final int DELETE_SINGLE_ITEM_ADAPTER = 0x0003;

	private VideoListHandler mVideoListHandler;
	private VideoListQueryTask mVideoListQueryTask;

	public static int getmMenuMode() {
		return mMenuMode;
	}

	private static class VideoListHandler extends Handler {
		WeakReference<VideoCenterActivity> activityReference;

		public VideoListHandler(VideoCenterActivity activity) {
			this.activityReference = new WeakReference<>(activity);
		}

		@Override
		public void handleMessage(Message msg) {
			activityReference.get().showList();
			VideoCenterActivity activity = activityReference.get();
			// we fond that activity is not null.
			if (null == activity) {
				return;
			}
			Bundle data = msg.getData();
			switch (msg.what) {
				case MEDIA_STORE_VIDEO_CREATE:
					VideoEntity vEntity = data.getParcelable(MEDIA_STORE_VIDEO_CREATE_DATA);
					videoCreated(activity, vEntity);
					break;
				case MEDIA_STORE_VIDEOS_CREATE:
					ArrayList<VideoEntity> entities = data.getParcelableArrayList(MEDIA_STORE_VIDEO_CREATE_DATA_S);
					videosCreate(activity, entities);
					break;
				case MEDIA_STORE_VIDEO_EMPTY_MESSAGE:
					//videoCreated(activity, null);
					break;
				case DELETE_SINGLE_ITEM:
					videoItemDeleted(activity, msg.arg1);
					break;
				case DELETE_SINGLE_ITEM_ADAPTER:

					break;
				case DELETE_ALL_DONE:
					videoItemAllDeleted(activity);
					break;
				default:
					break;
			}
			super.handleMessage(msg);
		}

		/**
		 * a video is add by system.
		 *
		 * @param activity video center activity
		 * @param ve       entity
		 */
		private void videoCreated(VideoCenterActivity activity, VideoEntity ve) {
			VideoLog.d(TAG, "adapter.getEntityList().add to first is completed ---- ");
			if (null == ve) {
				activity.showEmpty();
				return;
			}
			if (mMenuMode == ALL_SELECTED) {
				SelectManager.setItemSelect(ve.getData());
			} else if (mMenuMode == SELECT_TO_DELETE) {
				VideoLog.e(TAG, "mode is error" + mMenuMode);
			} else {
				mMenuMode = VideoCenterActivity.LIST_VIEW_NORMAL;
			}
			activity.mListImageAdapter.getEntityList().add(0, ve);
			activity.mListImageAdapter.notifyDataSetChanged();
			activity.mSelectMenuItem.setVisible(true);
			activity.mListView.setEnabled(true);
		}

		private void videosCreate(VideoCenterActivity activity, ArrayList<VideoEntity> entities) {
			if (null == entities || entities.size() == 0) {
				activity.showEmpty();
				return;
			}
			VideoLog.i(TAG, "videosCreate------ query list = " + entities.size());
			ListImageAdapter adapter = activity.mListImageAdapter;
			if (null == adapter.getEntityList() || adapter.getEntityList().size() == 0) {
				adapter.setEntityList(entities);
			} else {
				adapter.setEntityList(entities);
			}
			VideoLog.i(TAG, "videosCreate------ adapter list = " + adapter.getEntityList().size());
			if (mMenuMode == ALL_SELECTED) {
				VideoLog.d(TAG, "mMenuMode: mMenuMode == ALL_SELECTED");
			} else if (mMenuMode == SELECT_TO_DELETE) {
				VideoLog.d(TAG, "mMenuMode: mMenuMode == SELECT_TO_DELETE");
			} else {
				mMenuMode = VideoCenterActivity.LIST_VIEW_NORMAL;
			}
			adapter.notifyDataSetChanged();
			activity.mSelectMenuItem.setVisible(true);
			activity.mListView.setEnabled(true);
		}

		/**
		 * a video is deleted.
		 *
		 * @param activity video center activity
		 */
		private void videoItemDeleted(VideoCenterActivity activity, int arg1) {

			activity.mListImageAdapter.notifyDataSetChanged();
			//activity.deleteCount++;
			VideoLog.e(TAG,
			           "handler receive a message DELETE_SINGLE_ITEM, " + "DELETE ITEM NUMBER = " + arg1);
			activity.hideBottomMenu();
			activity.hideRightCheckBox();
			mMenuMode = VideoCenterActivity.LIST_VIEW_DELETING;
			if (null != activity.deleteProgressBar) {
				activity.deleteProgressBar.setProgress(arg1);
			}
		}

		/**
		 * all the video item which was selected.
		 *
		 * @param activity video center activity
		 */
		private void videoItemAllDeleted(VideoCenterActivity activity) {
			VideoLog.e(TAG, "handler handle receive a message DELETE_ALL_DONE");
			mMenuMode = VideoCenterActivity.LIST_VIEW_NORMAL;
			activity.mListImageAdapter.notifyDataSetChanged();
			if (null != activity.dialog) {
				activity.dialog.dismiss();
			}
			if (activity.mListImageAdapter == null
			    || activity.mListImageAdapter.getCount() == 0) {
				activity.showEmpty();
				VideoLog.d(TAG, "done videoItemAllDeleted show Empty is called.");
				activity.mSelectMenuItem.setVisible(false);
			} else {
				activity.mSelectMenuItem.setVisible(true);
				activity.mListView.setEnabled(true);
			}
			activity.hideBottomMenu();
		}
	}


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_video_center);
		initView();
		//  to set some listener when app have permission.
		if (checkPermission()) {
			VideoLog.i(TAG, "has permission on onCreate");
			prepareShowList();
		} else {
			VideoLog.i(TAG, "has no permission on onCreate, show empty!");
			showEmpty();
		}
	}

	private void initView() {
		mListView = (ListView) findViewById(R.id.video_center_lv);
		mEmptyVideoLinearLay = (LinearLayout) findViewById(R.id.video_center_empty_ll);

		// add delete all
		mSelectBottomLinear = (LinearLayout) findViewById(R.id.video_center_select_ll);
		mSelectAllButton = (Button) findViewById(R.id.video_center_select_ll_select_all);
		mDeleteSelectButton = (Button) findViewById(R.id.video_center_select_ll_delete_select);
	}

	private boolean checkPermission() {
		boolean hasPermission;
		if (MPermissionUtil.hasPermissionsAll(this, VIDEO_PLAYER_NEED_PERMISSIONS)) {
			mVideoListQueryTask = new VideoListQueryTask(this);
			mVideoListQueryTask.execute();
			hasPermission = true;
		} else {
			//requestPermissions(VIDEO_PLAYER_NEED_PERMISSIONS, M_STORAGE_PERMISSION_REQUEST);
			VideoLog.d(TAG, "activityReference on Start---, have no permission, to request");
			requestPermission();
			hasPermission = false;
		}
		return hasPermission;
	}

	private void prepareShowList() {
		initListener();
		initContentObserver();
		registerStorageListener();
		mMenuMode = LIST_VIEW_NORMAL;
		new VideoListQueryTask(this).execute();
	}

	private void initListener() {
		mListView.setOnItemClickListener(this);
		mListView.setOnItemLongClickListener(this);
		//mStorageListener = new StorageListener();

		mSelectAllButton.setOnClickListener(this);
		mDeleteSelectButton.setOnClickListener(this);
	}

	private void initContentObserver() {
		mVideoListHandler = new VideoListHandler(this);
		mVideoContentObserver = new VideoContentObserver(this, mVideoListHandler);
		registerContentObserver(mVideoContentObserver);
	}

	private void registerContentObserver(VideoContentObserver observer) {
		if (null == observer) {
			throw new IllegalArgumentException("observer is null, couldn't be registered");
		}
		getContentResolver().registerContentObserver(MediaAccess.VIDEO_URI, true, observer);
		VideoLog.i(TAG, "VideoContentObserver has been registered");
	}

	private void unRegisterContentObserver(VideoContentObserver observer) {
		if (null == observer) {
			return;
		}
		getContentResolver().unregisterContentObserver(observer);
	}

	private void registerStorageListener() {
		VideoLog.d(TAG, "registerStorageListener----");
		final IntentFilter iFilter = new IntentFilter();
		if (null == mStorageListener) {
			mStorageListener = new StorageListener();
		}
		iFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
		iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
		iFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
		iFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
		iFilter.addDataScheme("file");
		registerReceiver(mStorageListener, iFilter);
	}

	private void unRegisterStorageListener() {
		VideoLog.d(TAG, "unRegisterStorageListener----");
		if(mStorageListener != null) {
			unregisterReceiver(mStorageListener);
		}
	}

	private void requestPermission() {
		if (!MPermissionUtil.hasPermissionsAll(this, VIDEO_PLAYER_NEED_PERMISSIONS)) {
			showEmpty();
			requestPermissions(VIDEO_PLAYER_NEED_PERMISSIONS, M_STORAGE_PERMISSION_REQUEST);
		}
	}

	/**
	 * may some time, some body to close the permission in setting window.
	 * we must check it again.
	 */
	private void someOneClosePermission() {
		if (!MPermissionUtil.hasPermissionsAll(this, VIDEO_PLAYER_NEED_PERMISSIONS)) {
			showEmpty();
		} else {
			if (null == mListImageAdapter) {
				prepareShowList();
			} else {
				mListImageAdapter.notifyDataSetChanged();
			}
			VideoLog.d(TAG, "has permission on start" + mListImageAdapter);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		// now we
		someOneClosePermission();
	}

	@Override
	protected void onResume() {
		super.onResume();
		//someOneClosePermission();
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (null != mVideoListQueryTask) {
			mVideoListQueryTask = null;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unRegisterStorageListener();
		unRegisterContentObserver(mVideoContentObserver);
		VideoLog.i(TAG, "VideoContentObserver has been unregistered");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		VideoLog.v(TAG, "onCreateOptionsMenu- called");
		getMenuInflater().inflate(R.menu.app_info, menu);
		mSelectMenuItem = menu.findItem(R.id.app_select_item);
		if (mListImageAdapter == null || mListImageAdapter.getEntityList().size() == 0) {
			mSelectMenuItem.setVisible(false);
		} else if (mListImageAdapter.getEntityList().size() > 0) {
			mSelectMenuItem.setVisible(true);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		VideoLog.v(TAG, "onCreateOptionsMenu- called item select");
		switch (item.getItemId()) {
			case R.id.app_info:
				startActivity(AppInfoActivity.class, null);
				break;
			case R.id.app_select_item:
				mMenuMode = SELECT_TO_DELETE;
				changeUiByMode();
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void showList() {
		mEmptyVideoLinearLay.setVisibility(View.GONE);
		mListView.setVisibility(View.VISIBLE);
	}

	@Override
	public void showEmpty() {
		mListView.setVisibility(View.GONE);
		mEmptyVideoLinearLay.setVisibility(View.VISIBLE);
	}

	@Override
	public void permissionGranted() {
		prepareShowList();
	}

	@Override
	public void showCustomPermissionDialog() {
		final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

		dialogBuilder.setTitle(R.string.app_access_storage);
		dialogBuilder.setMessage(R.string.app_access_storage_extra_info);
		dialogBuilder.setIcon(R.mipmap.video_player_launcher);
		dialogBuilder.setPositiveButton(R.string.app_menu_ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				Intent setting = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri
						.parse(PACKAGE_URI_PREFIX + getPackageName()));
				startActivity(setting);
			}
		});
		dialogBuilder.setNegativeButton(R.string.app_menu_no, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				showEmpty();
				dialog.dismiss();
			}
		});
		AlertDialog dialog = dialogBuilder.create();
		dialog.show();
	}

	@Override
	public void onBackPressed() {
		switch (mMenuMode) {
			case SELECT_TO_DELETE:
			case ALL_SELECT_CANCELED:
				mMenuMode = LIST_VIEW_NORMAL;
				changeUiByMode();
				return;
			case ALL_SELECTED:
				mMenuMode = ALL_SELECT_CANCELED;
				changeUiByMode();
				break;
			default:
				super.onBackPressed();
				break;
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.video_center_select_ll_select_all:
				if (mMenuMode == SELECT_TO_DELETE || mMenuMode == ALL_SELECT_CANCELED) {
					mMenuMode = ALL_SELECTED;
				} else if (mMenuMode == ALL_SELECTED) {
					mMenuMode = ALL_SELECT_CANCELED;
				} else if (mMenuMode == LIST_VIEW_NORMAL) {
					mMenuMode = ALL_SELECTED;
				}
				changeUiByMode();
				break;
			case R.id.video_center_select_ll_delete_select:
				deleteSelectItem();
				break;
			default:
				break;
		}
	}

	// add delete multi
	private void changeUiByMode() {
		if (null == mListImageAdapter || mListImageAdapter.getCount() < 1) {
			return;
		}
		VideoLog.v(TAG, "video center menu mode = " + mMenuMode);
		switch (mMenuMode) {
			case SELECT_TO_DELETE:
				showBottomMenu();
				mSelectMenuItem.setVisible(false);
				break;
			case ALL_SELECTED:
				selectAll();
				break;
			case ALL_SELECT_CANCELED:
				cancelSelectAll();
				break;
			case LIST_VIEW_NORMAL:
				cancelSelectAll();
				hideBottomMenu();
				hideRightCheckBox();
				mSelectMenuItem.setVisible(true);
				break;
			default:
				break;
		}
		mListImageAdapter.notifyDataSetChanged();
	}

	private void selectAll() {
		ArrayList<VideoEntity> entities = mListImageAdapter.getEntityList();
		ArrayList<String> keys = new ArrayList<>();
		for (VideoEntity entity : entities) {
			keys.add(entity.getData());
		}
		SelectManager.setSelectAll(keys);
	}

	private void cancelSelectAll() {
		SelectManager.cancelSelectAll();
	}

	private void showBottomMenu() {
		mSelectBottomLinear.setVisibility(View.VISIBLE);
	}

	private void hideBottomMenu() {
		mSelectBottomLinear.setVisibility(View.GONE);
	}

	private void hideRightCheckBox() {
		mListImageAdapter.notifyDataSetChanged();
	}
	//add end.

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		VideoLog.d(TAG, "onItemClick(), position = " + position + " id =" + id);
		if (null == mListImageAdapter) {
			return;
		}
		VideoEntity entity = (VideoEntity) mListImageAdapter.getItem(position);
		if (mMenuMode == ALL_SELECTED || mMenuMode == ALL_SELECT_CANCELED) {
			mMenuMode = SELECT_TO_DELETE;
		}
		if (mMenuMode == SELECT_TO_DELETE) {
			boolean check = SelectManager.isItemSelect(entity.getData());
			VideoLog.i(TAG, "onItemClick check state = " + check + " mMenuMode = " + mMenuMode);
			SelectManager.setItemSelect(entity.getData(), !check);
			changeUiByMode();
			mListImageAdapter.notifyDataSetChanged();
		} else if (mMenuMode == LIST_VIEW_NORMAL) {
			if (null == entity) {
				return;
			}
			if (CommonUtil.isServiceRunning(getApplication(), FloatingWindowService.class.getName())) {
				stopService(FloatingWindowService.class);
			}
			entity.setPlaying(true);
			startActivity(VideoPlayActivity.class, entity);
		}
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
		VideoLog.d(TAG, "onItemLongClick(), position = " + position + " id =" + id);
		//VideoEntity selectVideo = (VideoEntity) mListListImageSimpleAdapter.getItem(position);
		VideoEntity selectVideo = (VideoEntity) mListImageAdapter.getItem(position);
		showDeleteConfirmDialog(selectVideo.getData());
		// true to be forbid system trigger click event.
		return true;
	}

	/**
	 * if done this we will delete the file selected,
	 * show a dialog to message user.
	 */
	private void showDeleteConfirmDialog(final String filePath) {
		AlertDialog.Builder deleteBuilder = new AlertDialog.Builder(this);
		deleteBuilder.setIcon(R.mipmap.video_player_launcher);
		deleteBuilder.setMessage(R.string.app_list_item_delete_share_info);
		deleteBuilder.setPositiveButton(R.string.app_menu_delete, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				// disable list view
				mListView.setEnabled(false);
				File file = new File(filePath);
				if (file.exists() && file.delete()) {
					notifyMediaRemove(filePath);
					if (removeFromListView(filePath) != 0) {
						mVideoListHandler.sendEmptyMessage(DELETE_ALL_DONE);
						VideoLog.d(TAG, "in long click, we deleted a file, path is: " + filePath);
					} else {
						VideoLog.w(TAG, "in long click, we deleted a file, path is: " + filePath);
					}
				} else {
					Toast.makeText(VideoCenterActivity.this, R.string.app_delete_failure, Toast.LENGTH_SHORT).show();
				}
				// disable list view
				mListView.setEnabled(true);
			}
		});
		deleteBuilder.setNeutralButton(R.string.app_menu_share, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				shareSingleVideo(filePath);
			}
		});

		deleteBuilder.setNegativeButton(R.string.app_menu_cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		deleteBuilder.show();
	}

	/**
	 * delete the selected item.
	 */
	private void deleteSelectItem() {
		if (SelectManager.getSelectItemCount() < 1) {
			Toast.makeText(this, R.string.app_select_nothing, Toast.LENGTH_SHORT).show();
		} else {
			showDeleteConfirmDialog();
		}
	}

	/**
	 * to message user to confirm delete file.
	 */
	private void showDeleteConfirmDialog() {
		AlertDialog dialog;
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setIcon(R.mipmap.video_player_launcher);
		builder.setTitle(R.string.app_select_delete_title);
		builder.setMessage(R.string.app_select_delete_confirm);
		builder.setPositiveButton(R.string.app_menu_ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// to disable mListView.
				mListView.setEnabled(false);
				showDialogWhileDeleting();
				new VideoMultiDeleteTask().execute();
				dialog.dismiss();
			}
		});
		builder.setNegativeButton(R.string.app_menu_cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				mListView.setEnabled(true);
				dialog.dismiss();
			}
		});
		dialog = builder.create();
		dialog.show();
	}

	/**
	 * while deleting, wo don't allow user do something such as scroll, click.
	 */
	private void showDialogWhileDeleting() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		View view = getLayoutInflater().inflate(R.layout.app_dialog_delete_lay, null);
		deleteProgressBar = (ProgressBar) view.findViewById(R.id.app_dialog_delete_progress);
		builder.setView(view);
		builder.setIcon(R.mipmap.video_player_launcher);
		dialog = builder.create();
		dialog.setCancelable(false);
		dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
			@Override
			public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
				return keyCode == KeyEvent.KEYCODE_SEARCH;
			}
		});
		deleteProgressBar.setMax(SelectManager.getSelectItemCount());
		deleteProgressBar.setProgress(0);
		dialog.show();
	}

	/**
	 * flush cache by special key(video path).
	 *
	 * @param key key of bitmap drawable.
	 */
	private void removeFromCache(String key) {
		ImageDrawableCache.getInstance(this).removeFromMemoryCache(key);
	}

	/**
	 * share the video to another app.
	 */
	public void shareSingleVideo(String imagePath) {
		Uri imageUri = Uri.parse(VIDEO_FILE_URI_PREFIX + imagePath);
		VideoLog.d(TAG, "shareSingleVideo, uri: " + imageUri);

		Intent shareIntent = new Intent();
		shareIntent.setAction(Intent.ACTION_SEND);
		shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
		shareIntent.setType(VIDEO_FILE_SHARE_TYPE);
		startActivity(Intent.createChooser(shareIntent, VIDEO_FILE_SHARE_TO));
	}

	/**
	 * a task to query video list from database.
	 */
	class VideoListQueryTask extends AsyncTask<String, Void, ArrayList<VideoEntity>> {
		Context mContext;

		public VideoListQueryTask(Context context) {
			super();
			mContext = context;
		}

		@Override
		protected ArrayList<VideoEntity> doInBackground(String... params) {
			return MediaAccess.getVideosInfo(mContext, 0);
		}

		@Override
		protected void onPostExecute(ArrayList<VideoEntity> videoEntities) {
			if (null == videoEntities || 0 == videoEntities.size()) {
				showEmpty();
				if (null == mListImageAdapter) {
					mListImageAdapter = new ListImageAdapter(mContext, mListView, new ArrayList<VideoEntity>());
					mListView.setAdapter(mListImageAdapter);
				}
				return;
			}
			showList();
			if (null == mListImageAdapter) {
				mListImageAdapter = new ListImageAdapter(mContext, mListView, videoEntities);
				mListView.setAdapter(mListImageAdapter);
			} else {
				mListImageAdapter.setEntityList(videoEntities);
				mListImageAdapter.notifyDataSetChanged();
			}
			if (null != mSelectMenuItem) {
				mSelectMenuItem.setVisible(true);
			}
			super.onPostExecute(videoEntities);
		}
	}

	/**
	 * a task to delete the user select video.
	 */
	class VideoMultiDeleteTask extends AsyncTask<String, String, Boolean> {
		public VideoMultiDeleteTask() {
			super();
		}

		int needDeletedCount;
		int deleteCount = 0;

		@Override
		protected Boolean doInBackground(String... params) {
			needDeletedCount = SelectManager.getItemSelectMap().size();
			for (String filePath : SelectManager.getItemSelectMap().keySet()) {
				// some time the item click twice, if that, map will save the item key, so, we must check it again.
				if (!SelectManager.isItemSelect(filePath)) {
					VideoLog.i(TAG, "doInBackground get a key, but it's not checked, continue");
					continue;
				}
				boolean deleted = deleteSingleFromMedia(filePath);
				if (!deleted) {
					VideoLog.w(TAG, "VideoMultiDeleteTask delete file failure, the file path is " + filePath);
				} else {
					deleteCount++;
					publishProgress(filePath);
					VideoLog.i(TAG, "VideoMultiDeleteTask delete file succeed, the file path is " + filePath);
				}
			}
			SelectManager.getItemSelectMap().clear();
			deleteCount = 0;
			mVideoListHandler.sendEmptyMessage(DELETE_ALL_DONE);
			VideoLog.i(TAG, "VideoMultiDeleteTask delete file succeed, send a done message");
			return true;
		}

		@Override
		protected void onProgressUpdate(String... values) {
			VideoLog.d(TAG, "onProgressUpdate ------ value - " + values[0] + " ?? NEED COUNT = " + needDeletedCount);
			String filePath = values[0];
			//mVideoListHandler.sendMessage(msg);
			if (removeFromListView(filePath) < 1) {
				VideoLog.e(TAG, "remove from list view error, file path is: " + filePath);
			}
			mListImageAdapter.notifyDataSetChanged();
			VideoLog.d(TAG, "onProgressUpdate DELETE_SINGLE_ITEM, DELETE ITEM NUMBER --------- " + deleteCount);
			hideBottomMenu();
			hideRightCheckBox();
			mMenuMode = VideoCenterActivity.LIST_VIEW_DELETING;
			if (null != deleteProgressBar) {
				deleteProgressBar.setProgress(deleteCount);
			}
		}

		@Override
		protected void onPostExecute(Boolean aBoolean) {
			if (!aBoolean) {
				Toast.makeText(VideoCenterActivity.this, R.string.app_delete_failure, Toast.LENGTH_SHORT).show();
			}
		}
	}

	/**
	 * delete a file by special key.
	 * <p/>
	 * first we will delete it in sd card, if succeed, update the media store, if succeed,
	 * remove the key of cache after while.
	 *
	 * @param filePath what's file will be delete.
	 * @return true if the file has been deleted.
	 */
	private boolean deleteSingleFromMedia(String filePath) {
		boolean deleted = true;
		File file = new File(filePath);
		if (file.exists() && file.delete()) {
			if (notifyMediaRemove(filePath) < 1) {
				VideoLog.e(TAG, "remove from database error, file path is: " + filePath);
				deleted = false;
			}
		} else {
			VideoLog.i(TAG, "remove from list view error, in deleteSingleFromMedia please check this file path is: " +
			                filePath + "is exist!");
			deleted = false;
		}
		return deleted;
	}

	/**
	 * to update the database to delete the file item.
	 *
	 * @param filePath file path.
	 */
	private int notifyMediaRemove(String filePath) {
		String where;
		int rows = 0;
		boolean isVideoFile = MediaFile.isVideoFileType(filePath);
		if (isVideoFile) {
			where = MediaStore.Video.Media.DATA + "='" + filePath + "'";
			rows = getContentResolver().delete(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, where, null);
		}
		return rows;
	}

	/**
	 * remove item from index list view.
	 *
	 * @param position the index of list view selected.
	 */
	private int removeFromListView(int position) {
		if (null == mListImageAdapter || (position > mListImageAdapter.getCount())) {
			return 0;
		}
		ArrayList<VideoEntity> list = mListImageAdapter.getEntityList();
		return removeFromListView(list.get(position).getData());
	}

	/**
	 * remove item by special key of list view.
	 *
	 * @param path the video(file) path of list view selected.
	 */
	private int removeFromListView(String path) {
		if (null == mListImageAdapter) {
			return 0;
		}
		ArrayList<VideoEntity> list = mListImageAdapter.getEntityList();
		//VideoLog.e(TAG, "before delete entities size = " + list.size());
		removeFromCache(path);
		for (VideoEntity tmp : list) {
			if (tmp.getData().equals(path)) {
				if (list.remove(tmp)) {
					break;
				} else {
					VideoLog.e(TAG, "entity list remove failed, please check it, path is: " + tmp.getData());
					return 0;
				}
			}
		}
		VideoLog.e(TAG, "after delete entities size = " + list.size());
		return 1;
	}

	class StorageListener extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			VideoLog.d(TAG, "receive broad permission, action is: " + intent.getAction());
			final String action = intent.getAction();
			if (Intent.ACTION_MEDIA_SCANNER_STARTED.equals(action)) {
				refreshSdStatus(MediaAccess.isMediaMounted(VideoCenterActivity.this));
			} else if (Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)) {
				refreshSdStatus(MediaAccess.isMediaMounted(VideoCenterActivity.this));
			} else if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action) || Intent.ACTION_MEDIA_EJECT.equals(action)) {
				VideoLog.w(TAG, "user's sd card is missed, please check it");
			}
		}
	}
	
	private void refreshSdStatus(final boolean mounted) {
		VideoLog.v(TAG, "refreshSdStatus(" + mounted + ")");
		if (mounted) {
			if (MediaAccess.isMediaScanning(this)) {
				VideoLog.d(TAG, "refreshSdStatus() isMediaScanning true");
				showScanningProgress();
				//showList();
			} else {
				VideoLog.d(TAG, "refreshSdStatus() isMediaScanning false");
				hideScanningProgress();
				//refreshVideoList();
				//showList();
			}
		} else {
			hideScanningProgress();
			showEmpty();
		}
	}
	
	private void showScanningProgress() {
		showProgress(getString(R.string.scanning));
	}

	private void hideScanningProgress() {
		hideProgress();
	}
	
	private void showProgress(final String message) {
		if (mProgressDialog == null) {
			mProgressDialog = new ProgressDialog(this);
			mProgressDialog.setIndeterminate(true);
			mProgressDialog.setCanceledOnTouchOutside(false);
			mProgressDialog.setCancelable(false);
			//mProgressDialog.setOnCancelListener(cancelListener);
			mProgressDialog.setMessage(message);
		}
		mProgressDialog.show();
	}
	
	private void hideProgress() {
		if (mProgressDialog != null) {
			mProgressDialog.dismiss();
			mProgressDialog = null;
		}
	}
}
