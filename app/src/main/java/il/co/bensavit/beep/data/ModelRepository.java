package il.co.bensavit.beep.data;

import android.app.Application;
import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import il.co.bensavit.beep.single.LocalWhisper;
import il.co.bensavit.beep.utils.DownloadManagerHelper;
import il.co.bensavit.beep.utils.DriveDownloadManagerHelper;
import il.co.bensavit.beep.utils.Logger;
import il.co.bensavit.beep.utils.ModelUtils;
import il.co.bensavit.beep.utils.StorageUtils;

public class ModelRepository {
    private static final String TAG = "ModelRepository";

    /**
     * Base URL for direct remote model hosting (e.g. Hugging Face, S3, R2, or custom web server).
     */
    public static final String MODEL_HOST_BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/";

    /**
     * Base URL for OCI (Oracle Cloud Infrastructure) object storage hosting.
     */
    public static final String OCI_BASE_URL = "https://axkx8ogwzsi4.objectstorage.il-jerusalem-1.oci.customer-oci.com/n/axkx8ogwzsi4/b/beep-app-storage/o/";

    public enum HostType {
        DIRECT_URL,
        GOOGLE_DRIVE,
        OCI
    }

    public static class ModelInfo {
        private final HostType hostType;
        private final String location; // Direct URL/relative path OR Google Drive file ID OR OCI relative path
        private final String sha1;

        public static ModelInfo direct(String relativePathOrUrl, String sha1) {
            return new ModelInfo(HostType.DIRECT_URL, relativePathOrUrl, sha1);
        }

        public static ModelInfo drive(String fileId, String sha1) {
            return new ModelInfo(HostType.GOOGLE_DRIVE, fileId, sha1);
        }

        public static ModelInfo oci(String relativePathOrUrl, String sha1) {
            return new ModelInfo(HostType.OCI, relativePathOrUrl, sha1);
        }

        public ModelInfo(HostType hostType, String location, String sha1) {
            this.hostType = hostType;
            this.location = location;
            this.sha1 = sha1;
        }

        public HostType getHostType() {
            return hostType;
        }

        public String getLocation() {
            return location;
        }

        public String getSha1() {
            return sha1;
        }

        public String getFullDownloadUrl() {
            if (hostType == HostType.OCI) {
                if (location != null && (location.startsWith("http://") || location.startsWith("https://"))) {
                    return location;
                }
                return OCI_BASE_URL + location;
            }
            if (hostType == HostType.DIRECT_URL) {
                if (location != null && (location.startsWith("http://") || location.startsWith("https://"))) {
                    return location;
                }
                return MODEL_HOST_BASE_URL + location;
            }
            return location;
        }
    }

    private static final Map<String, ModelInfo> REMOTE_MODEL_IDS = new LinkedHashMap<String, ModelInfo>() {{
        // OCI Storage hosts:
        put("ggml-tiny.bin", ModelInfo.oci("ggml-tiny.bin", "bd577a113a864445d4c299885e0cb97d4ba92b5f"));
        put("ggml-tiny.en.bin", ModelInfo.oci("ggml-tiny.en.bin", "c78c86eb1a8faa21b369bcd33207cc90d64ae9df"));
        put("ggml-base.bin", ModelInfo.oci("ggml-base.bin", "465707469ff3a37a2b9b8d8f89f2f99de7299dac"));
        put("ggml-small.bin", ModelInfo.oci("ggml-small.bin", "55356645c2b361a969dfd0ef2c5a50d530afd8d5"));

        // Google Drive host support is also fully available:
        // put("ggml-medium.bin", ModelInfo.drive("1ejXxgcByOOivap4v6h-O9R47mbKHvgx4", "fd9727b6e1217c2f614f9b698455c4ffd82463b4"));
    }};

    public static final String SUFFIX_CORRUPTED = "_CORRUPTED";

    public static List<String> getRemoteModelNames() {
        return new ArrayList<>(REMOTE_MODEL_IDS.keySet());
    }

    public static boolean isModelAvailable(String modelName) {
        return REMOTE_MODEL_IDS.get(modelName) != null;
    }

    public static String getExpectedSHA(String modelName) {
        ModelInfo info = REMOTE_MODEL_IDS.get(modelName);
        return info != null ? info.getSha1() : null;
    }

    public static boolean isCorrupted(Context context, String modelName) {
        File corruptedFile = new File(StorageUtils.getModelsDir(context), modelName + SUFFIX_CORRUPTED);
        return corruptedFile.exists();
    }

    public static boolean verifyModel(Context context, String modelName) {
        File modelFile = new File(StorageUtils.getModelsDir(context), modelName);
        return modelFile.exists() && modelFile.length() > 0 && !isCorrupted(context, modelName);
    }

    public static boolean performFullVerification(String modelName, File file) {
        String expectedSha = getExpectedSHA(modelName);
        if (expectedSha == null) return true; // No SHA to check against
        String actualSha = ModelUtils.getFileSha1(file);
        return expectedSha.equalsIgnoreCase(actualSha);
    }

