/*
 * Copyright (C) 2023-2024 The Josh Tool Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mumu.locationmocker;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;

public class TopUIView {
    private static final String TAG = "TopUIView";
    public static final int ANIMATION_TYPE_NONE = -1;
    public static final int ANIMATION_TYPE_LINEAR = 0;
    public static final int ANIMATION_TYPE_ROTATION = 1;
    public static final int VIEW_TYPE_BUTTON = 0;
    public static final int VIEW_TYPE_SWITCH = 1;
    public static final int VIEW_TYPE_TEXTVIEW = 2;
    private static final long mTouchLongPressThreshold = 700;
    private static final int mTouchTapThreshold = 200;
    private static final int m1stInfoDisappearTimeout = 3000;
    private static final int mInfoDisappearTimeout = 1500;
    private boolean mInfoHasShown = false;

    private int mAnimationType = -1;
    private int mInteractType = 0;
    private int mInteractValue = 0;
    private int mViewState = 0;
    private int[] mViewSize;
    private OnTapListener mOnTapListener;
    private ArrayList<View.OnClickListener> mClickListeners = new ArrayList<>();
    private ArrayList<Integer> mViewImageResources = new ArrayList<>();
    private final View mView;

    public final View.OnTouchListener mDefaultTouchListener = new View.OnTouchListener() {
        private long touchDownTime;
        private long touchUpTime;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchDownTime = System.currentTimeMillis();
                    if(mOnTapListener != null)
                        mOnTapListener.onTouched(mView);
                    v.performClick();
                    return true;
                case MotionEvent.ACTION_UP:
                    touchUpTime = System.currentTimeMillis();
                    long elapsedTime = touchUpTime - touchDownTime;
                    if (elapsedTime < mTouchTapThreshold) {
                        if (mOnTapListener != null)
                            mOnTapListener.onTap(mView);
                        performAnimation(mInteractValue);
                        goNextInteractValue();
                    } else if (elapsedTime > mTouchLongPressThreshold) {
                        if (mOnTapListener != null)
                            mOnTapListener.onLongPress(mView);
                    }
                    if(mOnTapListener != null)
                        mOnTapListener.onReleased(mView);
                    return true;
            }
            return false;
        }
    };

    public interface OnTapListener {
        default void onTap(View view) {

        }
        default void onLongPress(View view) {

        }
        default void onTouched(View view) {

        }
        default void onReleased(View view) {

        }
    }

    public TopUIView(View view, int type) {
        mView = view;
        mInteractType = type;

        ViewGroup.LayoutParams layoutParams = mView.getLayoutParams();
        mViewSize = new int[] {layoutParams.width, layoutParams.height};
    }

    public void setAnimationType(int animationType) {
        mAnimationType = animationType;
    }

    public void setOnTapListener(OnTapListener listener) {
        mOnTapListener = listener;
        if (listener != null)
            mView.setOnTouchListener(mDefaultTouchListener);
    }

    public void setState(int state) {
        mViewState = state;
        applyViewChange();
    }

    public void setVisible(boolean visible) {
        ViewGroup.LayoutParams params = mView.getLayoutParams();
        if (visible) {
            params.width = mViewSize[0];
            params.height = mViewSize[1];
        } else {
            params.width = 0;
            params.height = 0;
        }
        mView.setLayoutParams(params);
    }

    public void setViewStateAction(int state, int imageRes, View.OnClickListener cl) {
        setOnClickListener(state, cl);
        setImageResource(state, imageRes);
        applyViewChange();
    }

    public void setImageResource(int state, int resource) {
        mViewImageResources.add(state, resource);
        applyViewChange();
    }

    public void setOnClickListener(int state, View.OnClickListener clickListener) {
        mClickListeners.add(state, clickListener);
        applyViewChange();
    }

    private void applyViewChange() {
        try {
            View.OnClickListener cl = mClickListeners.get(mViewState);
            if (cl != null) {
                mView.setOnClickListener(cl);
            } else {
                Log.w(TAG, "this view with state " + mViewState + " didn't have an onClickListener");
            }
        } catch (IndexOutOfBoundsException e) {
            //Log.w(TAG, "index " + mViewState + " didn't have an onClickListener");
        }

        try {
            Integer res = mViewImageResources.get(mViewState);
            if (res != null) {
                if (mView instanceof ImageView)
                    ((ImageView) mView).setImageResource(res);
                else if (mView instanceof MaterialButton)
                    ((MaterialButton) mView).setIconResource(res);
            }
        } catch (IndexOutOfBoundsException e) {
            //Log.w(TAG, "index " + mViewState + " didn't have an image resource");
        }

    }

    public void setImageResource(int resource) {
        if (mView instanceof MaterialButton) {
            ((MaterialButton) mView).setIconResource(resource);
        } else if (mView instanceof ImageView) {
            ((ImageView) mView).setImageResource(resource);
        }
    }

    public void setVisibility(boolean visible) {
        mView.setVisibility(visible ? VISIBLE : View.GONE);
        ViewGroup.LayoutParams layoutParams = mView.getLayoutParams();
        layoutParams.width = visible ? mViewSize[0] : 0;
        mView.setLayoutParams(layoutParams);
    }

    public void setText(String text) {
        if (mView instanceof TextView)
            ((TextView) mView).setText(text);
    }

    public void showInstruction(Handler handler, String msg) {
        setText(msg);
        setVisibilityInternal(handler, getView(), VISIBLE);
    }

    public void setVisibilityInternal(Handler handler, View view, int visibility) {
        if (visibility == VISIBLE) {
            view.animate().alpha(1.0f)
                    .setDuration(300)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            super.onAnimationStart(animation);
                            view.setVisibility(VISIBLE);
                        }
                    });

            handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        setVisibilityInternal(handler, view, INVISIBLE);
                    }
            },  mInfoHasShown ? mInfoDisappearTimeout : m1stInfoDisappearTimeout);
            mInfoHasShown = true;
        } else {
            view.animate().alpha(0.0f)
                    .setDuration(300)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            view.setVisibility(View.GONE);
                            super.onAnimationEnd(animation);
                        }
                    });
        }
    }

    public View getView() {
        return mView;
    }

    public int getInteractValue() {
        return mInteractValue;
    }

    public int getState() {
        return mViewState;
    }

    public void goNextInteractValue() {
        if(mInteractType == VIEW_TYPE_SWITCH)
            mInteractValue = (mInteractValue + 1) % 2;
    }

    public void performAnimation(int interactValue) {
        if (mAnimationType == ANIMATION_TYPE_ROTATION) {
            performRotationAnimation(true, interactValue);
        }
    }

    private void performRotationAnimation(boolean clockwise, int interactValue) {
        float fromDegree = 0;
        float toDegree = 180;

        if (interactValue == 0) {
            fromDegree = 180;
            toDegree = 0;
        }

        RotateAnimation rotate = new RotateAnimation(fromDegree, toDegree,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(200);
        rotate.setInterpolator(new LinearInterpolator());
        mView.startAnimation(rotate);
        rotate.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mView.setRotation(mViewState == 1 ? 180f : 0f);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
    }
}
