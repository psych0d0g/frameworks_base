/*
 * Copyright (C) 2012 Sven Dawitz for the CyanogenMod Project
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

package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.BatteryManager;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import com.android.internal.R;
import com.android.internal.util.aokp.StatusBarHelpers;

/***
 * Note about CircleBattery Implementation:
 *
 * Unfortunately, we cannot use BatteryController or DockBatteryController here,
 * since communication between controller and this view is not possible without
 * huge changes. As a result, this Class is doing everything by itself,
 * monitoring battery level and battery settings.
 */

public class CircleBattery extends ImageView {
    private Handler mHandler;
    private Context mContext;
    private BatteryReceiver mBatteryReceiver = null;
    private SettingsObserver mObserver;

    // state variables
    private boolean mAttached;      // whether or not attached to a window
    private boolean mActivated;     // whether or not activated due to system settings
    private boolean mPercentage;    // whether or not to show percentage number
    private boolean mIsCharging;    // whether or not device is currently charging
    private int     mLevel;         // current battery level
    private int     mAnimOffset;    // current level of charging animation
    private boolean mIsAnimating;   // stores charge-animation status to reliably remove callbacks

    private int     mCircleSize;    // draw size of circle. read rather complicated from
                                    // another status bar icon, so it fits the icon size
                                    // no matter the dps and resolution
    private RectF   mCircleRect;    // contains the precalculated rect used in drawArc(), derived from mCircleSize
    private Float   mPercentX;      // precalculated x position for drawText() to appear centered
    private Float   mPercentY;      // precalculated y position for drawText() to appear vertical-centered

    // quiet a lot of paint variables. helps to move cpu-usage from actual drawing to initialization
    private Paint   mPaintFont;
    private Paint   mPaintGray;
    private Paint   mPaintSystem;
    private Paint   mPaintRed;
    private int batteryStyle;

    private int mCircleColor;
    private int mCircleTextColor;
    private int mCircleAnimSpeed;
    private int mCircleReset;

    private int mFontSize = 16;

    // runnable to invalidate view via mHandler.postDelayed() call
    private final Runnable mInvalidate = new Runnable() {
        public void run() {
            if(mActivated && mAttached) {
                invalidate();
            }
        }
    };

