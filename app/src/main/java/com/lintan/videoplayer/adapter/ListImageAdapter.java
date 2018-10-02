package com.lintan.videoplayer.adapter;

import android.Manifest;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.lintan.videoplayer.R;
import com.lintan.videoplayer.activity.VideoCenterActivity;
import com.lintan.videoplayer.bean.VideoEntity;
import com.lintan.videoplayer.util.SelectManager;
import com.lintan.videoplayer.util.cache.ImageDrawableCache;
import com.lintan.videoplayer.util.cache.VideoExtraInfoCache;
import com.lintan.videoplayer.util.log.VideoLog;
import com.lintan.videoplayer.util.permission.MPermissionUtil;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * use async task to get video thumbnail.
 * <p>
 * this adapter's use a weak reference between task and image view.
 * at the beginning of a asyncTask, we check the image view of task's reference to determine async task.
 * so it may be more fast than {@link ListImageSimpleAdapter}.
 * </p>
 * <p>
 * use media store tool directory.
 * ThumbnailUtils.createVideoThumbnail(viewHolder.dbPath, MediaStore.Images.Thumbnails.MICRO_KIND);
 * </p>
 * <p>
 * use
 * mMediaMetadataRetriever.setDataSource(videoPath);
 * mMediaMetadataRetriever.getFrameAtTime();
 * to get frame at point time clock.
 * </p>
 * Created by Administrator on 2016/9/1.
 */
public class ListImageAdapter extends BaseAdapter implements AbsListView.OnScrollListener {

	private static String TAG = "VideoPlayer/ListImageAdapter";

	private ListView mListView;

	private ImageDrawableCache mImageDrawableCache;
	private VideoExtraInfoCache mVideoRExtraInfoCache;

	/**
	 * dynamic load image
	 */
	private int mCurrentState;
	private int mListFirstItem;
	private int mListLastItem;
	private int mListItemTotalCount;
	private boolean mIsFling;

	private static final int CURRENT_ITEM_KEEP_COUNT = 20;

	private Context mContext;
	private LayoutInflater inflater;

	private Bitmap mLoadingBitmap;
	private ArrayList<VideoEntity> mVideoEntityList;

