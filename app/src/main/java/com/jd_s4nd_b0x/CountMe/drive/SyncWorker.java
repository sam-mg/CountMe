package com.jd_s4nd_b0x.CountMe.drive;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Background Drive sync, scheduled by {@link SyncManager#applySchedule()}. */
public class SyncWorker extends Worker {

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SyncManager.State result = SyncManager.getInstance(getApplicationContext()).syncBlocking();
        switch (result) {
            case FAILED:
                return Result.retry();
            // Signed out, or Google needs the user to re-consent: retrying cannot help.
            case NEEDS_SIGN_IN:
            case SIGNED_OUT:
                return Result.failure();
            default:
                return Result.success();
        }
    }
}
