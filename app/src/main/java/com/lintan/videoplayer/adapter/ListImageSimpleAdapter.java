package com.lintan.videoplayer.adapter;

import android.Manifest;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.lintan.videoplayer.R;
import com.lintan.videoplayer.activity.VideoCenterActivity;
import com.lintan.videoplayer.bean.VideoEntity;
import com.lintan.videoplayer.util.cache.ImageDrawableCache;
import com.lintan.videoplayer.util.cache.VideoExtraInfoCache;
import com.lintan.videoplayer.util.log.VideoLog;
import com.lintan.videoplayer.util.permission.MPermissionUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * a simple adapter to load bitmap from database or get a frame of video.
 * Created by Administrator on 2016/8/15.
 */
public class ListImageSimpleAdapter extends BaseAdapter implements AbsListView.OnScrollListener {

	private static final String TAG = "VideoPlayer/ListImageSimpleAdapter";

	private ListView mListView;
	private ImageDrawableCache mImageDrawableCache;
	private VideoExtraInfoCache mVideoRExtraInfoCache;

	private Context mContext;
	private LayoutInflater inflater;

	private BitmapDrawable mLoadingBitmapDrawable;
	private ArrayList<VideoEntity> mVideoEntityList;

	/**
	 * dynamic load image
	 */
	private int mCurrentState;
	private int mListFirstItem;
	private int mListLastItem;
	private int mListItemTotalCount;
	private LinkedList<AsyncBitmapTask> mAsyncBitmapTaskList;

	/**
	 * can replace by firstRunning.
	 */
	private boolean isFling;
	private boolean isFirstRunning;

	private int mLastFirstItemIndex = 0;
	private FlingOrientation mFlingOrientation;

	public enum FlingOrientation {
		UP,/*finger going to up*/
		DOWN/*finger going to down*/
	}

	public ListImageSimpleAdapter(Context context, ListView listView, ArrayList<VideoEntity> videoEntityList) {
		super();
		this.mContext = context;
		this.mVideoEntityList = videoEntityList;
		inflater = LayoutInflater.from(context);
		mListView = listView;

		mImageDrawableCache = ImageDrawableCache.getInstance(mContext);
		mVideoRExtraInfoCache = new VideoExtraInfoCache();
		mAsyncBitmapTaskList = new LinkedList<>();

		Bitmap bitmapTmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_video_default);
		Bitmap loadingBitmap = Bitmap.createScaledBitmap(bitmapTmp, mImageDrawableCache.getDefaultWidth
				(), mImageDrawableCache.getDefaultHeight(), true);
		mLoadingBitmapDrawable = new BitmapDrawable(context.getResources(), loadingBitmap);

