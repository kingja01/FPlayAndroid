//
// FPlayAndroid is distributed under the FreeBSD License
//
// Copyright (c) 2013-2014, Carlos Rafael Gimenes das Neves
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this
//    list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
// ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
// The views and conclusions contained in the software and documentation are those
// of the authors and should not be interpreted as representing official policies,
// either expressed or implied, of the FreeBSD Project.
//
// https://github.com/carlosrafaelgn/FPlayAndroid
//
package br.com.carlosrafaelgn.fplay.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;

import br.com.carlosrafaelgn.fplay.ActivityMain;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BackgroundActivityMonitor;
import br.com.carlosrafaelgn.fplay.ui.CustomContextMenu;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.NullDrawable;

//
//Handling Runtime Changes
//http://developer.android.com/guide/topics/resources/runtime-changes.html
//
//<activity> (all attributes, including android:configChanges)
//http://developer.android.com/guide/topics/manifest/activity-element.html
//
public final class ActivityHost extends Activity implements Player.PlayerDestroyedObserver, Animation.AnimationListener {
	private ClientActivity top;
	private boolean exitOnDestroy, isFading, useFadeOutNextTime, ignoreFadeNextTime;
	private FrameLayout parent;
	private View oldView, newView;
	private AnimationSet anim;

	private void updateTitle(CharSequence title, boolean announce) {
		setTitle(title);
		//announce should be false when starting/finishing an activity from a menu
		//because in those cases AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED will
		//already happen due to a change in the active window, making the user hear
		//the title twice!
		if (announce)
			UI.announceAccessibilityText(title);
	}

	public ClientActivity getTopActivity() {
		return top;
	}
	
	public void setExitOnDestroy(boolean exitOnDestroy) {
		this.exitOnDestroy = exitOnDestroy;
	}
	
	@Override
	public void onAnimationEnd(Animation animation) {
		if (isFading) {
			isFading = false;
			if (parent != null) {
				if (oldView != null) {
					parent.removeView(oldView);
					oldView = null;
				}
				if (newView != null) {
					newView.setEnabled(true);
					newView = null;
				}
				parent = null;
			}
		}
		if (anim != null) {
			anim.setAnimationListener(null);
			anim = null;
		}
		if (animation != null)
			BackgroundActivityMonitor.start(this);
	}
	
	@Override
	public void onAnimationRepeat(Animation animation) {
	}
	
	@Override
	public void onAnimationStart(Animation animation) {
	}

	public View findViewByIdDirect(int id) {
		return super.findViewById(id);
	}

	@Override
	public View findViewById(int id) {
		return ((newView != null) ? newView.findViewById(id) : super.findViewById(id));
	}

	@Override
	public void setContentView(View view) {
		super.setContentView(view);
		BackgroundActivityMonitor.start(this);
	}

	@Override
	public void setContentView(View view, ViewGroup.LayoutParams params) {
		super.setContentView(view, params);
		BackgroundActivityMonitor.start(this);
	}

	@Override
	public void setContentView(int layoutResID) {
		super.setContentView(layoutResID);
		BackgroundActivityMonitor.start(this);
	}

