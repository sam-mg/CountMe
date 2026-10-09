package com.jd_s4nd_b0x.CountMe;

import android.app.Application;

import com.jd_s4nd_b0x.CountMe.drive.SyncManager;
import com.jd_s4nd_b0x.CountMe.util.ThemePrefs;

public class CountMeApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemePrefs.apply(this);
        // Creating the manager hooks repository change events up to the Drive sync.
        SyncManager.getInstance(this).applySchedule();
    }
}