		mFlingOrientation = FlingOrientation.UP;
		mCurrentState = SCROLL_STATE_IDLE;
		isFling = false;
		isFirstRunning = true;
		mListView.setOnScrollListener(this);
	}

	public ArrayList<VideoEntity> getEntityList() {
		return mVideoEntityList;
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
		View view = convertView;
		ViewHolder vh;
		if (convertView == null) {
			vh = new ViewHolder();
			view = inflater.inflate(R.layout.video_list_item_lay, null);
			vh.thumbnailIV = (ImageView) view.findViewById(R.id.video_list_item_thumb);
			vh.videoName = (TextView) view.findViewById(R.id.video_list_item_title);
			vh.videoDuring = (TextView) view.findViewById(R.id.video_list_item_duration);
			vh.videoSize = (TextView) view.findViewById(R.id.video_list_item_size);

			view.setTag(vh);
		} else {
			vh = (ViewHolder) view.getTag();
		}

		vh.key = mVideoEntityList.get(position).getData();
		vh.dbPath = mVideoEntityList.get(position).getData();

		vh.videoName.setText(mVideoEntityList.get(position).getName());
		vh.videoDuring.setText(
				mVideoRExtraInfoCache.getDuration(mVideoEntityList.get(position).getDuring()));
		vh.videoSize.setText(mVideoRExtraInfoCache.getFileSize(mContext, mVideoEntityList.get(position).getSize()));

		// set tag, we use list view findViewWithTag to find it.
		vh.thumbnailIV.setTag(vh.key);
		vh.thumbnailIV.setImageResource(R.drawable.ic_video_default);

		BitmapDrawable drawable = mImageDrawableCache.getBitmapFromMemoryCache(vh.key);
		vh.thumbnailIV.setImageDrawable(mLoadingBitmapDrawable);
		if (null != drawable) {
			VideoLog.v(TAG, "in getView(), image load from memory cache.");
			vh.thumbnailIV.setImageDrawable(drawable);
		} else {
			//AsyncBitmapTask task = new AsyncBitmapTask(vh);
			//mAsyncBitmapTaskList.add(task);
			//task.execute();
		}
		return view;
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		VideoLog.v(TAG, "onScrollStateChanged, current state " + mCurrentState + ", now state is " + scrollState);
		mCurrentState = scrollState;
		switch (scrollState) {
			case SCROLL_STATE_FLING:
				isFling = true;
				cancelAllAsyncTask();
				break;
			case SCROLL_STATE_TOUCH_SCROLL:
			case SCROLL_STATE_IDLE:
				isFling = false;
				isFirstRunning = false;
				clearNotUseImageItem();
				freshCurrentFace();
			default:
				isFling = false;
				break;
		}
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
	                     int totalItemCount) {

		if (mLastFirstItemIndex < firstVisibleItem) {
			mFlingOrientation = FlingOrientation.UP;
		} else if (mLastFirstItemIndex > firstVisibleItem) {
			mFlingOrientation = FlingOrientation.DOWN;
		}
		mLastFirstItemIndex = firstVisibleItem;

		mListFirstItem = firstVisibleItem;
		mListLastItem = mListFirstItem + visibleItemCount;
		mListItemTotalCount = totalItemCount;

		if (isFirstRunning && visibleItemCount > 0) {
			VideoLog.i(TAG, "on Scroll state " + mCurrentState + ", It is should be happened at first time running ");
			isFirstRunning = false;
			freshCurrentFace();
		}
	}

	public void cancelAllAsyncTask() {
		if (null == mAsyncBitmapTaskList) {
			VideoLog.w(TAG, "cancelAllAsyncTask(), task list is null, and return.");
			return;
		}

		int saveTaskCount = 10;
		int cancelTaskCount = mListLastItem - saveTaskCount;
		if(cancelTaskCount < 0){
			cancelTaskCount = 0;
		}

//		for(int i = 0; i < cancelTaskCount; i++){
//			AsyncBitmapTask task = mAsyncBitmapTaskList.get(i);
//			if(!task.isCancelled()){
//				task.cancel(true);
//			}
//			mAsyncBitmapTaskList.remove(i);
//		}
		int i = 0;
		for (AsyncBitmapTask task : mAsyncBitmapTaskList) {
			i++;
			if(i >= cancelTaskCount){
				break;
			}
			task.cancel(true);
		}
	}

	/**
	 * when user stop scroll, just load current screen image Item.
	 * <p/>
	 * think of user experience, the way may slowing load image from DB. so I changed my logic with get view.
	 * modified by tanlin at 09/09/16
	 */
	private void freshCurrentFace() {
		String key;
		ImageView image;
		if (mFlingOrientation == FlingOrientation.UP) {
			VideoLog.d(TAG, "finger up first item " + mListFirstItem + " mListLastItem " + mListLastItem);
			for (int i = mListFirstItem; i < mListLastItem && i < mListItemTotalCount; i++) {
				key = mVideoEntityList.get(i).getData();
				image = (ImageView) mListView.findViewWithTag(key);
				setSingleImage(image, key);
			}
		} else if (mFlingOrientation == FlingOrientation.DOWN) {
			VideoLog.d(TAG, "finger down first item " + mListFirstItem + " mListLastItem " + mListLastItem);
			for (int i = mListLastItem - 1; i >= 0 && i >= mListFirstItem; i--) {
				key = mVideoEntityList.get(i).getData();
				image = (ImageView) mListView.findViewWithTag(key);
				setSingleImage(image, key);
			}
		}
	}

	private void setSingleImage(ImageView image, String key) {
		if (null == image) {
			VideoLog.w(TAG, "setSingleImage() param image is null, and return");
			return;
		}
		BitmapDrawable drawable = mImageDrawableCache.getBitmapFromMemoryCache(key);
		if (drawable != null) {
			VideoLog.v(TAG, "setSingleImage: image is loaded from cache, path: " + key);
			image.setImageDrawable(drawable);
		} else {
			VideoLog.v(TAG, "setSingleImage: image is loaded from task, path: " + key);
			AsyncBitmapTask task = new AsyncBitmapTask(image, key, key);
			task.execute(key);
			mAsyncBitmapTaskList.add(task);
		}
	}

	private void clearNotUseImageItem() {
		int firstIndex = mListFirstItem - 5;
		int lastIndex = mListLastItem + 6;
		if (firstIndex < 0) {
			firstIndex = 0;
		}
		if (lastIndex > mListItemTotalCount) {
			lastIndex = mListItemTotalCount;
		}
		for (int i = firstIndex; i >= 0 && lastIndex < 10; i--) {
			VideoLog.i(TAG, "i = " + i + "remove a cache, cacheKey is: " + mVideoEntityList.get(i).getData());
			mImageDrawableCache.removeFromMemoryCache(mVideoEntityList.get(i).getData());
		}
		for (int i = lastIndex; i < mListItemTotalCount && firstIndex > 6; i++) {
			VideoLog.i(TAG, "i = " + i + "remove a cache, cacheKey is: " + mVideoEntityList.get(i).getData());
			mImageDrawableCache.removeFromMemoryCache(mVideoEntityList.get(i).getData());
		}
	}

	class ViewHolder {
		// id in database.
		long idDb;
		// cache cacheKey is path
		String key;
		String dbPath;
		ImageView thumbnailIV;

		TextView videoName;
		TextView videoDuring;
		TextView videoSize;
	}

	class AsyncBitmapTask extends AsyncTask<String, Float, BitmapDrawable> {

		private ViewHolder viewHolder;

		public AsyncBitmapTask(ImageView imageView, String imageKey, String dbPath) {
			viewHolder = new ViewHolder();
			viewHolder.dbPath = dbPath;
			viewHolder.idDb = 0;
			viewHolder.key = imageKey;
			viewHolder.thumbnailIV = imageView;
		}

		public AsyncBitmapTask(ViewHolder viewHolder) {
			super();
			this.viewHolder = viewHolder;
			mAsyncBitmapTaskList.add(this);
		}

		@Override
		protected BitmapDrawable doInBackground(String... params) {

			if (!MPermissionUtil.hasPermissionsAll((VideoCenterActivity) mContext,
			                                       Manifest.permission.READ_EXTERNAL_STORAGE,
			                                       Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
				return null;
			}

			Bitmap bitmapInDb = MediaStore.Video.Thumbnails.getThumbnail(mContext.getContentResolver(),
			                                                             viewHolder.idDb,
			                                                             MediaStore.Video.Thumbnails.FULL_SCREEN_KIND,
			                                                             null);
			// 1. use tools to create a thumbnail.
			if (null == bitmapInDb) {
				//VideoLog.d(TAG, "going to call getBitmapFromSdCardByCreate()");
				//bitmapInDb = getBitmapFromSdCardByCreate(viewHolder.dbPath);
			}
			// 2. to get the first image of this video.
			if (null == bitmapInDb) {
				VideoLog.d(TAG, "going to call getBitmapFromVideo()");
				bitmapInDb = getBitmapFromVideo(viewHolder.dbPath);
			}
			if (null == bitmapInDb) {
				VideoLog.d(TAG, "do in background return null");
				return null;
			}
			BitmapDrawable drawable = getScaledBitmapDrawableByBitmap(bitmapInDb,
			                                                          mImageDrawableCache.getDefaultWidth(),
			                                                          mImageDrawableCache.getDefaultHeight());
			mImageDrawableCache.addBitmapToMemoryCache(viewHolder.key, drawable);
			return drawable;
		}

		private Bitmap getBitmapFromSdCardByCreate(String videoPath) {
			VideoLog.i(TAG, "get the bitmap from db , the path is: " + videoPath);
			return ThumbnailUtils.createVideoThumbnail(viewHolder.dbPath, MediaStore.Images.Thumbnails.MICRO_KIND);
		}

		private Bitmap getBitmapFromVideo(String videoPath) {
			VideoLog.w(TAG, "get the bitmap from video frame, the path is: " + videoPath);
			File file = new File(videoPath);
			if (!file.exists()) {
				VideoLog.e(TAG, "getBitmapFromVideo()-- videopath is error" + videoPath);
				return null;
			}
			MediaMetadataRetriever mMediaMetadataRetriever = new MediaMetadataRetriever();
			mMediaMetadataRetriever.setDataSource(videoPath);
			return mMediaMetadataRetriever.getFrameAtTime();
		}

		private BitmapDrawable getBitmapDrawableByBitmap(Bitmap src) {

			return new BitmapDrawable(mContext.getResources(), src);
		}

		private BitmapDrawable getScaledBitmapDrawableByBitmap(Bitmap src, int dW, int dH) {
			Bitmap scaleMap = Bitmap
					.createScaledBitmap(src, dW, dH, true);
			return new BitmapDrawable(mContext.getResources(), scaleMap);
		}

		@Override
		protected void onPostExecute(BitmapDrawable drawable) {
			ImageView imageView = (ImageView) mListView.findViewWithTag(viewHolder.key);
			if (null == drawable || null == imageView) {
				return;
			}
			imageView.setImageDrawable(drawable);
			mAsyncBitmapTaskList.remove(this);
		}
	}
}
