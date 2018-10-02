package com.lintan.videoplayer.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.lintan.videoplayer.R;
import com.lintan.videoplayer.activity.base.VideoPlayerBaseActivity;
import com.lintan.videoplayer.bean.VideoEntity;
import com.lintan.videoplayer.service.FloatingWindowService;
import com.lintan.videoplayer.util.CommonUtil;
import com.lintan.videoplayer.util.cache.ImageDrawableCache;
import com.lintan.videoplayer.util.log.VideoLog;
import com.lintan.videoplayer.util.media.MyMediaControlCenter;
import com.lintan.videoplayer.util.media.MyMediaControlCenter.ControllerCallBack;

import java.lang.ref.WeakReference;

/**
 *
 */
public class VideoPlayActivity extends VideoPlayerBaseActivity
		implements View.OnTouchListener, SeekBar.OnSeekBarChangeListener, View.OnClickListener,
		           ControllerCallBack {

	public static final String CURRENT_VIDEO_PATH = "video_path";
	private static final String TAG = "VideoPlayer/VideoPlayActivity";
	private static final int TIME_MILLISECOND_UNIT = 1000;
	private static final int PAUSE_IMAGE_SHOW_MIN_TIME = 2000;
	private static final int ALERT_PERMISSION_REQUEST_CODE = 0x0001;
	private static final int PERCENT_SWITCH = 100;
	private static final int HANDLER_MESSAGE_DELAY = 10;

	private static final String PAUSE = "pause";
	private static final String RESUME = "resume";
	private static final int RESUME_PAUSE_IMAGE_PREFIX_ID = R.drawable.video_play_video_pause;

	private static final int MEDIA_PLAYER_PROGRESS_UPDATE = 0x0001;
	private static final int MEDIA_PLAYER_RP_IMAGE_HIDE = 0x0002;
	private static final int MEDIA_PLAYER_RP_IMAGE_HIDE_TOUCH = 0x0003;

	/**
	 * use surface view to play video.
	 */
	private SurfaceView mVideoSurfaceView;
	private ImageView mTmpImageView;
	private ImageView mResumePlayImageView;
	private ImageView mMiniWindowImageView;
	//private ImageButton mMiniWindowImageView;

	private LinearLayout mBottomLinearLay;
	private SeekBar mDuringSeekBar;
	private TextView mDuringStartTextView;
	private TextView mDuringEndTextView;

	// add title
	private TextView mVideoPlayTitle;

	private MediaPlayer mMediaPlayer;
	private SurfaceHolder mSurfaceHolder;
	//private MediaPlayerControl mMediaPlayerControl;
	private MyMediaControlCenter mMediaPlayerControl;
	//private PlayState mPlayState;// = PlayState.PAUSE;
	private PlayState mTargetState;// = PlayState.PAUSE;

	//private int mCurrentPosition;

	private boolean mIsFullScreen = true;
	private boolean mSeekBarIsShowing;
	private boolean mDragging;
	private boolean mDuringSeekBarShowing;
	// floating window.
	public static final String INTENT_FROM = "intentFrom";
	private boolean mHasWindowAlertPermission = false;
	private Intent mIntentFrom;

	private enum PlayState {
		PLAYING, PAUSE
	}

	private VideoPlayHandler mVideoPlayHandler;
	// modify because object
	private VideoEntity mVideoEntity;

	// screen off and on
	private ScreenStatusReceiver mScreenStatusReceiver;
	private String SCREEN_ON = "android.intent.action.SCREEN_ON";
	private String SCREEN_OFF = "android.intent.action.SCREEN_OFF";
	private String USER_PRESENT = "android.intent.action.USER_PRESENT";

	private class ScreenStatusReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (SCREEN_ON.equals(intent.getAction())) {
				VideoLog.e(TAG, "screen on, entity current = " + mVideoEntity.getCurrentPosition());
			} else if (SCREEN_OFF.equals(intent.getAction())) {
				VideoLog.e(TAG, "screen off, be careful, entity current = " + mVideoEntity.getCurrentPosition());
				mMediaPlayerControl.pause();
			} else if (USER_PRESENT.equals(intent.getAction())) {
				VideoLog.e(TAG, "ACTION_USER_PRESENT," + mVideoEntity.getCurrentPosition());
				if (mVideoEntity.isPlaying()) {
					mMediaPlayerControl.start();
				}
			}
		}
	}

	private void registerScreenStatusReceiver() {
		mScreenStatusReceiver = new ScreenStatusReceiver();
		IntentFilter screenStatusIF = new IntentFilter();
		screenStatusIF.addAction(SCREEN_ON);
		screenStatusIF.addAction(SCREEN_OFF);
		screenStatusIF.addAction(USER_PRESENT);

		registerReceiver(mScreenStatusReceiver, screenStatusIF);
	}

	private void unRegisterScreenStatusReceiver() {
		unregisterReceiver(mScreenStatusReceiver);
	}
	// add screen end

	private static class VideoPlayHandler extends Handler {
		VideoPlayActivity activity;
		WeakReference<VideoPlayActivity> activityWeakReference;

		VideoPlayHandler(VideoPlayActivity activity) {
			activityWeakReference = new WeakReference<>(activity);
		}

		@Override
		public void handleMessage(Message msg) {
			int what = msg.what;
			activity = activityWeakReference.get();
			if (activity == null) {
				return;
			}
			switch (what) {
				case MEDIA_PLAYER_PROGRESS_UPDATE:
					long pro = activity.setProgress();
					activity.mVideoEntity.setCurrentPosition(pro);
					VideoLog.d(TAG, "handler MEDIA_PLAYER_PROGRESS_UPDATE, setProcess() = " + pro);
					activity.setResumePlayImage();
					if (!activity.mDragging && activity.mDuringSeekBarShowing
					    && activity.mVideoEntity.isPlaying()) {

						activity.mMediaPlayerControl.sendUpdateProgressMessage();
						activity.mVideoEntity.setPlaying(true);
					} else if (!activity.mVideoEntity.isPlaying()) {
						// because stop and restart, the app should save current sate.
						activity.mVideoEntity.setCurrentPosition(activity.mMediaPlayerControl.getCurrentPosition());
						activity.mVideoEntity.setPlaying(false);
						VideoLog.e(TAG, "handler MEDIA_PLAYER_PROGRESS_UPDATE, entity's current position"
						                + activity.mVideoEntity.getCurrentPosition());
					}
					break;
				case MEDIA_PLAYER_RP_IMAGE_HIDE:
					activity.hidePauseResumeImage();
					break;
				default:
					break;
			}
			super.handleMessage(msg);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setWindowsTheme(this);
		fullScreen();
		setContentView(R.layout.activity_video_play);
		setFiledFromIntent();
		initHandler();
		initView();
		initControls();
		registerScreenStatusReceiver();
	}

	private void setWindowsTheme(Activity activity) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			Window window = activity.getWindow();

			window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
			                  | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);

			window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
			                                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
			                                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
			                                            | View.SYSTEM_UI_FLAG_IMMERSIVE
			                                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
			window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
			window.setStatusBarColor(Color.TRANSPARENT);
			window.setNavigationBarColor(Color.TRANSPARENT);
		}
	}

	private void fullScreen() {
		if (mIsFullScreen) {
			hideSeekBarAndFullScreen();
			WindowManager.LayoutParams params = getWindow().getAttributes();
			params.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
			getWindow().setAttributes(params);
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
		} else {
			showSeekBarAndFullScreen();
			WindowManager.LayoutParams params = getWindow().getAttributes();
			params.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
			getWindow().setAttributes(params);
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
		}
		mIsFullScreen = !mIsFullScreen;
	}

	private void hideSeekBarAndFullScreen() {
		mSeekBarIsShowing = false;
		if (null != mBottomLinearLay) {
			mBottomLinearLay.setVisibility(View.GONE);
			mVideoPlayTitle.setVisibility(View.GONE);
		}
	}

	private void showSeekBarAndFullScreen() {
		mSeekBarIsShowing = true;
		if (null != mBottomLinearLay) {
			mBottomLinearLay.setVisibility(View.VISIBLE);
			mVideoPlayTitle.setVisibility(View.VISIBLE);
		}
	}

	private void setFiledFromIntent() {
		mIntentFrom = getIntent();

		mVideoEntity = mIntentFrom.getBundleExtra(VideoPlayerBaseActivity.ENTITY_OBJ_BUNDLE_TAG)
		                          .getParcelable(VideoPlayerBaseActivity.ENTITY_OBJ_TAG);
		if (null == mVideoEntity) {
			throw new IllegalArgumentException("VideoEntity is null, you must add it in your intent.");
		}
		int mCurrentPosition = (int) mVideoEntity.getCurrentPosition();
		VideoLog.d(TAG, "setFiledFromIntent: mCurrentPosition :" + mCurrentPosition);
	}

	private void initHandler() {
		mVideoPlayHandler = new VideoPlayHandler(this);
	}

	private void initView() {
		//mCustomVideoView = (CustomVideoView) findViewById(R.id.custom_video_view_video_play);
		mVideoSurfaceView = (SurfaceView) findViewById(R.id.surface_view_video_play);
		mTmpImageView = (ImageView) findViewById(R.id.video_play_tmp_image_view);

		mVideoPlayTitle = (TextView) findViewById(R.id.video_play_video_title);

		mBottomLinearLay = (LinearLayout) findViewById(R.id.video_play_during_ll);
		mResumePlayImageView = (ImageView) findViewById(R.id.video_play_video_pause_or_resume);
		mMiniWindowImageView = (ImageView) findViewById(R.id.video_play_mini_window);
		//mMiniWindowImageView = (ImageButton) findViewById(R.id.video_play_mini_window);
		mDuringStartTextView = (TextView) findViewById(R.id.video_play_during_ll_start);
		mDuringEndTextView = (TextView) findViewById(R.id.video_play_during_ll_end);
		mDuringSeekBar = (SeekBar) findViewById(R.id.video_play_during_seek_ll_bar);
		mSeekBarIsShowing = false;
		mVideoPlayTitle.setText(mVideoEntity.getName());
	}

	private void initControls() {
		VideoLog.w(TAG, "initControls() called, current position = " + mVideoEntity.getCurrentPosition());

		mMediaPlayer = new MediaPlayer();
		mSurfaceHolder = mVideoSurfaceView.getHolder();
		mMediaPlayerControl = new MyMediaControlCenter(mMediaPlayer, mVideoEntity, mVideoPlayHandler);
		// add begin
		//mMediaPlayerControl = new MyMediaControlCenter(mMediaPlayer, mVideoEntity, mVideoPlayHandler);
		mMediaPlayerControl.setControllerCallBack(this);
		mMediaPlayer.setOnPreparedListener(mMediaPlayerControl);
		mMediaPlayer.setOnCompletionListener(mMediaPlayerControl);
		mMediaPlayer.setOnErrorListener(mMediaPlayerControl);
		mSurfaceHolder.addCallback(mMediaPlayerControl);
		// add end

		mVideoSurfaceView.setOnTouchListener(this);
		mResumePlayImageView.setOnClickListener(this);
		mMiniWindowImageView.setOnClickListener(this);
		mDuringSeekBar.setOnSeekBarChangeListener(this);
		mDuringSeekBarShowing = true;
	}

	@Override
	protected void onStart() {
		VideoLog.v(TAG, "play activity onStart----");
		super.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onPause() {
		mVideoPlayHandler.removeMessages(MEDIA_PLAYER_PROGRESS_UPDATE);
		super.onPause();
	}

	@Override
	protected void onStop() {
		VideoLog.e(TAG,
		           "onStop---" + mVideoEntity.getName()
		           + "currentposition = " + mVideoEntity.getCurrentPosition());

		super.onStop();
	}

	@Override
	protected void onDestroy() {
		mMediaPlayerControl.stop();
		mSurfaceHolder.removeCallback(mMediaPlayerControl);
		unRegisterScreenStatusReceiver();
		super.onDestroy();
	}

	@Override
	public void onBackPressed() {
		String from = mIntentFrom.getStringExtra(INTENT_FROM);
		// when user back from mini window, we will going to video center.
		if (from.equals(FloatingWindowService.class.getName())) {
			startActivity(VideoCenterActivity.class, mVideoEntity);
		}
		finish();
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				if (v.getId() == mVideoSurfaceView.getId()) {
					fullScreen();
					mResumePlayImageView.setVisibility(View.VISIBLE);
					showImageOnTouch();
				}
				break;
			default:
				break;
		}

		return true;
	}

	/**
	 * on touch, set the image we want to see.
	 */
	private void showImageOnTouch() {
		mResumePlayImageView.setVisibility(View.VISIBLE);
		if (mMediaPlayerControl.isPlaying()) {
			BitmapDrawable pause = ImageDrawableCache.getInstance(this).getBitmapFromMemoryCache(
					RESUME_PAUSE_IMAGE_PREFIX_ID + PAUSE);
			if (null == pause) {
				VideoLog.w(TAG, "pause is null , at showImageOnTouch(), to load a image add to cache.");
				pause = new BitmapDrawable(getResources(), BitmapFactory.decodeResource(
						getResources(), R.drawable.video_play_video_pause));
				ImageDrawableCache.getInstance(this).addBitmapToMemoryCache(
						RESUME_PAUSE_IMAGE_PREFIX_ID + PAUSE, pause);
				mResumePlayImageView.setImageDrawable(pause);
			}
			mVideoPlayHandler.sendEmptyMessageDelayed(MEDIA_PLAYER_RP_IMAGE_HIDE, PAUSE_IMAGE_SHOW_MIN_TIME);
		}
	}

	private void initViewData() {
		long during = mVideoEntity.getDuring();
		long currentPosition = mVideoEntity.getCurrentPosition();
		mDuringSeekBar.setMax((int) (during / TIME_MILLISECOND_UNIT));

		VideoLog.e(TAG, "initViewData -- mVideoEntity.getCurrentPosition()" + currentPosition);
		mDuringStartTextView.setText(CommonUtil.stringForTime(currentPosition));
		mDuringEndTextView.setText(CommonUtil.stringForTime(during));
		mDuringSeekBar.setProgress((int) (currentPosition / TIME_MILLISECOND_UNIT));
	}

	private void setEntityWidthAndHeight() {
		mVideoEntity.setWidth(mMediaPlayerControl.getVideoWidth());
		mVideoEntity.setHeight(mMediaPlayerControl.getVideoHeight());
		setScreenOrientation();
	}

	private void setScreenOrientation() {
		VideoLog.d(TAG, "setScreenOrientation() in VideoPlayActivity");
		if (mVideoEntity.getWidth() > mVideoEntity.getHeight()) {
			if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
				VideoLog.i(TAG, "going to set SCREEN_ORIENTATION_LANDSCAPE");
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			}
		} else {
			if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
				VideoLog.i(TAG, "going to set SCREEN_ORIENTATION_PORTRAIT");
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			}
		}
	}

	private void checkIfNeedCover() {
		long during = mVideoEntity.getDuring();
		long currentPosition = mVideoEntity.getCurrentPosition();
		VideoLog.d(TAG, "at checkIfNeedCover() " + currentPosition + " total = " + during);
		if (currentPosition >= during) {
			mTmpImageView.setVisibility(View.VISIBLE);
			mDuringSeekBar.setProgress((int) (during / TIME_MILLISECOND_UNIT));
			mTmpImageView.setImageBitmap(mMediaPlayerControl.getDisplayBitmap());
		} else {
			mTmpImageView.setVisibility(View.GONE);
			mMediaPlayerControl.seekTo((int) currentPosition);
			// if pause at pause. now we don't need play
			if (mVideoEntity.isPlaying()) {
				mResumePlayImageView.setImageResource(R.drawable.video_play_video_pause);
				mMediaPlayerControl.start();
				VideoLog.e(TAG, "in play activity at checkIfNeedCover call start()");
			} else {
				mResumePlayImageView.setImageResource(R.drawable.video_play_video_resume);
			}
		}
	}

	@Override
	public void initSomeSelfAtPrepared() {
		initViewData();
		setEntityWidthAndHeight();
		checkIfNeedCover();
	}

	/**
	 * set progress of seek bar and current time text and total time text.
	 *
	 * @return current position of media player
	 */
	@Override
	public int setProgress() {
		if (mMediaPlayerControl == null || mDragging) {
			return 0;
		}
		if (!mMediaPlayerControl.isPlaying()) {
			return 0;
		}
		int position = mMediaPlayerControl.getCurrentPosition();
		int duration = mMediaPlayerControl.getDuration();
		if (mDuringSeekBar != null) {
			if (duration > 0) {
				mDuringSeekBar.setProgress(position / TIME_MILLISECOND_UNIT);
				VideoLog.d(TAG, position + " at setProgress(), mDuringSeekBar's Process is: " + mDuringSeekBar
						.getProgress());
			}
			int percent = mMediaPlayerControl.getBufferPercentage();
			mDuringSeekBar.setSecondaryProgress(percent * 10);
		}

		if (mDuringEndTextView != null) {
			mDuringEndTextView.setText(CommonUtil.stringForTime(duration));
		}
		if (mDuringStartTextView != null) {
			mDuringStartTextView.setText(CommonUtil.stringForTime(position));
		}
		return position;
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
	}

	@Override
	public void fullProgress() {
		VideoLog.e(TAG, "fullProgress by app check.");
		mDuringSeekBar.setProgress((int) (mVideoEntity.getDuring() / TIME_MILLISECOND_UNIT) + 1);
		mVideoEntity.setCurrentPosition(mVideoEntity.getDuring());
		mVideoEntity.setPlaying(false);
		mVideoPlayHandler.removeMessages(MEDIA_PLAYER_PROGRESS_UPDATE);
		mResumePlayImageView.setImageResource(R.drawable.video_play_video_resume);
	}

	@Override
	public void hideTmpImage() {
		if (mTmpImageView.getVisibility() == View.VISIBLE) {
			mTmpImageView.setVisibility(View.GONE);
		}
	}

	private void hidePauseResumeImage() {
		mResumePlayImageView.setVisibility(View.GONE);
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		if (!mMediaPlayerControl.canSeekBackward() || !mMediaPlayerControl.canSeekForward()) {
			return;
		}
		if (mDuringStartTextView != null)
			mDuringStartTextView.setText(CommonUtil.stringForTime((TIME_MILLISECOND_UNIT * progress)));
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		mDragging = true;
		hideTmpImage();
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		mDragging = false;
		VideoLog.v(TAG, "mDuringSeekBar onStopTrackingTouch-");
		mMediaPlayerControl.seekTo(seekBar.getProgress() * TIME_MILLISECOND_UNIT);
		mVideoPlayHandler.sendEmptyMessageDelayed(MEDIA_PLAYER_RP_IMAGE_HIDE, HANDLER_MESSAGE_DELAY);
		if (mMediaPlayerControl.isPlaying()) {
			mVideoPlayHandler.sendEmptyMessage(MEDIA_PLAYER_PROGRESS_UPDATE);
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.video_play_video_pause_or_resume:
				VideoLog.e(TAG, "video_play_video_pause_or_resume click.");
				pauseResumeImageClick();
				hideTmpImage();
				break;
			case R.id.video_play_mini_window:
				VideoLog.w(TAG, "video_play_mini_window click, to start start service.");
				if (mHasWindowAlertPermission) {
					restScreenOrientationPortrait();
					//restScreenOrientationLandscape();
					startService(FloatingWindowService.class, mVideoEntity, true);
				} else {
					requestAlertWindowPermission();
				}
				break;
			default:
				break;
		}
	}

	/**
	 * to start a mini window, we must reset the orientation that get
	 * the screen size.
	 * <p/>
	 * reset the orientation vertical(PORTRAIT)
	 */
	private void restScreenOrientationPortrait() {
		if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
			VideoLog.i(TAG, "going to set SCREEN_ORIENTATION_PORTRAIT");
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}
	}
	private void restScreenOrientationLandscape() {
		if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
			VideoLog.i(TAG, "going to set SCREEN_ORIENTATION_PORTRAIT");
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		}
	}

	/**
	 * the resume or pause button is clicked by user,
	 * we set a different image to the widget from cache.
	 * <p/>
	 * <note> if the image from cache is null, it will new object and put
	 * it in that cache.
	 */
	private void pauseResumeImageClick() {
		ImageDrawableCache cache = ImageDrawableCache.getInstance(this);

		// because the cache key is a string path value, so, we use a drawable id as a prefix and it's function as a
		// end flag.
		BitmapDrawable pause = cache.getBitmapFromMemoryCache(RESUME_PAUSE_IMAGE_PREFIX_ID + PAUSE);
		BitmapDrawable resume = cache.getBitmapFromMemoryCache(RESUME_PAUSE_IMAGE_PREFIX_ID + RESUME);
		if (null == pause) {
			Resources resources = getResources();
			pause = new BitmapDrawable(resources, BitmapFactory.decodeResource(resources,
			                                                                   R.drawable
					                                                                   .video_play_video_pause));
			cache.addBitmapToMemoryCache(RESUME_PAUSE_IMAGE_PREFIX_ID + PAUSE, pause);
		}
		if (null == resume) {
			Resources resources = getResources();
			resume = new BitmapDrawable(resources, BitmapFactory.decodeResource(resources,
			                                                                    R.drawable
					                                                                    .video_play_video_resume));
			cache.addBitmapToMemoryCache(RESUME_PAUSE_IMAGE_PREFIX_ID + RESUME, resume);
		}
		if (mMediaPlayerControl.isPlaying()) {
			mTargetState = PlayState.PAUSE;
			//showSeekBarAndFullScreen();
			//fullScreen();
			mMediaPlayerControl.pause();
			mVideoEntity.setPlaying(false);
			//mPlayState = PlayState.PAUSE;
			mVideoPlayHandler.removeMessages(MEDIA_PLAYER_RP_IMAGE_HIDE);
			mResumePlayImageView.setImageDrawable(resume);
			VideoLog.i(TAG, "pauseResumeImageClick(), we remove the message to display resume image");
		} else {
			mTargetState = PlayState.PLAYING;
			mVideoEntity.setPlaying(true);
			//hideSeekBarAndFullScreen();
			mResumePlayImageView.setImageDrawable(pause);
			mResumePlayImageView.setVisibility(View.GONE);
			if (mVideoEntity.getCurrentPosition() >= mVideoEntity.getDuring()) {
				mMediaPlayerControl.seekTo(0);
				mVideoEntity.setCurrentPosition(0);
			}
			mMediaPlayerControl.start();
			//mPlayState = PlayState.PLAYING;
		}
		fullScreen();
		VideoLog.d(TAG, "pause is null? " + pause + ", resume is null? " + resume);
	}

	/**
	 * set the resume or pause image according to media player state automatically.
	 */
	public void setResumePlayImage() {
		if (mMediaPlayerControl.isPlaying()) {
			mResumePlayImageView.setImageResource(R.drawable.video_play_video_pause);
		} else {
			mResumePlayImageView.setImageResource(R.drawable.video_play_video_resume);
		}
	}

	private void requestAlertWindowPermission() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
			mHasWindowAlertPermission = true;
			restScreenOrientationPortrait();
			//restScreenOrientationLandscape();
			startService(FloatingWindowService.class, mVideoEntity, true);
			VideoLog.d(TAG, "requestAlertWindowPermission(), already grated permission");
			VideoLog.i(TAG, "to start start service, and entity's w&h is: " +
			                mVideoEntity.getWidth() + "x" + mVideoEntity.getHeight());
		} else {
			VideoLog.d(TAG, "requestAlertWindowPermission(), no permission granted");
			Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
			intent.setData(Uri.parse("package:" + getPackageName()));
			startActivityForResult(intent, ALERT_PERMISSION_REQUEST_CODE);
		}
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == ALERT_PERMISSION_REQUEST_CODE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (Settings.canDrawOverlays(this)) {
				VideoLog.i(TAG, "onActivityResult(), granted permission and start service.");
				restScreenOrientationPortrait();
				mHasWindowAlertPermission = true;
				startService(FloatingWindowService.class, mVideoEntity, true);
			} else {
				mHasWindowAlertPermission = false;
				VideoLog.i(TAG, "onActivityResult(), denied permission and finish itself.");
				finish();
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}
}
