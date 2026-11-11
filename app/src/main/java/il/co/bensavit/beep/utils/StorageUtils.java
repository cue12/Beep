package il.co.bensavit.beep.utils;

import android.content.Context;
import java.io.File;

public class StorageUtils {
    public static final String MODELS_DIR = "models";
    public static final String SHARED_DIR = "shared";

    public static void ensureDirectoriesExist(Context context) {
        ensureDirectoryExists(new File(context.getFilesDir(), MODELS_DIR));
        ensureDirectoryExists(new File(context.getFilesDir(), SHARED_DIR));
    }

    private static void ensureDirectoryExists(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            Logger.e("StorageUtils", "Failed to create directory: " + dir.getAbsolutePath());
        }
    }

    public static File getModelsDir(Context context) {
        return new File(context.getFilesDir(), MODELS_DIR);
    }

    public static File getSharedDir(Context context) {
        return new File(context.getFilesDir(), SHARED_DIR);
    }
}
