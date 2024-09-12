package com.wingtech.cloudserver.listener;

import android.content.pm.IPackageDataObserver;
import android.os.RemoteException;
import android.util.Log;

/**
 * Created by lijiwei on 18-4-9.
 */

public class ClearUserDataObserver extends IPackageDataObserver.Stub {

    private static final String TAG = "ClearUserDataObserver";

    @Override
    public void onRemoveCompleted(String s, boolean b) throws RemoteException {
        Log.d(TAG, "onRemoveCompleted: success");
    }
}
