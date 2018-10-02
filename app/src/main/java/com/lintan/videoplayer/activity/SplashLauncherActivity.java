package com.lintan.videoplayer.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;

import com.lintan.videoplayer.R;
import com.lintan.videoplayer.adapter.SplashViewPagerAdapter;
import com.lintan.videoplayer.bean.VideoEntity;
import com.lintan.videoplayer.util.log.VideoLog;
import com.lintan.videoplayer.util.shared.SharedPreferenceUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;

public class SplashLauncherActivity extends AppCompatActivity implements ViewPager.OnPageChangeListener {

	public static final String DATA = "data";

	private static final String TAG = "VideoPlayer/SplashLauncherActivity";

	private static final String IS_FIRST_LAUNCHED_KEY = "isFirstLaunch";
	private MyHandler myHandler;
	private static final int TMP_SHOW_TIME_MIN = 2000;
	private static final int TASK_OTHER_SHOW_MIN = 500;
	private static final int ASYNC_TASK_QUERY_COMPLETED = 0x0001;
	private static final int ASYNC_TASK_OTHER_COMPLETED = 0x0002;

	private long mStartTime;
	private ArrayList<VideoEntity> mVideoEntityList = null;

	private ViewPager mViewPager;
	private LinearLayout mDotsContainerLiner;
	private LinkedList<View> mViewList;
	private LinkedList<View> mDotViewList;

	private static class MyHandler extends Handler {
		private WeakReference<SplashLauncherActivity> reference;
		private SplashLauncherActivity activity;
		MyHandler(SplashLauncherActivity activity) {
			reference = new WeakReference<>(activity);
		}
		@Override
		public void handleMessage(Message msg) {
			activity = reference.get();
			switch (msg.what) {
				case ASYNC_TASK_QUERY_COMPLETED:
					long loadingTime = System.currentTimeMillis() - activity.mStartTime;
					if (loadingTime < TMP_SHOW_TIME_MIN) {
						activity.myHandler.postDelayed(activity.ToVideoCenterActivityRunnableWithData,
						                                      TMP_SHOW_TIME_MIN -
						                                      loadingTime);
					} else {
						activity.myHandler.post(activity.ToVideoCenterActivityRunnableWithData);
					}
					break;
				case ASYNC_TASK_OTHER_COMPLETED:
					activity.myHandler.post(activity.ToVideoCenterActivityRunnable);
					break;
				default:
					break;
			}
			super.handleMessage(msg);
		}
	}

	private Runnable ToVideoCenterActivityRunnable = new Runnable() {
		@Override
		public void run() {
			Intent intent = new Intent(SplashLauncherActivity.this, VideoCenterActivity.class);
			SplashLauncherActivity.this.startActivity(intent);
			finish();
		}
	};

	private Runnable ToVideoCenterActivityRunnableWithData = new Runnable() {
		@Override
		public void run() {
			Intent intent = new Intent(SplashLauncherActivity.this, VideoCenterActivity.class);
			intent.putParcelableArrayListExtra(DATA, mVideoEntityList);
			SplashLauncherActivity.this.startActivity(intent);
			finish();
		}
	};

	/**
	 * to set the status bar background.
	 *
	 * @param activity activity
	 */
	private void setWindowsTheme(Activity activity) {
		activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
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
			window.setStatusBarColor(0xff0b87c7);
			window.setNavigationBarColor(Color.TRANSPARENT);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setWindowsTheme(this);
		myHandler = new MyHandler(this);
		if (!isLaunched()) {
			mStartTime = System.currentTimeMillis();
			setContentView(R.layout.activity_splash_launcher);
			initView();
			initAdapter();
			initListener();
		} else {
			setContentView(R.layout.activity_splash_launcher_tmp);
			myHandler.sendEmptyMessageDelayed(ASYNC_TASK_OTHER_COMPLETED, TASK_OTHER_SHOW_MIN);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
		//mHandler.sendEmptyMessageDelayed(ASYNC_TASK_QUERY_COMPLETED, TMP_SHOW_TIME_MIN);
	}

	private boolean isLaunched() {

		return (boolean) SharedPreferenceUtil.get(this, IS_FIRST_LAUNCHED_KEY, false);
	}

	private void initView() {
		mViewPager = (ViewPager) findViewById(R.id.splash_activity_viewpager);
		mDotsContainerLiner = (LinearLayout) findViewById(R.id.splash_activity_viewpager_ll);
		//mIndicatorView = (IndicatorView) findViewById(R.id.splash_activity_viewpager_indicator);
		LayoutInflater inflater = LayoutInflater.from(this);
		mViewList = new LinkedList<>();
		mDotViewList = new LinkedList<>();
		mViewList.add(inflater.inflate(R.layout.splash_view_pager_index, null));
		mViewList.add(inflater.inflate(R.layout.splash_view_pager_content, null));
		mViewList.add(inflater.inflate(R.layout.splash_view_pager_start, null));
		View v1 = findViewById(R.id.splash_activity_viewpager_ll_view1);
		View v2 = findViewById(R.id.splash_activity_viewpager_ll_view2);
		View v3 = findViewById(R.id.splash_activity_viewpager_ll_view3);
		mDotViewList.add(v1);
		mDotViewList.add(v2);
		mDotViewList.add(v3);
		//mStartButton = mViewList.get(2).findViewById(R.id.button);
	}

	private void initAdapter() {
		SplashViewPagerAdapter mViewPagerAdapter = new SplashViewPagerAdapter(mViewList);
		mViewPager.setAdapter(mViewPagerAdapter);
		mViewPager.addOnPageChangeListener(this);
		mViewPager.setCurrentItem(0);
		mDotViewList.get(0).setSelected(true);
	}

	public void buttonOnClickToVideoList(View v) {
		// if we must do some thing at this time, we can delayed
		SharedPreferenceUtil.put(this, IS_FIRST_LAUNCHED_KEY, true);
		myHandler.sendEmptyMessageDelayed(ASYNC_TASK_OTHER_COMPLETED, TASK_OTHER_SHOW_MIN);
	}

	private void initListener() {
		//mIndicatorView.setViewPager(mViewPager);
		//mIndicatorView.addOnPageChangeListener(this);
	}

	@Override
	public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

	}

	@Override
	public void onPageSelected(int position) {
		VideoLog.v(TAG, "onPageSelected + " + position);
		if (position == mDotViewList.size() - 1) {
			hideDots();
		} else {
			showDots();
			clearSelection();
			mDotViewList.get(position).setSelected(true);
		}
	}

	private void hideDots() {
		mDotsContainerLiner.setVisibility(View.GONE);
	}

	private void showDots() {
		mDotsContainerLiner.setVisibility(View.VISIBLE);
	}

	private void clearSelection() {
		for (View v : mDotViewList) {
			v.setSelected(false);
		}
	}

	@Override
	public void onPageScrollStateChanged(int state) {

	}

	@Override
	protected void onStop() {
		super.onStop();
		finish();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (null != mViewList) {
			mViewList.clear();
			mViewList = null;
		}
		if (null != mDotViewList) {
			mDotViewList.clear();
			mDotViewList = null;
		}
	}
}