    private final Application application;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean downloadCancelled = new AtomicBoolean(false);
    private volatile long currentDownloadId = -1;

    public static class ResolvedDownloadInfo {
        public final String url;
        public final String cookies;

        public ResolvedDownloadInfo(String url, String cookies) {
            this.url = url;
            this.cookies = cookies;
        }
    }

    private final Object pendingResolutionLock = new Object();
    private volatile ResolvedDownloadInfo pendingResolvedInfo = null;
    private volatile java.util.concurrent.CountDownLatch pendingLatch = null;

    public interface ModelLoadCallback {
        void onProgress(int progress);

        void onDownloadStarted(String modelName);

        default void onHtmlConfirmationRequired(String modelName, String driveUrl) {}

        void onDownloadFinished();

        void onError(String modelName, boolean isDownloadError, boolean alreadyExists);

        void onSuccess(String modelName);

        void onComplete();
    }

    public ModelRepository(Application application) {
        this.application = application;
    }

    public void onDriveUrlResolved(String modelName, String directUrl, String cookies) {
        synchronized (pendingResolutionLock) {
            pendingResolvedInfo = new ResolvedDownloadInfo(directUrl, cookies);
            if (pendingLatch != null) {
                pendingLatch.countDown();
            }
        }
    }

    public void loadLocalModel(String modelName, ModelLoadCallback callback) {
        executorService.execute(() -> {
            String selectedModel = StorageUtils.MODELS_DIR + "/" + modelName;
            ModelInfo modelInfo = REMOTE_MODEL_IDS.get(modelName);

            if (modelInfo != null) {
                File modelFile = new File(application.getFilesDir(), selectedModel);
                File corruptedFile = new File(application.getFilesDir(), selectedModel + SUFFIX_CORRUPTED);

                // If file exists, verify it's not empty and check SHA
                boolean existsAndValid = verifyModel(application, modelName);

                if (existsAndValid) {
                    Logger.i(TAG, "Model already exists and is valid: " + modelName);
                } else {
                    if (modelFile.exists()) {
                        Logger.w(TAG, "Found invalid or empty model file, deleting and re-downloading: " + modelName);
                        modelFile.delete();
                    }
                    if (corruptedFile.exists()) {
                        Logger.w(TAG, "Found corrupted model file, deleting and re-downloading: " + modelName);
                        corruptedFile.delete();
                    }

                    downloadCancelled.set(false);
                    if (callback != null) {
                        callback.onDownloadStarted(modelName);
                        callback.onProgress(0);
                    }

                    long downloadId = -1;

                    if (modelInfo.getHostType() == HostType.GOOGLE_DRIVE) {
                        String fileId = modelInfo.getLocation();
                        DriveDownloadManagerHelper.ResolvedUrlInfo resolvedInfo = DriveDownloadManagerHelper.resolveDirectUrl(fileId);

                        if (resolvedInfo.requiresHtmlConfirmation) {
                            if (callback != null) {
                                callback.onHtmlConfirmationRequired(modelName, resolvedInfo.url);
                            }
                            java.util.concurrent.CountDownLatch latch;
                            synchronized (pendingResolutionLock) {
                                pendingResolvedInfo = null;
                                latch = new java.util.concurrent.CountDownLatch(1);
                                pendingLatch = latch;
                            }
                            try {
                                latch.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            ResolvedDownloadInfo resolved;
                            synchronized (pendingResolutionLock) {
                                resolved = pendingResolvedInfo;
                                pendingLatch = null;
                            }
                            if (downloadCancelled.get() || resolved == null) {
                                if (callback != null) {
                                    callback.onDownloadFinished();
                                    if (!downloadCancelled.get()) {
                                        callback.onError(modelName, true, false);
                                    }
                                    callback.onComplete();
                                }
                                return;
                            }
                            downloadId = DriveDownloadManagerHelper.enqueueDirectDownload(application, resolved.url, resolved.cookies, modelName);
                        } else {
                            downloadId = DriveDownloadManagerHelper.enqueueDirectDownload(application, resolvedInfo.url, resolvedInfo.cookies, modelName);
                        }
                    } else {
                        String downloadUrl = modelInfo.getFullDownloadUrl();
                        downloadId = DownloadManagerHelper.enqueueDownload(application, downloadUrl, modelName);
                    }

                    currentDownloadId = downloadId;

                    if (downloadCancelled.get()) {
                        cancelDownload();
                        return;
                    }

                    if (!waitForDownloadManager(downloadId, modelName, modelFile, callback)) {
                        if (callback != null) {
                            callback.onDownloadFinished();
                            if (!downloadCancelled.get()) {
                                callback.onError(modelName, true, false);
                            }
                            callback.onComplete();
                        }
                        currentDownloadId = -1;
                        return;
                    }
                    currentDownloadId = -1;
                    if (callback != null) callback.onDownloadFinished();
                }
            }

            try {
                Logger.i(TAG, "Loading local model: " + modelName);
                LocalWhisper.INSTANCE.setModelPath(selectedModel);
                LocalWhisper.INSTANCE.init(application);
                Logger.i(TAG, "Local model loaded successfully: " + modelName);
                if (callback != null) callback.onSuccess(modelName);
            } catch (Exception e) {
                Logger.e(TAG, "Error loading model: " + modelName, e);
                if (callback != null) callback.onError(modelName, false, false);
            }

            if (callback != null) callback.onComplete();
        });
    }

    public void cancelDownload() {
        downloadCancelled.set(true);
        synchronized (pendingResolutionLock) {
            if (pendingLatch != null) {
                pendingLatch.countDown();
            }
        }
        if (currentDownloadId != -1) {
            DownloadManager manager = (DownloadManager) application.getSystemService(Context.DOWNLOAD_SERVICE);
            manager.remove(currentDownloadId);
            currentDownloadId = -1;
        }
    }

    public void deleteDownloadedModel(String modelName) {
        File modelFile = new File(application.getFilesDir(), StorageUtils.MODELS_DIR + "/" + modelName);
        if (modelFile.exists()) {
            if (!modelFile.delete()) {
                Logger.w(TAG, "Failed to delete model file: " + modelFile.getAbsolutePath());
            }
        }
        File corruptedFile = new File(application.getFilesDir(), StorageUtils.MODELS_DIR + "/" + modelName + SUFFIX_CORRUPTED);
        if (corruptedFile.exists()) {
            if (!corruptedFile.delete()) {
                Logger.w(TAG, "Failed to delete corrupted model file: " + corruptedFile.getAbsolutePath());
            }
        }
    }

    public void clearDownloadedModels() {
        File modelsDir = StorageUtils.getModelsDir(application);
        if (modelsDir.exists() && modelsDir.isDirectory()) {
            File[] files = modelsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        if (!file.delete()) {
                            Logger.w(TAG, "Failed to delete model file: " + file.getAbsolutePath());
                        }
                    }
                }
            }
        }
    }

    private boolean waitForDownloadManager(long downloadId, String modelName, File dstFile, ModelLoadCallback callback) {
        DownloadManager manager = (DownloadManager) application.getSystemService(Context.DOWNLOAD_SERVICE);
        while (!downloadCancelled.get()) {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            try (Cursor cursor = manager.query(query)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    if (statusColumn != -1) {
                        int status = cursor.getInt(statusColumn);

                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            int uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                            if (uriIdx != -1) {
                                String uriString = cursor.getString(uriIdx);
                                if (uriString != null) {
                                    Uri uri = Uri.parse(uriString);
                                    File parentDir = dstFile.getParentFile();
                                    if (parentDir != null && !parentDir.exists()) {
                                        if (!parentDir.mkdirs()) {
                                            Logger.w(TAG, "Failed to create directory: " + parentDir.getAbsolutePath());
                                        }
                                    }
                                    try (java.io.InputStream in = application.getContentResolver().openInputStream(uri);
                                         java.io.OutputStream out = new java.io.FileOutputStream(dstFile)) {
                                        byte[] buf = new byte[8192];
                                        int len;
                                        while ((len = in.read(buf)) > 0) {
                                            out.write(buf, 0, len);
                                        }
                                    } catch (IOException e) {
                                        Logger.e(TAG, "Failed to copy downloaded file from " + uriString, e);
                                        manager.remove(downloadId);
                                        return false;
                                    }

                                    Logger.i(TAG, "Successfully copied downloaded model to: " + dstFile.getAbsolutePath());
                                    // Verify SHA once upon downloading
                                    if (!performFullVerification(modelName, dstFile)) {
                                        Logger.e(TAG, "SHA verification failed for: " + modelName);
                                        File corruptedFile = new File(dstFile.getParent(), dstFile.getName() + SUFFIX_CORRUPTED);
                                        if (dstFile.renameTo(corruptedFile)) {
                                            Logger.i(TAG, "Renamed corrupted file to: " + corruptedFile.getName());
                                        }
                                        manager.remove(downloadId);
                                        return false;
                                    }
                                    manager.remove(downloadId);
                                    return true;
                                }
                            }
                            return false;
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            return false;
                        } else {
                            int downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                            int totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                            if (downloadedIdx != -1 && totalIdx != -1) {
                                int bytesDownloaded = cursor.getInt(downloadedIdx);
                                int bytesTotal = cursor.getInt(totalIdx);
                                if (bytesTotal > 0) {
                                    int progress = (int) (bytesDownloaded * 100L / bytesTotal);
                                    if (callback != null) callback.onProgress(progress);
                                }
                            }
                        }
                    }
                } else {
                    return false;
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
