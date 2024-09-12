package com.wingtech.cloudserver.client.applicaiton;

import android.app.Application;
import android.view.WindowManager;

/**
 * Created by lijiwei on 2018/2/12.
 */

public class MyApplication extends Application {

    private WindowManager.LayoutParams wmParams=new WindowManager.LayoutParams();

    public WindowManager.LayoutParams getMywmParams(){
        return wmParams;
    }

}
