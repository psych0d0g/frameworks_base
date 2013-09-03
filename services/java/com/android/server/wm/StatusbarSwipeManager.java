package com.android.server.wm;

import android.provider.Settings;
import android.os.Handler;
import android.util.Log;
import android.content.Context;
import android.content.Intent;

public class StatusbarSwipeManager {

    private static StatusbarSwipeManager mInstance;
    
    private final String TAG = "StatusbarSwipeManager";
    private final boolean DEBUG = true;
    
    private Context mContext;
    private Runnable mRunnable;
    private Handler mHandler;
    
    private static final String ACTION_STATUSBAR_SWIPE = "com.android.server.wm.ACTION_STATUSBAR_SWIPE";
    
    public static StatusbarSwipeManager getInstance(){
        if (mInstance == null){
            mInstance = new StatusbarSwipeManager();
        }
        return mInstance;
    }
    
    private StatusbarSwipeManager(){
        mHandler = new Handler();
    }
    
    public void setContext(Context context){
        mContext = context;
    }
    
    private Runnable getRunnable(){
        return new Runnable() {
            public void run() {
                Intent intent = new Intent(ACTION_STATUSBAR_SWIPE);
                intent.setFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                intent.putExtra("state", 0);
                mContext.sendBroadcast(intent);
                done();
            }               
        };
    }

    private long getDelay(){
        return Settings.System.getInt(mContext.getContentResolver(), 
                Settings.System.STATUSBAR_SWIPE_TIMEOUT, 5000); 
    }
    
    public void startTimer(){
        if (DEBUG) Log.d(TAG, "startTimer mRunnable="+mRunnable);
        if (mRunnable != null){
            stopTimer();
        }

        mRunnable = getRunnable();
        
        Intent intent = new Intent(ACTION_STATUSBAR_SWIPE);
        intent.setFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("state", 1);
        mContext.sendBroadcast(intent);
        
        mHandler.postDelayed(mRunnable, getDelay());
    }
    
    private void done(){
        if (DEBUG) Log.d(TAG, "done mRunnable="+mRunnable);
        mRunnable = null;
    }
    
    public void stopTimer(){
        if (DEBUG) Log.d(TAG, "stopTimer mRunnable="+mRunnable);
        if (mRunnable != null){
            mHandler.removeCallbacks(mRunnable);
        }
    }
    
    public void resumeTimer(){
        if (DEBUG) Log.d(TAG, "resumeTimer mRunnable="+mRunnable);
        if (mRunnable != null){
            mHandler.post(mRunnable);
        }
    }
}

