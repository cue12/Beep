package il.co.bensavit.beep.utils;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

public class DownloadManagerHelper {
    private static final String TAG = "DownloadManagerHelper";

    /**
     * Enqueues a direct file download using Android DownloadManager.
     */
    public static long enqueueDownload(Context context, String directUrl, String fileName) {
        Logger.i(TAG, "Enqueuing direct download for " + fileName + " from: " + directUrl);

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(directUrl));
        request.addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36");
        request.setTitle(fileName);
        request.setDescription("Downloading model file...");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        // Save to external files downloads directory
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName);

        // Allow downloading over both Wi-Fi and Mobile networks
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);

        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        try {
            return manager.enqueue(request);
        } catch (Exception e) {
            Logger.e(TAG, "Failed to enqueue download for " + fileName, e);
            return -1;
        }
    }
}
