package com.lintan.videoplayer.activity;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.lintan.videoplayer.R;
import com.lintan.videoplayer.util.log.VideoLog;

public class AppInfoActivity extends AppCompatActivity implements View.OnTouchListener {
	
	private static final String TAG = "VideoPlayer/AppInfoActivity";
	ImageView closeImg;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setScreenPortrait();
		setContentView(R.layout.activity_app_info);
		closeImg = (ImageView) findViewById(R.id.app_info_title_image);
		VideoLog.d(TAG, "user look at the screen.");
		closeImg.setOnTouchListener(this);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {

		if (event.getAction() == MotionEvent.ACTION_UP) {
			VideoLog.d(TAG, "going to finish the AppInfo.");
			finish();
		}
		return true;
	}

	private void setScreenPortrait(){
		if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
			VideoLog.i(TAG, "going to set SCREEN_ORIENTATION_PORTRAIT");
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}
	}
}
