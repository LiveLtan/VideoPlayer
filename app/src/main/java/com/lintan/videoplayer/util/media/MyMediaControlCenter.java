package com.lintan.videoplayer.util.media;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Message;
import android.view.SurfaceHolder;
import android.widget.MediaController;

import com.lintan.videoplayer.bean.VideoEntity;
import com.lintan.videoplayer.util.log.VideoLog;

import java.io.IOException;
// const value
import static com.lintan.videoplayer.service.FloatingWindowService.MEDIA_PLAYER_PROGRESS_UPDATE;
import static com.lintan.videoplayer.service.FloatingWindowService.MEDIA_PLAYER_READY;

/**
 * common work class.
 * Created by Administrator on 2016/9/15.
 */
public class MyMediaControlCenter
		implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener,
		           SurfaceHolder.Callback,
		           MediaController.MediaPlayerControl {

	private static final String TAG = "VideoPlayer/MyMediaControlCenter";

	private static final int TIME_MILLISECOND_UNIT = 1000;
	private static final int PERCENT_SWITCH = 100;
	private static final int PREPARE_CHECK_DELAY = 1000;

	private MediaPlayer mMediaPlayer;
	private VideoEntity mVideoEntity;

	private static final int MEDIA_PLAYER_RP_IMAGE_HIDE = 0x0002;
	private Handler mVideoPlayHandler;
	private int mMediaDuration = 0;
	private boolean mPlayerPrepared = false;

	private Runnable checkPrepared = new Runnable() {
		@Override
		public void run() {
			VideoLog.w(TAG, "check runnable");
			if (mPlayerPrepared) {
				mVideoPlayHandler.removeCallbacks(this);
				mVideoPlayHandler.sendEmptyMessage(MEDIA_PLAYER_READY);
			} else {
				mVideoPlayHandler.postDelayed(this, PREPARE_CHECK_DELAY);
			}
		}
	};

	public interface ControllerCallBack {
		/**
		 * extra work if you need.
		 */
		void initSomeSelfAtPrepared();

		/**
		 * to set the progress value.
		 *
		 * @return current position.
		 */
		int setProgress();

		/**
		 * some time progress goes wrong.
		 */
		void fullProgress();

		/**
		 * called when surface created, some time, the video comes to end.
		 * we want to draw a bitmap on surface holder, but not work.
		 *
		 * @param holder surface holder like a canvas.
		 */
		void surfaceCreated(SurfaceHolder holder);

		void hideTmpImage();
	}

	private ControllerCallBack controllerCallBack;

	public MyMediaControlCenter() {

	}

	public MyMediaControlCenter(MediaPlayer mediaPlayer, VideoEntity videoEntity, Handler handler) {
		mMediaPlayer = mediaPlayer;
		mVideoEntity = videoEntity;
		mVideoPlayHandler = handler;
		VideoLog.v(TAG, "MyMediaControlCenter init -- " + mVideoEntity.getCurrentPosition());
		mPlayerPrepared = false;
		try {
			mMediaPlayer.setDataSource(videoEntity.getData());
			mMediaPlayer.prepareAsync();
		} catch (IOException e) {
			VideoLog.e(TAG, "MyMediaControlCenter init exception:" + e);
		}
	}

	public void setControllerCallBack(ControllerCallBack setting) {
		controllerCallBack = setting;
	}

	public int getVideoWidth() {
		return mMediaPlayer.getVideoWidth();
	}

	public int getVideoHeight() {
		return mMediaPlayer.getVideoHeight();
	}

	public Bitmap getDisplayBitmap() {
		String videoPath = mVideoEntity.getData();
		MediaMetadataRetriever mMediaMetadataRetriever = new MediaMetadataRetriever();
		mMediaMetadataRetriever.setDataSource(videoPath);

		Bitmap videoFrame = mMediaMetadataRetriever.getFrameAtTime();
		mMediaMetadataRetriever.release();
		return videoFrame;
	}

	public void sendUpdateProgressMessage() {
		VideoLog.v(TAG, "sendUpdateProgressMessage, current = " + mVideoEntity.getCurrentPosition());
		Message update = mVideoPlayHandler.obtainMessage(MEDIA_PLAYER_PROGRESS_UPDATE);
		mVideoPlayHandler
				.sendMessageDelayed(update, (TIME_MILLISECOND_UNIT - (mVideoEntity.getCurrentPosition()
				                                                      % TIME_MILLISECOND_UNIT)));
	}

	/**
	 * stop play and release the player.
	 */
	public void stop() {
		if (null != mMediaPlayer) {
			VideoLog.i(TAG, "media player going to release, because you called stop method.");
			mVideoPlayHandler.removeMessages(MEDIA_PLAYER_PROGRESS_UPDATE);
			mVideoPlayHandler.removeCallbacks(checkPrepared);
			mMediaPlayer.stop();
			mMediaPlayer.release();
			mMediaPlayer = null;
		}
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		VideoLog.v(TAG, "onPrepared(), video file path is " + mVideoEntity);
		mMediaDuration = mp.getDuration();
		mPlayerPrepared = true;
		if (null != controllerCallBack) {
			controllerCallBack.initSomeSelfAtPrepared();
		}
	}

	// play completed
	@Override
	public void onCompletion(MediaPlayer mp) {
		VideoLog.e(TAG, "player onCompletion. current position = " + mVideoEntity.getCurrentPosition());
		long currentPosition = mVideoEntity.getCurrentPosition();
		if (null != controllerCallBack && currentPosition > 0) {
			controllerCallBack.fullProgress();
		}
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		VideoLog.e(TAG, "onError(), " + what + ", extra = " + extra);
		mp.reset();
		return false;
	}

	// callbacks
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		VideoLog.d(TAG, "surfaceCreated(), video file path is " + mVideoEntity.getData());
		mMediaPlayer.setDisplay(holder);
		controllerCallBack.surfaceCreated(holder);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		VideoLog.i(TAG, " surfaceChanged in control, width = " + width + " height = " + height);
		mMediaPlayer.setDisplay(holder);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		VideoLog.w(TAG, "surfaceDestroyed().");
		if (null != mMediaPlayer) {
			VideoLog.d(TAG, "surfaceDestroyed(), and to release in control by automatically.");
			mVideoPlayHandler.removeMessages(MEDIA_PLAYER_PROGRESS_UPDATE);
			mVideoPlayHandler.removeCallbacks(checkPrepared);
			mMediaPlayer.stop();
		}
	}

	// media control
	@Override
	public void start() {
		long current = mVideoEntity.getCurrentPosition();
		VideoLog.d(TAG, "MediaPlayerControl start play. current = " + current);
		if (null == mMediaPlayer) {
			return;
		}
		if (!mPlayerPrepared) {
			mVideoPlayHandler.postDelayed(checkPrepared, PREPARE_CHECK_DELAY);
			return;
		}
		mMediaPlayer.seekTo((int) mVideoEntity.getCurrentPosition());
		mMediaPlayer.start();
		VideoLog.d(TAG, "MediaPlayerControl start play. current = " + current);
		mVideoPlayHandler.sendEmptyMessage(MEDIA_PLAYER_PROGRESS_UPDATE);
	}

	@Override
	public void pause() {
		VideoLog.v(TAG, "pause call" + mVideoEntity.getCurrentPosition());
		mVideoPlayHandler.removeMessages(MEDIA_PLAYER_PROGRESS_UPDATE);
		mVideoPlayHandler.removeCallbacks(checkPrepared);
		if (null != mMediaPlayer) {
			VideoLog.d(TAG, "to pause" + mVideoEntity.getCurrentPosition());
			mMediaPlayer.pause();
		}
	}

	@Override
	public int getDuration() {
		if (null == mMediaPlayer) {
			return -1;
		}
		return mMediaDuration;
	}

	@Override
	public int getCurrentPosition() {
		VideoLog.v(TAG, "getCurrentPosition" + mVideoEntity.getCurrentPosition());
		if (null == mMediaPlayer) {
			return -1;
		}
		//mVideoEntity.setCurrentPosition(mMediaPlayer.getCurrentPosition());
		VideoLog.d(TAG,
		           "in control getCurrentPosition(), current position is: " + mVideoEntity.getCurrentPosition());
		return mMediaPlayer.getCurrentPosition();
	}

	@Override
	public void seekTo(int pos) {
		VideoLog.e(TAG, "seekTo" + pos);
		if (null == mMediaPlayer) {
			return;
		}
		mMediaPlayer.seekTo(pos);
		mVideoEntity.setCurrentPosition(pos);
	}

	@Override
	public boolean isPlaying() {
		return (null != mMediaPlayer) && mMediaPlayer.isPlaying();
	}

	@Override
	public int getBufferPercentage() {
		if (null == mMediaPlayer) {
			return 0;
		}
		return mMediaPlayer.getCurrentPosition() * PERCENT_SWITCH / mMediaPlayer.getDuration();
	}

	@Override
	public boolean canPause() {
		return true;
	}

	@Override
	public boolean canSeekBackward() {
		return true;
	}

	@Override
	public boolean canSeekForward() {
		return true;
	}

	@Override
	public int getAudioSessionId() {
		if (null == mMediaPlayer) {
			return -1;
		}
		return mMediaPlayer.getAudioSessionId();
	}
}