	public ListImageAdapter(Context context, ListView listView, ArrayList<VideoEntity> videoEntityList) {
		super();
		this.mContext = context;
		this.mVideoEntityList = videoEntityList;
		mListView = listView;
		mImageDrawableCache = ImageDrawableCache.getInstance(mContext);
		mVideoRExtraInfoCache = new VideoExtraInfoCache();
		inflater = LayoutInflater.from(context);
		//mLoadingBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_video_default);
		Bitmap bitmapTmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_video_default);
		mLoadingBitmap = Bitmap.createScaledBitmap(bitmapTmp, mImageDrawableCache.getDefaultWidth
				(), mImageDrawableCache.getDefaultHeight(), true);
		mListView.setOnScrollListener(this);
		mIsFling = false;
	}

	public synchronized ArrayList<VideoEntity> getEntityList() {
		return mVideoEntityList;
	}

	public synchronized void setEntityList(ArrayList<VideoEntity> dataList) {
		mVideoEntityList.clear();
		mVideoEntityList.addAll(dataList);
	}

	public int getmListFirstItem() {
		return mListFirstItem;
	}

	@Override
	public int getCount() {
		return mVideoEntityList.size();
	}

	@Override
	public Object getItem(int position) {
		return mVideoEntityList.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		VideoLog.v(TAG, "adapter getView(), filePath = " + position + ", convertView = " + convertView);
		if (position >= mVideoEntityList.size()) {
			return convertView;
		}
		VideoEntity entity = mVideoEntityList.get(position);
		View view = convertView;
		ViewHolder vh;
		if (null == view) {
			view = inflater.inflate(R.layout.video_list_item_lay_del, null);
			vh = new ViewHolder();
			vh.thumbnailIV = (ImageView) view.findViewById(R.id.video_list_item_thumb);
			vh.videoName = (TextView) view.findViewById(R.id.video_list_item_title);
			vh.videoDuring = (TextView) view.findViewById(R.id.video_list_item_duration);
			vh.videoSize = (TextView) view.findViewById(R.id.video_list_item_size);
			vh.checkBox = (CheckBox) view.findViewById(R.id.video_list_item_checkBox);
			view.setTag(vh);
		} else {
			vh = (ViewHolder) view.getTag();
		}
		if (position >= mVideoEntityList.size()) {
			VideoLog.e(TAG, "adapter getView wrong, position = "
			                + position + "data size = " + mVideoEntityList.size());
			return convertView;
		}
		vh.idDb = entity.getIdDb();
		vh.cacheKey = entity.getData();
		vh.dbPath = entity.getData();

		vh.videoName.setText(entity.getName());
		vh.videoDuring.setText(
				mVideoRExtraInfoCache.getDuration(entity.getDuring()));
		vh.videoSize.setText(mVideoRExtraInfoCache.getFileSize(mContext, entity.getSize()));

		vh.checkBox.setOnCheckedChangeListener(new SelectChangeListener(vh.cacheKey));

		int listMode = VideoCenterActivity.getmMenuMode();
		if (listMode == VideoCenterActivity.LIST_VIEW_NORMAL
		    || listMode == VideoCenterActivity.LIST_VIEW_DELETING
				) {
			vh.checkBox.setVisibility(View.GONE);
		} else {
			vh.checkBox.setVisibility(View.VISIBLE);
			vh.checkBox.setChecked(SelectManager.isItemSelect(vh.cacheKey));
		}

		BitmapDrawable drawable = mImageDrawableCache.getBitmapFromMemoryCache(vh.cacheKey);
		if (null != drawable) {
			VideoLog.d(TAG, "image load from memory cache.");
			vh.thumbnailIV.setImageDrawable(drawable);
		} else {
			if (mIsFling) {
				VideoLog.d(TAG, "image load from default image. you are scrolling so fast.");
				vh.thumbnailIV.setImageBitmap(mLoadingBitmap);
			} else {
				VideoLog.d(TAG, "image load from async task.");
				setViewToHolder(vh);
			}
		}
		//vh.thumbnailIV.setImageBitmap(mLoadingBitmap);
		return view;
	}

	private void setViewToHolder(ViewHolder vh) {

		if (cancelOldTask(vh.cacheKey, vh.thumbnailIV)) {
			//AsyncBitmapTask bitmapTask = new AsyncBitmapTask(vh);
			AsyncBitmapTask bitmapTask = new AsyncBitmapTask(vh.idDb, vh.cacheKey, vh.thumbnailIV);
			AsyncBitmapDrawable asyncDrawable = new AsyncBitmapDrawable(mContext.getResources(),
			                                                            mLoadingBitmap, /*default display image*/
			                                                            bitmapTask);
			vh.thumbnailIV.setImageDrawable(asyncDrawable);
			bitmapTask.execute();
		}
	}

	/**
	 * cancel async task if we fond cacheKey in task is not equal this cacheKey input.
	 *
	 * @param key   image cacheKey now.
	 * @param image image now.
	 * @return true if should new task.
	 */
	private boolean cancelOldTask(String key, ImageView image) {

		AsyncBitmapTask bitmapTask = getBitmapTask(image);
		if (null != bitmapTask) {
			// to compare image's cacheKey of the current task.
			// if equal, it's means that, it the same task and don't cancel it.
			String imageKey = bitmapTask.getImageKey();
			if (key.equals(imageKey)) {
				return false;
			} else {
				/*(null == imageKey || !cacheKey.equals(imageKey)) */
				VideoLog.i(TAG, "task will be canceled, original cacheKey(url) is: " + imageKey + ", now is" + key);
				bitmapTask.cancel(true);
			}
		}
		return true;
	}

	/**
	 * to get the bitmap's work task by thumbnailIV.
	 *
	 * @param imageView the thumbnailIV
	 * @return task working(/worked)
	 */
	private AsyncBitmapTask getBitmapTask(ImageView imageView) {
		if (null == imageView) {
			return null;
		}
		Drawable drawable = imageView.getDrawable();
		if (drawable instanceof AsyncBitmapDrawable) {
			// because we have set a AsyncBitmapDrawable at created it.
			// so we can get a AsyncBitmapDrawable in here.
			AsyncBitmapDrawable asyncBitmapDrawable = (AsyncBitmapDrawable) drawable;
			return asyncBitmapDrawable.getBitmapTask();
		}
		VideoLog.w(TAG, "getBitmapTask from image drawable type: " + drawable);
		return null;
	}

	private void clearNotUseImageItem() {
		int firstIndex = mListFirstItem - CURRENT_ITEM_KEEP_COUNT;
		int lastIndex = mListLastItem + CURRENT_ITEM_KEEP_COUNT;
		int currentCount = lastIndex - firstIndex;
		int saveCount = CURRENT_ITEM_KEEP_COUNT * 2 + currentCount;
		if (firstIndex < 0) {
			firstIndex = 0;
		}
		if (lastIndex > mListItemTotalCount) {
			lastIndex = mListItemTotalCount;
		}
		for (int i = firstIndex; i >= 0 && lastIndex > saveCount; i--) {
			VideoLog.i(TAG, "before i = " + i + ", remove a cache, cacheKey is: " + mVideoEntityList.get(i).getData());
			mImageDrawableCache.removeFromMemoryCache(mVideoEntityList.get(i).getData());
		}
		for (int i = lastIndex;
		     i < mListItemTotalCount && firstIndex < saveCount;
		     i++) {
			VideoLog.i(TAG, "after i = " + i + ", remove a cache, cacheKey is: " + mVideoEntityList.get(i).getData());
			mImageDrawableCache.removeFromMemoryCache(mVideoEntityList.get(i).getData());
		}
		VideoLog.v(TAG, "first = " + firstIndex + ", last = " + lastIndex + ", total = " + mListItemTotalCount);
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		VideoLog.v(TAG, "onScrollStateChanged, current state " + mCurrentState + ", now state is " + scrollState);
		mCurrentState = scrollState;
		switch (scrollState) {
			case SCROLL_STATE_FLING:
				mIsFling = true;
				break;
			case SCROLL_STATE_TOUCH_SCROLL:
				mIsFling = false;
				break;
			case SCROLL_STATE_IDLE:
				mIsFling = false;
				notifyDataSetChanged();
				clearNotUseImageItem();
			default:
				break;
		}
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
	                     int totalItemCount) {

		mListFirstItem = firstVisibleItem;
		mListLastItem = mListFirstItem + visibleItemCount;
		mListItemTotalCount = totalItemCount;
	}

	class ViewHolder {
		// id in database.
		long idDb;
		// cache cacheKey is path
		String cacheKey;
		String dbPath;
		ImageView thumbnailIV;

		CheckBox checkBox;
		TextView videoName;
		TextView videoDuring;
		TextView videoSize;
	}

	class AsyncBitmapTask extends AsyncTask<String, Float, BitmapDrawable> {

		private String imageKey;
		private long idDb;
		private ViewHolder viewHolder;

		private WeakReference<ImageView> imageViewWeakReference;

		public AsyncBitmapTask(ImageView imageView) {
			super();
			imageViewWeakReference = new WeakReference<>(imageView);
		}

		/**
		 * we use the view holder, find the behaivre of list view is wrong.
		 *
		 * @param idDb      database row id.
		 * @param imageKey  image key of cache, usually is image path.
		 * @param imageView the view of screen.
		 */
		public AsyncBitmapTask(long idDb, String imageKey, ImageView imageView) {
			super();
			this.idDb = idDb;
			this.imageKey = imageKey;
			imageViewWeakReference = new WeakReference<>(imageView);
		}

		/**
		 * when you filling, it's will do not work.
		 *
		 * @param viewHolder vh
		 * @deprecated replaced by {@link #AsyncBitmapTask(long, String, ImageView)}
		 */
		public AsyncBitmapTask(ViewHolder viewHolder) {
			super();
			this.viewHolder = viewHolder;
			this.idDb = viewHolder.idDb;
			this.imageKey = viewHolder.cacheKey;
			imageViewWeakReference = new WeakReference<>(viewHolder.thumbnailIV);
		}

		public String getImageKey() {
			return imageKey;
		}


		@Override
		protected BitmapDrawable doInBackground(String... params) {

			if (!MPermissionUtil.hasPermissionsAll((VideoCenterActivity) mContext,
			                                       Manifest.permission.READ_EXTERNAL_STORAGE,
			                                       Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
				return null;
			}
			Bitmap bitmapInDb = null;
			try {
				bitmapInDb = MediaStore.Video.Thumbnails.getThumbnail(mContext.getContentResolver(),
				                                                      idDb,
				                                                      MediaStore.Video.Thumbnails.FULL_SCREEN_KIND,
				                                                      null);
			} catch (IllegalArgumentException e) {
				VideoLog.v(TAG, "error in ListImageAdapter AsyncBitmapTask#doing background()." + e.toString());
			}
			// 1. use tools to create a thumbnail,
			// but the image is not clear, so use the second way.
			if (null == bitmapInDb) {
				//VideoLog.d(TAG, "going to call getBitmapFromSdCardByCreate()");
				//bitmapInDb = getBitmapFromSdCardByCreate(viewHolder.dbPath);
			}
			// 2. to get the first image of this video.
			if (null == bitmapInDb) {
				bitmapInDb = getBitmapFromVideo(imageKey);
			}
			if (null == bitmapInDb) {
				VideoLog.w(TAG, "do in background return null!");
				return null;
			}
			/*
			It's not be ok, and make app run slowly, because it start at whenever 'getView' was called and do not
			cancel. so, we use the subclass of bitmap drawable which add a weak reference to bitmap task.

			BitmapDrawable drawable = getScaledBitmapDrawableByBitmap(bitmapInDb,
			                                                          mImageDrawableCache.getDefaultWidth(),
			                                                          mImageDrawableCache.getDefaultHeight());
			*/
			AsyncBitmapDrawable drawable = getScaledAsyncBitmapDrawableByBitmap(bitmapInDb,
			                                                                    mImageDrawableCache.getDefaultWidth(),
			                                                                    mImageDrawableCache.getDefaultHeight
					                                                                    ());
			mImageDrawableCache.addBitmapToMemoryCache(imageKey, drawable);
			return drawable;
		}

		private Bitmap getBitmapFromSdCardByCreate(String videoPath) {
			VideoLog.i(TAG, "get the bitmap from db , the path is: " + videoPath);
			return ThumbnailUtils.createVideoThumbnail(imageKey, MediaStore.Images.Thumbnails.MICRO_KIND);
		}

		private Bitmap getBitmapFromVideo(String videoPath) {
			VideoLog.i(TAG, "get the bitmap from video frame, the path is: " + videoPath);
			File file = new File(videoPath);
			if (!file.exists()) {
				VideoLog.e(TAG, "getBitmapFromVideo()-- video path is error. return null, path =" + videoPath);
				return null;
			}
			MediaMetadataRetriever mMediaMetadataRetriever = new MediaMetadataRetriever();
			mMediaMetadataRetriever.setDataSource(videoPath);

			Bitmap videoFrame = mMediaMetadataRetriever.getFrameAtTime();
			mMediaMetadataRetriever.release();
			return videoFrame;
		}

		private BitmapDrawable getBitmapDrawableByBitmap(Bitmap src) {

			return new BitmapDrawable(mContext.getResources(), src);
		}

		private BitmapDrawable getScaledBitmapDrawableByBitmap(Bitmap src, int dW, int dH) {
			Bitmap scaleMap = Bitmap
					.createScaledBitmap(src, dW, dH, true);
			return new BitmapDrawable(mContext.getResources(), scaleMap);
		}

		private AsyncBitmapDrawable getScaledAsyncBitmapDrawableByBitmap(Bitmap src, int dW, int dH) {
			Bitmap scaleMap = Bitmap
					.createScaledBitmap(src, dW, dH, true);
			return new AsyncBitmapDrawable(mContext.getResources(), scaleMap, this);
		}

		@Override
		protected void onPostExecute(BitmapDrawable drawable) {
			ImageView imageView = getAttachImageView();
			if (null == drawable || null == imageView) {
				VideoLog.w(TAG, "task onPostExecute do nothing."
				                + " image view is null? " + imageView
				                + ", background drawable is null? " + drawable);
				return;
			}
			imageView.setImageDrawable(drawable);
		}

		public ImageView getAttachImageView() {
			ImageView imageView = imageViewWeakReference.get();
			if (getBitmapTask(imageView) == this) {
				return imageView;
			}
			return null;
		}
	}

	/**
	 * to get the async task, must use a weak reference of task, \
	 * if not, the deep will not be gc.
	 */
	class AsyncBitmapDrawable extends BitmapDrawable {

		private WeakReference<AsyncBitmapTask> bitmapTaskWeakReference;

		public AsyncBitmapDrawable(Resources res, Bitmap bitmap, AsyncBitmapTask task) {
			super(res, bitmap);
			bitmapTaskWeakReference = new WeakReference<>(task);
		}

		public AsyncBitmapTask getBitmapTask() {
			return bitmapTaskWeakReference.get();
		}
	}

	class SelectChangeListener implements CheckBox.OnCheckedChangeListener {

		private String filePath;

		SelectChangeListener(String filePath) {
			this.filePath = filePath;
		}

		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			SelectManager.setItemSelect(filePath, isChecked);
		}
	}
}

