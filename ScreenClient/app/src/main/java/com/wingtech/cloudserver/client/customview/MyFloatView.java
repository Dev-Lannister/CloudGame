package com.wingtech.cloudserver.client.customview;

import android.content.Context;
import android.os.Message;
import android.support.v7.widget.AppCompatImageView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.wingtech.cloudserver.client.MainActivity;
import com.wingtech.cloudserver.client.applicaiton.MyApplication;

/**
 * Created by lijiwei on 2018/2/12.
 */

public class MyFloatView extends AppCompatImageView{

    private static final String TAG = "MyFloatView";

    private float mTouchStartX;
    private float mTouchStartY;
    private float x;
    private float y;

    private long downTime;
    private long upTime;

    private WindowManager wm=(WindowManager)getContext().getApplicationContext().
            getSystemService(Context.WINDOW_SERVICE);
    private WindowManager.LayoutParams wmParams = ((MyApplication)getContext().
            getApplicationContext()).getMywmParams();

    public MyFloatView(Context context) {
        super(context);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        x = event.getRawX();
        y = event.getRawY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downTime = System.currentTimeMillis();
                mTouchStartX =  event.getX();
                mTouchStartY =  event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                updateViewPosition();
                break;
            case MotionEvent.ACTION_UP:
                updateViewPosition();
                upTime = System.currentTimeMillis();
                Log.d(TAG, "onTouchEvent: tiomeoffset="+(upTime-downTime));
                if(upTime - downTime < 100){
                    Log.d(TAG, "onTouchEvent: onclick");
                    Message message = Message.obtain();
                    message.what = 5;
                    MainActivity.mHandler.sendMessage(message);
                }
                mTouchStartX=mTouchStartY=0;
                break;
        }
        return true;
    }
    private void updateViewPosition(){
        //更新浮动窗口位置参数
        wmParams.x=(int)( x-mTouchStartX);
        wmParams.y=(int) (y-mTouchStartY);
        wm.updateViewLayout(this, wmParams);
    }
}
