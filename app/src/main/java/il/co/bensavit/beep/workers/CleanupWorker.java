package il.co.bensavit.beep.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;

import il.co.bensavit.beep.utils.Logger;
import il.co.bensavit.beep.utils.StorageUtils;

public class CleanupWorker extends Worker {

    private static final String TAG = "CleanupWorker";
    private static final long MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days

    public CleanupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Logger.i(TAG, "Starting storage cleanup...");
        try {
            File sharedDir = StorageUtils.getSharedDir(getApplicationContext());
            if (sharedDir.exists() && sharedDir.isDirectory()) {
                File[] files = sharedDir.listFiles();
                if (files != null) {
                    long now = System.currentTimeMillis();
                    for (File file : files) {
                        if ("jfk.wav".equals(file.getName())) continue; // Preserve sample

                        if (now - file.lastModified() > MAX_AGE_MS) {
                            if (file.delete()) {
                                Logger.i(TAG, "Deleted old file: " + file.getName());
                            } else {
                                Logger.w(TAG, "Failed to delete: " + file.getName());
                            }
                        }
                    }
                }
            }
            return Result.success();
        } catch (Throwable t) {
            Logger.e(TAG, "Storage cleanup failed", t);
            return Result.failure();
        }
    }
}
