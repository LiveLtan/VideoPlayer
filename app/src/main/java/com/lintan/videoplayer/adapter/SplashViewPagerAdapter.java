package com.lintan.videoplayer.adapter;

import android.support.v4.view.PagerAdapter;
import android.view.View;
import android.view.ViewGroup;

import java.util.LinkedList;

/**
 * it is a splash view pager adapter,
 * and it will running at first time that app has been installed.
 *
 * Created by lintan on 9/7/16.
 */
public class SplashViewPagerAdapter extends PagerAdapter {
	private LinkedList<View> mViews;

	public SplashViewPagerAdapter(LinkedList<View> viewList) {
		super();
		mViews = viewList;
	}

	@Override
	public Object instantiateItem(ViewGroup container, int position) {
		View view = mViews.get(position);
		container.addView(view);
		return view;
	}

	@Override
	public void destroyItem(ViewGroup container, int position, Object object) {
		container.removeView(mViews.get(position));
		//super.destroyItem(container, position, object);
	}

	@Override
	public int getCount() {
		return mViews.size();
	}

	@Override
	public boolean isViewFromObject(View view, Object object) {
		return view == object;
	}
}
