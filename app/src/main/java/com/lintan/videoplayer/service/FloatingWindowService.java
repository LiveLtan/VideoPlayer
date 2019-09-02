package com.lintan.videoplayer.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import com.lintan.videoplayer.R;
import com.lintan.videoplayer.activity.VideoPlayActivity;
import com.lintan.videoplayer.activity.base.VideoPlayerBaseActivity;
import com.lintan.videoplayer.bean.VideoEntity;
import com.lintan.videoplayer.util.log.VideoLog;
import com.lintan.videoplayer.util.media.MyMediaControlCenter;

import java.lang.ref.WeakReference;

/**
 * It's support a floating window to play a video, and the
 * video surface can be move follow your finger, enjoying it.
 * <p/>
 * Created by lintan on 9/10/16.
 */
public class FloatingWindowService extends Service
		implements View.OnClickListener, View.OnTouchListener, SeekBar.OnSeekBarChangeListener,
		           MyMediaControlCenter.ControllerCallBack {
	
	public static final String VIDEO_CURRENT_POSITION = "currentPosition";

	private static final String TAG = "VideoPlayer/FloatingWindowService";

	// service in first run, we fond a bug, fix it use boolean tag.
	private static final String IS_SERVICE_LAUNCHED = "isServiceLaunched";

	// if a video's width bigger than height, the default height is the
	// one third of screen's height.
	private static final float FLOAT_WINDOW_SCALE = 1.0f / 3;

	private static final float FLOAT_WINDOW_WIDTH_MIN_RATE = 0.5F;
	private static final float FLOAT_WINDOW_HEIGHT_MIN_RATE = 0.5F;

	private static final float FLOAT_WINDOW_WIDTH_MAX_RATE = 1.2f;
	private static final float FLOAT_WINDOW_HEIGHT_MAX_RATE = 1.2f;

	private static final float FLOAT_WINDOW_LEFT_RIGHT_SIDE = 0.5f;
	private static final float FLOAT_WINDOW_TOP_BOTTOM_SIDE = 0.5f;

	// it's not ok
	//private static final int VIDEO_ORIENTATION_VERTICAL = 0;
	//private static final int VIDEO_ORIENTATION_HORIZONTAL = 90;

	private WindowManager.LayoutParams mWmParams;
	private WindowManager mWindowManager;

	private static final int FINGER_MAX_CLICK_ZONE = 10;
	private float mLastRawX = 0f;
	private float mLastRawY = 0f;

	private float mStartViewX;
	private float mStartViewY;
	private Point mScreenSize;

	//private LinearLayout mFloatLayout;
	private RelativeLayout mFloatLayout;

	private SurfaceHolder mSurfaceHolder;
	private SurfaceView mVideoPlaySurfaceView;
	private ImageView mTmpImageView;

	private LinearLayout mFloatingBottomLinear;
	private ImageView mResumePauseImageView;
	private ImageView mCloseWindowImageView;
	private SeekBar mVideoFloatingSeekBar;
	private ImageView mRestoreToActivityImageView;

	private static final int TIME_MILLISECOND_UNIT = 1000;
	private static final int PERCENT_SWITCH = 100;

	private static final int MEDIA_PLAYER_PROGRESS_UPDATE = 0x0001;
	private static final int MEDIA_PLAYER_RP_IMAGE_HIDE = 0x0002;
	private VideoPlayHandler mVideoPlayHandler;

	// private String mCurrentVideoPath;
	// private int mCurrentPosition;
	// modify because object by lin.tan
	private VideoEntity mVideoEntity;

	private MediaPlayer mMediaPlayer;
	private MyMediaControlCenter mMediaPlayerControl;
	private boolean mSeekBarDragging;
	private boolean isVideoPlaying;

	private boolean mFloatingSeekBarIsShowing;
	//private boolean isNeedCloseWindow = false;

	// to reduce calculate time in action move.
	private int mSurfaceViewMarginTop = 0;
	private int mSurfaceViewMarginLeft = 0;
	private int mScreenFingerCount = 0;
	private float lastDistance;
	private boolean isFistIn;

	private static class VideoPlayHandler extends Handler {
		private WeakReference<FloatingWindowService> serviceWeakReference;

		private FloatingWindowService windowService;

		VideoPlayHandler(FloatingWindowService service) {
			serviceWeakReference = new WeakReference<>(service);
		}

		@Override
		public void handleMessage(Message msg) {
			int what = msg.what;
			windowService = serviceWeakReference.get();
			if (null == windowService) {
				return;
			}
			switch (what) {
				case MEDIA_PLAYER_PROGRESS_UPDATE:
					VideoLog.i(TAG, "MEDIA_PLAYER_PROGRESS_UPDATE -- handler receive");
					windowService.isVideoPlaying = true;
					windowService.setResumePlayImage();
					windowService.mVideoEntity.setCurrentPosition(windowService.setProgress());
					windowService.mVideoEntity.setPlaying(true);
					if (!windowService.mSeekBarDragging && windowService.mMediaPlayerControl.isPlaying()) {
						windowService.mMediaPlayerControl.sendUpdateProgressMessage();
					}
					break;
				case MEDIA_PLAYER_RP_IMAGE_HIDE:
					break;
				default:
					break;
			}
			super.handleMessage(msg);
		}
	}

	public FloatingWindowService() {
		super();
	}
	
	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		//initWindow();
		isFistIn = true;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		mVideoEntity = intent.getBundleExtra(VideoPlayerBaseActivity.ENTITY_OBJ_BUNDLE_TAG)
		                     .getParcelable(VideoPlayerBaseActivity.ENTITY_OBJ_TAG);
		if (null == mVideoEntity /*|| null == mCurrentVideoPath || 0 == mCurrentVideoPath.length()*/) {
			throw new IllegalArgumentException("you must put you video path in intent.");
		}
		initWindow();
		createFloatView();
		initControls();
		updateWindowPosition();
		//play();
		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mFloatLayout != null) {
			mWindowManager.removeView(mFloatLayout);
		}
		mMediaPlayerControl.stop();
		mSurfaceHolder.removeCallback(mMediaPlayerControl);
	}

	private void initWindow() {
		mWindowManager = (WindowManager) getApplication().getSystemService(Context.WINDOW_SERVICE);
		if (null == mWmParams) {
			mWmParams = new WindowManager.LayoutParams();
		}
		if (Build.VERSION.SDK_INT < 26) {
			mWmParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
		} else {
			mWmParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
		}
		mWmParams.format = PixelFormat.RGBA_8888;
		initScreenSize();
		setWindowCanOutOfScreen();
		setWindowWidthHeight();
	}

	private void initScreenSize() {
		mScreenSize = new Point();
		mWindowManager.getDefaultDisplay().getSize(mScreenSize);
	}

	/**
	 * setWindowFullScreenAnd
	 */

	private void setWindowCanOutOfScreen() {
		// | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
		// lead to back screen.
		mWmParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
		                  | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
		                  | WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE
		                  | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
		                  | View.SYSTEM_UI_FLAG_IMMERSIVE
		                  | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
	}

	private void setWindowWidthHeight() {
		mWmParams.gravity = Gravity.START | Gravity.TOP;
		mWmParams.x = 0;
		mWmParams.y = 0;
		mWmParams.width = mScreenSize.x;
		mWmParams.height = (int) (mScreenSize.y * FLOAT_WINDOW_SCALE);

		VideoLog.d(TAG, "init window width = " + mWmParams.width + ", height = " + mWmParams.height);
		VideoLog.d(TAG, "init window x = " + mWmParams.x + ", y = " + mWmParams.y);
	}

	private void createFloatView() {
		createFloatingView();
		setSubViewOfFloating(mFloatLayout);
		setSubViewListener();
	}

	private void createFloatingView() {
		LayoutInflater inflater = LayoutInflater.from(getApplication());
		VideoLog.i(TAG, "createFloatingView-----mVideoEntity.getOrientation()" + mVideoEntity.getOrientation());
		if (mVideoEntity.getWidth() > mVideoEntity.getHeight()) {
			VideoLog.i(TAG, "createFloatingView-----mVideoEntity.getWidth() > mVideoEntity.getHeight() is true" +
			                mVideoEntity.getWidth() + "x" + mVideoEntity.getHeight());
			mFloatLayout = (RelativeLayout) inflater.inflate(R.layout.video_play_float_layout, null);
		} else {
			VideoLog.i(TAG, "createFloatingView-----mVideoEntity.getWidth() > mVideoEntity.getHeight() is false" +
			                mVideoEntity.getWidth() + "x" + mVideoEntity.getHeight());
			mFloatLayout = (RelativeLayout) inflater.inflate(R.layout.video_play_float_vertical_layout, null);
		}
		mWindowManager.addView(mFloatLayout, mWmParams);
	}

	private void setSubViewOfFloating(View floatingViewParent) {
		//mSurfaceFrame = (FrameLayout) floatingViewParent.findViewById(R.id.video_play_floating_surface_fr);
		mVideoPlaySurfaceView = (SurfaceView) floatingViewParent.findViewById(R.id.video_play_floating_surface_view);

		// add for tmp image view.
		mTmpImageView = (ImageView) floatingViewParent.findViewById(R.id.video_play_floating_image_view);

		mFloatingBottomLinear = (LinearLayout)
				floatingViewParent.findViewById(R.id.video_play_floating_play_during_ll);
		mCloseWindowImageView = (ImageView) floatingViewParent.findViewById(R.id.floating_video_play_close_window);
		mResumePauseImageView = (ImageView) floatingViewParent.findViewById(R.id.video_play_floating_rp);
		mVideoFloatingSeekBar = (SeekBar) floatingViewParent.findViewById(R.id.video_play_floating_seek_ll_bar);

		mRestoreToActivityImageView = (ImageView) floatingViewParent.findViewById(R.id.video_play_floating_restore);
	}

	private void setSubViewListener() {
		mVideoPlaySurfaceView.setOnTouchListener(this);
		mVideoPlaySurfaceView.setOnClickListener(this);
		mCloseWindowImageView.setOnClickListener(this);
		mResumePauseImageView.setOnClickListener(this);
		mRestoreToActivityImageView.setOnClickListener(this);
	}

	/**
	 * init media player and media control,
	 * init handler to update progress of seek bar.
	 */
	private void initControls() {
		mFloatingSeekBarIsShowing = false;
		mVideoPlayHandler = new VideoPlayHandler(this);

		mMediaPlayer = new MediaPlayer();
		mSurfaceHolder = mVideoPlaySurfaceView.getHolder();

		// add begin
		mMediaPlayerControl = new MyMediaControlCenter(mMediaPlayer, mVideoEntity, mVideoPlayHandler);
		mMediaPlayerControl.setControllerCallBack(this);
		mSurfaceHolder.addCallback(mMediaPlayerControl);
		mMediaPlayer.setOnErrorListener(mMediaPlayerControl);
		mMediaPlayer.setOnPreparedListener(mMediaPlayerControl);
		mMediaPlayer.setOnCompletionListener(mMediaPlayerControl);
		// add end

		mVideoPlaySurfaceView.setOnTouchListener(this);
		mVideoFloatingSeekBar.setOnSeekBarChangeListener(this);
	}

	/**
	 * to let the window to center of screen.
	 */
	private void updateWindowPosition() {
		mWmParams.x = (mScreenSize.x - mWmParams.width) / 2;
		mWmParams.y = (mScreenSize.y - mWmParams.height) / 2;
		mWindowManager.updateViewLayout(mFloatLayout, mWmParams);
	}


	/**
	 * set image while media is playing, because touch event
	 * will change it.
	 */
	private void clickToChangerPlayResumeImage() {
		if (isVideoPlaying) {
			mResumePauseImageView.setImageResource(R.drawable.floating_video_play_play_window);
			isVideoPlaying = false;
			mVideoEntity.setPlaying(false);
			mVideoPlayHandler.removeMessages(MEDIA_PLAYER_PROGRESS_UPDATE);
			mMediaPlayerControl.pause();

		} else {
			mResumePauseImageView.setImageResource(R.drawable.floating_video_play_pause_window);
			isVideoPlaying = true;
			mVideoEntity.setPlaying(true);
			if (mVideoEntity.getCurrentPosition() >= mVideoEntity.getDuring()) {
				mMediaPlayerControl.seekTo(0);
				mVideoEntity.setCurrentPosition(0);
			}
			mMediaPlayerControl.start();
		}
	}

	/**
	 * to start a new activity.
	 *
	 * @param destActivity where you go.
	 */
	private void startActivity(Class<?> destActivity) {
		Intent intentActivity = new Intent(this, destActivity);
		// if not, receive a runtime exception: add flag new task.
		intentActivity.putExtra(VideoPlayActivity.INTENT_FROM, FloatingWindowService.class.getName());
		//intentActivity.putExtra(VideoPlayActivity.INTENT_SHOULD_PLAY, mVideoEntity.isPlaying());
		intentActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intentActivity.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
		//intentActivity.putExtra(VideoCenterActivity.VIDEO_PATH_INTENT_FLAG, mCurrentVideoPath);
		//intentActivity.putExtra(FloatingWindowService.VIDEO_CURRENT_POSITION, mCurrentPosition);
		Bundle bundle = new Bundle();
		bundle.putParcelable(VideoPlayerBaseActivity.ENTITY_OBJ_TAG, mVideoEntity);
		intentActivity.putExtra(VideoPlayerBaseActivity.ENTITY_OBJ_BUNDLE_TAG, bundle);
		startActivity(intentActivity);
		this.stopSelf();
	}

	private void showOrHidePlayBar() {
		VideoLog.d(TAG, "showOrHidePlayBar() " + mFloatingSeekBarIsShowing);
		if (mFloatingSeekBarIsShowing) {
			mFloatingBottomLinear.setVisibility(View.GONE);
			mCloseWindowImageView.setVisibility(View.GONE);
		} else {
			mFloatingBottomLinear.setVisibility(View.VISIBLE);
			mCloseWindowImageView.setVisibility(View.VISIBLE);
		}
		mFloatingSeekBarIsShowing = !mFloatingSeekBarIsShowing;
	}

	@Override
	public void hideTmpImage() {
		if (mTmpImageView.getVisibility() == View.VISIBLE) {
			mTmpImageView.setVisibility(View.GONE);
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.video_play_floating_layout:
				//case R.id.video_play_floating_surface_fr:
			case R.id.video_play_floating_surface_view:
				VideoLog.d(TAG, "video_play_floating_surface_view click.");
				showOrHidePlayBar();
				break;
			case R.id.floating_video_play_close_window:
				this.stopSelf();
				break;
			case R.id.video_play_floating_rp:
				hideTmpImage();
				clickToChangerPlayResumeImage();
				break;
			case R.id.video_play_floating_restore:
				VideoLog.d(TAG, "video_play_floating_restore click.");
				startActivity(VideoPlayActivity.class);
				break;
			case R.layout.video_play_float_layout:
				VideoLog.d(TAG, "video_play_floating_restore click.");
				break;
			default:
				break;
		}
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {

		float mRawX = event.getRawX();
		float mRawY = event.getRawY();

		switch (event.getAction() & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_DOWN:
				mScreenFingerCount = 1;
				mStartViewX = event.getX();
				mStartViewY = event.getY();
				mLastRawX = event.getRawX();
				mLastRawY = event.getRawY();
				mSurfaceViewMarginTop = v.getTop();
				mSurfaceViewMarginLeft = v.getLeft();

				VideoLog.d(TAG, "lp ---------- " + mSurfaceViewMarginLeft + " " + mSurfaceViewMarginTop);
				//VideoLog.v(TAG, "onTouch action down, last rawx = " + mLastRawX + "last rawY = " + mLastRawY);
				VideoLog.v(TAG, "onTouch action down, startx = " + mStartViewX + "starty = " + mStartViewY);
				break;
			case MotionEvent.ACTION_POINTER_DOWN:
				lastDistance = distanceBetweenPoints(event);
				mScreenFingerCount += 1;
				break;
			case MotionEvent.ACTION_MOVE:
				if (mScreenFingerCount >= 2 && calculateDistance(event)) {
					updateWindowSize();
				} else {
					// move with window mSurfaceViewMarginTop corrdinate.
					/*
					* because we are setting the top and left position,
					* so use rawX - getStartX of view.
					* */
					mWmParams.x = (int) (mRawX - mStartViewX - mSurfaceViewMarginLeft);
					mWmParams.y = (int) (mRawY - mStartViewY - mSurfaceViewMarginTop);
					mWindowManager.updateViewLayout(mFloatLayout, mWmParams);
				}
				arriveSides();
				break;
			case MotionEvent.ACTION_UP:
				mScreenFingerCount = 0;
				float xDistance = Math.abs(mRawX - mLastRawX);
				float yDistance = Math.abs(mRawY - mLastRawY);
				boolean mIsClick;
				if (xDistance < FINGER_MAX_CLICK_ZONE || yDistance < FINGER_MAX_CLICK_ZONE) {
					VideoLog.i(TAG, "On touch: it is click event.");
					mIsClick = true;
				} else {
					mIsClick = false;
				}
				return !mIsClick;
			case MotionEvent.ACTION_POINTER_UP:
				mScreenFingerCount -= 1;
				break;
			default:
				break;
		}
		return false;
	}

	/**
	 * the distance as small as possible, because it will be effect
	 * you touch event in move process.
	 *
	 * @param event touch event.
	 * @return true if user want to scale window.
	 */
	private boolean calculateDistance(MotionEvent event) {
		boolean shouldResize = false;
		float newDist = distanceBetweenPoints(event);
		if (Math.abs(newDist - lastDistance) > 10) {
			VideoLog.d(TAG, "calculateDistance distance is: " + Math.abs(newDist - lastDistance));
			scaleScreen(newDist / lastDistance);
			lastDistance = newDist;
			shouldResize = true;
		}
		return shouldResize;
	}

	private float distanceBetweenPoints(MotionEvent event) {
		float x = event.getX(0) - event.getX(1);
		float y = event.getY(0) - event.getY(1);
		return (float) Math.sqrt((x * x) + (y * y));
	}

	private void scaleScreen(float f) {
		VideoLog.d(TAG, "scaleScreen rate is :" + f);
		mWmParams.height *= f;
		mWmParams.width *= f;
	}

	/**
	 * check the window is out of screen to much.
	 */
	private void arriveSides() {
		if (mWmParams.x < (0 - mWmParams.width * FLOAT_WINDOW_LEFT_RIGHT_SIDE)) {
			mWmParams.x = (int) (0 - mWmParams.width * FLOAT_WINDOW_LEFT_RIGHT_SIDE);
		}
		if (mWmParams.x > (mScreenSize.x - mWmParams.width * FLOAT_WINDOW_LEFT_RIGHT_SIDE)) {
			mWmParams.x = (int) (mScreenSize.x - mWmParams.width * FLOAT_WINDOW_LEFT_RIGHT_SIDE);
		}
		if (mWmParams.y < (0 - mWmParams.height * FLOAT_WINDOW_TOP_BOTTOM_SIDE)) {
			mWmParams.y = (int) (0 - mWmParams.height * FLOAT_WINDOW_TOP_BOTTOM_SIDE);
		}
		if (mWmParams.y > (mScreenSize.y - mWmParams.height * FLOAT_WINDOW_TOP_BOTTOM_SIDE)) {
			mWmParams.y = (int) (mScreenSize.y - mWmParams.height * FLOAT_WINDOW_TOP_BOTTOM_SIDE);
		}
		mWindowManager.updateViewLayout(mFloatLayout, mWmParams);
	}

	/**
	 * to set window's size.
	 * it will be called when screen scale and init.
	 */
	private void updateWindowSize() {
		// if video's width is bigger than height.
		// the window's size will be reset to min size.
		if (mVideoEntity.getWidth() <= mVideoEntity.getHeight()) {
			if (isFistIn) {
				mWmParams.height = (int) (mScreenSize.y * FLOAT_WINDOW_HEIGHT_MIN_RATE);
				mWmParams.width = (int) (mScreenSize.x * FLOAT_WINDOW_WIDTH_MIN_RATE);
				updateWindowPosition();
				mWindowManager.updateViewLayout(mFloatLayout, mWmParams);
			}
		}
		resizeWindow();
	}

	/**
	 * set the window size max or min.
	 */
	private void resizeWindow() {
		if (isFistIn) {
			isFistIn = false;
			return;
		}
		VideoLog.v(TAG, "mWmParams.height = " + mWmParams.height + "mWmParams.WIDTH = " + mWmParams.width);
		// horizontal
		if (mVideoEntity.getWidth() > mVideoEntity.getHeight()) {
			if (mWmParams.height > mScreenSize.x * FLOAT_WINDOW_WIDTH_MAX_RATE) {
				mWmParams.height = (int) (mScreenSize.x * FLOAT_WINDOW_WIDTH_MAX_RATE);
				mWmParams.width = (int) (mScreenSize.y * FLOAT_WINDOW_HEIGHT_MAX_RATE);
			}
			if (mWmParams.width < mScreenSize.y * FLOAT_WINDOW_HEIGHT_MIN_RATE) {
				mWmParams.width = (int) (mScreenSize.y * FLOAT_WINDOW_HEIGHT_MIN_RATE);
				mWmParams.height = (int) (mScreenSize.x * FLOAT_WINDOW_WIDTH_MIN_RATE);
			}
		} else if (mVideoEntity.getWidth() < mVideoEntity.getHeight()) { /*vertical*/
			if (mWmParams.width > mScreenSize.x * FLOAT_WINDOW_WIDTH_MAX_RATE) {
				mWmParams.width = (int) (mScreenSize.x * FLOAT_WINDOW_WIDTH_MAX_RATE);
				mWmParams.height = (int) (mScreenSize.y * FLOAT_WINDOW_HEIGHT_MAX_RATE);
			}
			if (mWmParams.height < mScreenSize.y * FLOAT_WINDOW_WIDTH_MIN_RATE) {
				mWmParams.height = (int) (mScreenSize.y * FLOAT_WINDOW_HEIGHT_MIN_RATE);
				mWmParams.width = (int) (mScreenSize.x * FLOAT_WINDOW_WIDTH_MIN_RATE);
			}
		}
		VideoLog.v(TAG, "mWmParams.left = " + mWmParams.x + "mWmParams.top = " + mWmParams.y);
		mWindowManager.updateViewLayout(mFloatLayout, mWmParams);
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		mSeekBarDragging = true;
		hideTmpImage();
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		mSeekBarDragging = false;
		mMediaPlayerControl.seekTo(seekBar.getProgress() * TIME_MILLISECOND_UNIT);
		if (mMediaPlayerControl.isPlaying()) {
			mMediaPlayerControl.start();
			mVideoPlayHandler.sendEmptyMessage(MEDIA_PLAYER_PROGRESS_UPDATE);
		}
	}

	@Override
	public void initSomeSelfAtPrepared() {
		initViewData();
		updateWindowSize();
		//play();
	}

	private void initViewData() {
		long during = mVideoEntity.getDuring();
		mVideoFloatingSeekBar.setMax((int) (during / TIME_MILLISECOND_UNIT));
		long currentPosition = mVideoEntity.getCurrentPosition();
		mVideoFloatingSeekBar.setProgress((int) (currentPosition / TIME_MILLISECOND_UNIT));
		if (currentPosition >= during) {
			mTmpImageView.setVisibility(View.VISIBLE);
			mTmpImageView.setImageBitmap(mMediaPlayerControl.getDisplayBitmap());
		} else {
			mTmpImageView.setVisibility(View.GONE);
			mMediaPlayerControl.seekTo((int) currentPosition);
		}
		if(mVideoEntity.isPlaying()){
			mMediaPlayerControl.start();
		}
	}

	/**
	 * set the resume or pause image according to media player state automatically.
	 */
	public void setResumePlayImage() {
		if (mMediaPlayerControl.isPlaying()) {
			mResumePauseImageView.setImageResource(R.drawable.floating_video_play_pause_window);
		} else {
			mResumePauseImageView.setImageResource(R.drawable.floating_video_play_play_window);
		}
	}

	@Override
	public int setProgress() {
		if (mMediaPlayerControl == null || mSeekBarDragging) {
			return 0;
		}
		int position = mMediaPlayerControl.getCurrentPosition();
		int duration = mMediaPlayerControl.getDuration();
		if (mVideoFloatingSeekBar != null) {
			if (duration > 0) {
				mVideoFloatingSeekBar.setProgress(position / TIME_MILLISECOND_UNIT);
				VideoLog.i(TAG, position + " at setProgress(), myProcess is: " + mVideoFloatingSeekBar.getProgress());
			}
			int percent = mMediaPlayerControl.getBufferPercentage();
			mVideoFloatingSeekBar.setSecondaryProgress(percent * 10);
		}
		return position;
	}

	@Override
	public void fullProgress() {
		mVideoPlayHandler.removeMessages(MEDIA_PLAYER_PROGRESS_UPDATE);
		mVideoFloatingSeekBar.setProgress((int) (mVideoEntity.getDuring() / TIME_MILLISECOND_UNIT) + 1);
		mResumePauseImageView.setImageResource(R.drawable.floating_video_play_play_window);
		mVideoEntity.setCurrentPosition(mVideoEntity.getDuring());
		mVideoEntity.setPlaying(false);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
	}
}