    // observes changes in system battery settings and enables/disables view accordingly
    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        public void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUSBAR_BATTERY_ICON), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CIRCLE_BATTERY_COLOR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CIRCLE_BATTERY_TEXT_COLOR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CIRCLE_BATTERY_ANIMATIONSPEED), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CIRCLE_BATTERY_RESET), false, this);
            onChange(true);
        }

        public void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            Resources res = getResources();

            batteryStyle = (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.STATUSBAR_BATTERY_ICON, 0));

            mCircleColor = (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.STATUS_BAR_CIRCLE_BATTERY_COLOR, res.getColor(R.color.holo_blue_dark)));
            mCircleTextColor = (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.STATUS_BAR_CIRCLE_BATTERY_TEXT_COLOR, res.getColor(R.color.holo_blue_dark)));
            mCircleAnimSpeed = (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.STATUS_BAR_CIRCLE_BATTERY_ANIMATIONSPEED, 3));

            if (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.STATUS_BAR_CIRCLE_BATTERY_RESET, 0) == 1) {
                mCircleColor = res.getColor(R.color.holo_blue_dark);
                mCircleTextColor = res.getColor(R.color.holo_blue_dark);
            }

            /*
             * initialize vars and force redraw
             */
            initializeCircleVars();
            mCircleRect = null;
            mCircleSize = 0;

            mActivated = (batteryStyle == SbBatteryController.BATTERY_STYLE_CIRCLE ||
                          batteryStyle == SbBatteryController.BATTERY_STYLE_CIRCLE_PERCENT ||
                          batteryStyle == SbBatteryController.BATTERY_STYLE_DOTTED_CIRCLE ||
                          batteryStyle == SbBatteryController.BATTERY_STYLE_DOTTED_CIRCLE_PERCENT);
            mPercentage = (batteryStyle == SbBatteryController.BATTERY_STYLE_CIRCLE_PERCENT ||
                           batteryStyle == SbBatteryController.BATTERY_STYLE_DOTTED_CIRCLE_PERCENT);

            setVisibility(mActivated ? View.VISIBLE : View.GONE);
            if (mBatteryReceiver != null) {
                mBatteryReceiver.updateRegistration();
            }

            if (mActivated && mAttached) {
                invalidate();
            }
        }
    }

    // keeps track of current battery level and charger-plugged-state
    class BatteryReceiver extends BroadcastReceiver {
        private boolean mIsRegistered = false;

        public BatteryReceiver(Context context) {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                mLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                mIsCharging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;

                if (mActivated && mAttached) {
                    invalidate();
                }
            }
        }

        private void registerSelf() {
            if (!mIsRegistered) {
                mIsRegistered = true;

                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_BATTERY_CHANGED);
                mContext.registerReceiver(mBatteryReceiver, filter);
            }
        }

        private void unregisterSelf() {
            if (mIsRegistered) {
                mIsRegistered = false;
                mContext.unregisterReceiver(this);
            }
        }

        private void updateRegistration() {
            if (mActivated && mAttached) {
                registerSelf();
            } else {
                unregisterSelf();
            }
        }
    }

    /***
     * Start of CircleBattery implementation
     */
    public CircleBattery(Context context) {
        this(context, null);
    }

    public CircleBattery(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircleBattery(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mContext = context;
        mHandler = new Handler();

        batteryStyle = (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.STATUSBAR_BATTERY_ICON, 0));
        mObserver = new SettingsObserver(mHandler);
        mBatteryReceiver = new BatteryReceiver(mContext);

        initializeCircleVars();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
            mAttached = true;
            mObserver.observe();
            mBatteryReceiver.updateRegistration();
            mHandler.postDelayed(mInvalidate, 250);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            mAttached = false;
            mObserver.unobserve(); 
            mBatteryReceiver.updateRegistration();
            mCircleRect = null; // makes sure, size based variables get
                                // recalculated on next attach
            mCircleSize = 0;    // makes sure, mCircleSize is reread from icons on
                                // next attach
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mCircleSize == 0) {
            initSizeMeasureIconHeight();
        }
        setMeasuredDimension(mCircleSize + getPaddingLeft(), mCircleSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mCircleRect == null) {
            initSizeBasedStuff();
        }

        updateChargeAnim();

        Paint usePaint = mPaintSystem;

        // turn red at 14% - same level android battery warning appears
        if (mLevel <= 14) {
            usePaint = mPaintRed;
        }
        usePaint.setAntiAlias(true);
        if (batteryStyle == SbBatteryController.BATTERY_STYLE_DOTTED_CIRCLE_PERCENT ||
            batteryStyle == SbBatteryController.BATTERY_STYLE_DOTTED_CIRCLE) {
            // change usePaint from solid to dashed
            usePaint.setPathEffect(new DashPathEffect(new float[]{3,2},0));
        }else {
            usePaint.setPathEffect(null);
        }

        // pad circle percentage to 100% once it reaches 97%
        // for one, the circle looks odd with a too small gap,
        // for another, some phones never reach 100% due to hardware design
        int padLevel = mLevel;
        if (mLevel >= 97) {
            padLevel=100;
        }

        // draw thin gray ring first
        canvas.drawArc(mCircleRect, 270, 360, false, mPaintGray);
        // draw thin colored ring-level last
        canvas.drawArc(mCircleRect, 270+mAnimOffset, 3.6f * padLevel, false, usePaint);
        // if chosen by options, draw percentage text in the middle
        // always skip percentage when 100, so layout doesnt break
        if (mLevel < 100 && mPercentage){
            if (mLevel <= 14) {
                mPaintFont.setColor(mPaintRed.getColor());
            }else {
                mPaintFont.setColor(mCircleTextColor);
            }
            canvas.drawText(Integer.toString(mLevel), mPercentX, mPercentY, mPaintFont);
        }
    }

    /***
     * Initialize the Circle vars for start and observer
     */
    private void initializeCircleVars() {
        // initialize and setup all paint variables
        // stroke width is later set in initSizeBasedStuff()

        mFontSize = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUSBAR_FONT_SIZE, 16);

        Resources res = getResources();

        mPaintFont = new Paint();
        mPaintFont.setAntiAlias(true);
        mPaintFont.setDither(true);
        mPaintFont.setStyle(Paint.Style.STROKE);

        mPaintGray = new Paint(mPaintFont);
        mPaintSystem = new Paint(mPaintFont);
        mPaintRed = new Paint(mPaintFont);

        mPaintSystem.setColor(mCircleColor);
        // could not find the darker definition anywhere in resources
        // do not want to use static 0x404040 color value. would break theming.
        mPaintGray.setColor(res.getColor(R.color.darker_gray));
        mPaintRed.setColor(res.getColor(R.color.holo_red_light));

        // font needs some extra settings
        mPaintFont.setTextAlign(Align.CENTER);
        mPaintFont.setFakeBoldText(true);
    }

    /***
     * updates the animation counter
     * cares for timed callbacks to continue animation cycles
     * uses mInvalidate for delayed invalidate() callbacks
     */
    private void updateChargeAnim() {
        if (!mIsCharging || mLevel >= 97) {
            if (mIsAnimating) {
                mIsAnimating = false;
                mAnimOffset = 0;
                mHandler.removeCallbacks(mInvalidate);
            }
            return;
        }

        mIsAnimating = true;

        if (mAnimOffset > 360) {
            mAnimOffset = 0;
        } else {
            mAnimOffset += mCircleAnimSpeed;
        }

        mHandler.removeCallbacks(mInvalidate);
        mHandler.postDelayed(mInvalidate, 50);
    }

    /***
     * initializes all size dependent variables
     * sets stroke width and text size of all involved paints
     * YES! i think the method name is appropriate
     */
    private void initSizeBasedStuff() {
        if (mCircleSize == 0) {
            initSizeMeasureIconHeight();
        }

        float percentageFontSize = mCircleSize / 2.5f;
        mPaintFont.setTextSize(percentageFontSize);

        float strokeWidth = mCircleSize / 7f;
        mPaintRed.setStrokeWidth(strokeWidth);
        mPaintSystem.setStrokeWidth(strokeWidth);
        mPaintGray.setStrokeWidth(strokeWidth / 3.5f);
        // calculate rectangle for drawArc calls
        int pTop = getPaddingTop();
        int pLeft = getPaddingLeft();
        mCircleRect = new RectF(pLeft + strokeWidth / 2.0f, pTop + strokeWidth / 2.0f, mCircleSize
                - strokeWidth / 2.0f + pLeft - pTop, mCircleSize - strokeWidth / 2.0f);

        // calculate Y position for text
        Rect bounds = new Rect();
        mPaintFont.getTextBounds("99", 0, "99".length(), bounds);
        
        // needs center becaus of font allign
        mPercentX = mCircleRect.left + mCircleRect.width() / 2.0f;
        mPercentY = mCircleRect.top + mCircleRect.height() / 2.0f + bounds.height() / 2.0f - 1;
    }

    private void initSizeMeasureIconHeight() {
        int width = StatusBarHelpers.getIconWidth(mContext, mFontSize);
        width += 4;
        mCircleSize = width;
    }
}