	void setContentViewWithTransition(View view, boolean fadeAllowed, boolean forceFadeOut) {
		if (fadeAllowed && !ignoreFadeNextTime && UI.transition != UI.TRANSITION_NONE) {
			try {
				parent = (FrameLayout)findViewById(android.R.id.content);
			} catch (Throwable ex) {
				parent = null;
			}
		} else {
			parent = null;
		}
		if (parent != null) {
			anim = null;
			try {
				oldView = parent.getChildAt(0);
				if (oldView != null) {
					newView = view;
					anim = new AnimationSet(true);
					int x, y;
					if (UI.transition == UI.TRANSITION_FADE) {
						x = oldView.getWidth() >> 1;
						y = 0;
					} else {
						x = UI.lastViewCenterLocation[0];
						y = UI.lastViewCenterLocation[1];
					}
					try {
						parent.getLocationOnScreen(UI.lastViewCenterLocation);
						x -= UI.lastViewCenterLocation[0];
						y -= UI.lastViewCenterLocation[1];
					} catch (Throwable ex) {
					}
					//leave prepared for next time
					UI.storeViewCenterLocationForFade(null);
					if (useFadeOutNextTime || forceFadeOut) {
						if (UI.transition == UI.TRANSITION_FADE) {
							anim.addAnimation(new ScaleAnimation(1, 0.75f, 1, 1, x, y));
							anim.addAnimation(new TranslateAnimation(0, 0, 0, oldView.getHeight() >> 3));
						} else {
							anim.addAnimation(new ScaleAnimation(1, 0.3f, 1, 0.3f, x, y));
						}
						anim.addAnimation(new AlphaAnimation(1, 0));
					} else {
						if (UI.transition == UI.TRANSITION_FADE) {
							anim.addAnimation(new ScaleAnimation(0.75f, 1, 1, 1, x, y));
							anim.addAnimation(new TranslateAnimation(0, 0, -(oldView.getHeight() >> 3), 0));
						} else {
							anim.addAnimation(new ScaleAnimation(0.3f, 1, 0.3f, 1, x, y));
						}
						anim.addAnimation(new AlphaAnimation(0, 1));
					}
					anim.setDuration(330);
					anim.setInterpolator(new AccelerateDecelerateInterpolator());
					anim.setRepeatCount(0);
					anim.setFillAfter(false);
				} else {
					parent = null;
				}
			} catch (Throwable ex) {
				anim = null;
				parent = null;
			}
			if (anim != null) {
				BackgroundActivityMonitor.stop();
				anim.setAnimationListener(this);
				oldView.setEnabled(false);
				view.setEnabled(false);
				parent.addView(view, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
				if (useFadeOutNextTime || forceFadeOut) {
					oldView.bringToFront();
					oldView.startAnimation(anim);
				} else {
					view.bringToFront();
					view.startAnimation(anim);
				}
				isFading = true;
			}
		}
		if (parent == null) {
			oldView = null;
			newView = null;
			setContentView(view);
		}
		useFadeOutNextTime = false;
	}
	
	private void startActivityInternal(ClientActivity activity, boolean announce) {
		activity.finished = false;
		activity.activity = this;
		activity.previousActivity = top;
		top = activity;
		updateTitle(activity.getTitle(), announce);
		activity.paused = true;
		activity.onCreate();
		if (!activity.finished) {
			activity.onCreateLayout(true);
			if (!activity.finished && activity.paused) {
				activity.paused = false;
				activity.onResume();
			}
		}
	}
	
	public void startActivity(ClientActivity activity, boolean announce) {
		if (top != null) {
			if (!top.paused) {
				top.paused = true;
				top.onPause();
			}
			if (top != null)
				top.onCleanupLayout();
		}
		startActivityInternal(activity, announce);
	}
	
	public void finishActivity(ClientActivity activity, ClientActivity newActivity, int code, boolean announce) {
		if (activity.finished || activity != top)
			return;
		useFadeOutNextTime = true;
		activity.finished = true;
		if (!activity.paused) {
			activity.paused = true;
			activity.onPause();
		}
		activity.onCleanupLayout();
		activity.onDestroy();
		if (top != null)
			top = top.previousActivity;
		else
			top = null;
		if (top != null)
			updateTitle(top.getTitle(), announce);
		else
			//prevent the title from being said by TalkBack when the
			//application finishes in response to a menu item
			setTitle("\u00A0");
		activity.activity = null;
		activity.previousActivity = null;
		final ClientActivity oldTop = top;
		if (oldTop != null && !oldTop.finished)
			oldTop.activityFinished(activity, activity.requestCode, code);
		if (newActivity != null) {
			startActivityInternal(newActivity, announce);
		} else {
			if (top == null) {
				finish();
			} else if (top == oldTop) {
				//we must check because the top activity could have
				//changed as a consequence of oldTop.activityFinished()
				//being called
				top.onCreateLayout(false);
				if (top != null && !top.finished && top.paused) {
					top.paused = false;
					top.onResume();
				}
			}
		}
		System.gc();
	}

	@Override
	public void onBackPressed() {
		if (isFading)
			return;
		if (top != null) {
			if (top.onBackPressed())
				return;
			if (top != null && top.previousActivity != null) {
				finishActivity(top, null, 0, true);
				return;
			}
		}
		super.onBackPressed();
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (isFading)
			return false;
		if (top != null) {
			final View v = top.getNullContextMenuView();
			if (v != null)
				CustomContextMenu.openContextMenu(v, top);
		}
		return false;
	}
	
	//replace onKeyDown with dispatchKeyEvent + event.getAction() + event.getKeyCode()?!?!?!
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		//
		//Allowing applications to play nice(r) with each other: Handling remote control buttons
		//http://android-developers.blogspot.com.br/2010/06/allowing-applications-to-play-nicer.html
		//
		//...In a media playback application, this is used to react to headset button
		//presses when your activity doesn’t have the focus. For when it does, we override
		//the Activity.onKeyDown() or onKeyUp() methods for the user interface to trap the
		//headset button-related events...
		switch (keyCode) {
		case KeyEvent.KEYCODE_VOLUME_DOWN:
		case KeyEvent.KEYCODE_VOLUME_UP:
			if (Player.volumeControlType == Player.VOLUME_CONTROL_STREAM) {
				Player.handleMediaButton(keyCode);
				return true;
			}
			break;
		default:
			if ((event == null || event.getRepeatCount() == 0) && Player.handleMediaButton(keyCode))
				return true;
			break;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		if (Player.isMediaButton(keyCode))
			return true;
		return super.onKeyLongPress(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (Player.isMediaButton(keyCode))
			return true;
		return super.onKeyUp(keyCode, event);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		final Uri data;
		if (Player.state == Player.STATE_INITIALIZED && intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && (data = intent.getData()) != null) {
			System.out.println(data.getPath());
		}
	}

	@Override
	protected final void onCreate(Bundle savedInstanceState) {
		//StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
		//	.detectAll()
		//	.penaltyLog()
		//	.build());
		//StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
		//	.detectAll()
		//	.penaltyLog()
		//	.build());
		//top is null everytime onCreate is called, which means
		//everytime a new ActivityMain is created
		//if top was any ClientActivity other than ActivityMain when
		//onSaveInstanceState was called, then the android:viewHierarchyState
		//in the savedInstanceState Bundle will not match the actual view
		//structure that belongs to ActivityMain
		//that's why we pass null to super.onCreate!
		super.onCreate(null);
		if (Player.state >= Player.STATE_TERMINATING) {
			finish();
			return;
		}
		MainHandler.initialize();
		UI.initialize(getApplication(), this);
		if (Player.startService(getApplication()))
			UI.setAndroidThemeAccordingly(this);
		UI.storeViewCenterLocationForFade(null);
		Player.alreadySelected = false; //fix the initial selection when the app is started from the widget
		top = new ActivityMain();
		top.finished = false;
		top.activity = this;
		top.previousActivity = null;
		setTitle(top.getTitle());
		getWindow().setBackgroundDrawable(new NullDrawable());
		top.paused = true;
		top.onCreate();
		if (top != null && !top.finished) {
			top.onCreateLayout(true);
			if (top != null && !top.finished)
				Player.addDestroyedObserver(this);
			else
				finish();
		} else {
			finish();
		}
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		//when the user is returning to the app, just reset the
		//previously stored position, allowing for the current song
		//to be displayed instead
		Player.listFirst = -1;
		Player.listTop = 0;
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.clear(); //see the comments in the method above
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		final boolean i = UI.isLandscape;
		ActivityMain.localeHasBeenChanged = false;
		UI.initialize(getApplication(), this);
		if (i != UI.isLandscape) {
			if (top != null) {
				ignoreFadeNextTime = true;
				top.onOrientationChanged();
				ignoreFadeNextTime = false;
				System.gc();
			}
		}
	}
	
	@Override
	protected void onPause() {
		BackgroundActivityMonitor.stop();
		onAnimationEnd(null);
		if (top != null && !top.paused) {
			top.paused = true;
			top.onPause();
		}
		Player.setAppIdle(true);
		super.onPause();
	}

	@Override
	protected void onResume() {
		if (Player.state >= Player.STATE_TERMINATING) {
			finish();
			return;
		}
		Player.setAppIdle(false);
		if (UI.forcedLocale != UI.LOCALE_NONE)
			UI.reapplyForcedLocale(getApplication(), this);
		if (top != null && top.paused) {
			top.paused = false;
			top.onResume();
		}
		BackgroundActivityMonitor.start(this);
		super.onResume();
	}
	
	private void finalCleanup() {
		ClientActivity c = top, p;
		top = null;
		parent = null;
		oldView = null;
		newView = null;
		if (anim != null) {
			anim.setAnimationListener(null);
			anim = null;
		}
		while (c != null) {
			if (!c.finished) {
				c.finished = true;
				if (!c.paused) {
					c.paused = true;
					c.onPause();
				}
				c.onCleanupLayout();
				c.onDestroy();
			}
			p = c.previousActivity;
			c.activity = null;
			c.previousActivity = null;
			c = p;
		}
	}
	
	@Override
	protected void onDestroy() {
		Player.removeDestroyedObserver(this);
		finalCleanup();
		super.onDestroy();
		if (exitOnDestroy)
			Player.stopService();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (top != null)
			top.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onPlayerDestroyed() {
		finalCleanup();
		finish();
	}
}
