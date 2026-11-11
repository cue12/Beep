package il.co.bensavit.beep.utils;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AssetUtils {
  private static final String TAG = "AssetUtils";

  public static File copyFileIfNotExists(Context context, File distDir, String filename) {
    File dstFile = new File(distDir, filename);
    if (dstFile.exists() && dstFile.length() > 0) {
      return dstFile;
    } else {
      if (dstFile.exists()) {
        Logger.w(TAG, "Found empty or partial file at " + dstFile.getAbsolutePath() + ", deleting and re-copying.");
        dstFile.delete();
      }
      File parentFile = dstFile.getParentFile();
      if (parentFile != null && !parentFile.exists()) {
        if (!parentFile.mkdirs()) {
           Logger.w(TAG, "Could not create directory: " + parentFile);
        }
      }
      AssetUtils.copyFileFromAssets(context, filename, dstFile);
    }
    return dstFile;
  }

  public static void copyFileFromAssets(Context appCtx, String srcPath, File dstFile) {
    if (srcPath.isEmpty()) {
      return;
    }
    try (InputStream is = new BufferedInputStream(appCtx.getAssets().open(srcPath))) {
      // Open output stream only if input stream (asset) was successfully opened
      try (OutputStream os = new BufferedOutputStream(new FileOutputStream(dstFile))) {
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
          os.write(buffer, 0, length);
        }
        os.flush();
      }
    } catch (IOException e) {
      Logger.e(TAG, "copyFileFromAssets error: " + srcPath + ". " + e.getMessage());
      if (dstFile.exists()) {
        dstFile.delete(); // Don't leave partial or empty files
      }
    }
  }

  public static File copyUriToFile(Context context, android.net.Uri uri, File dstFile) {
    Logger.d(TAG, "copyUriToFile: uri=" + uri + ", dst=" + dstFile.getAbsolutePath());
    try (java.io.InputStream is = context.getContentResolver().openInputStream(uri);
         java.io.OutputStream os = new java.io.FileOutputStream(dstFile)) {
      if (is == null) {
        Logger.e(TAG, "copyUriToFile: InputStream is null");
        return null;
      }
      byte[] buffer = new byte[1024];
      int length;
      int totalRead = 0;
      while ((length = is.read(buffer)) != -1) {
        os.write(buffer, 0, length);
        totalRead += length;
      }
      Logger.d(TAG, "copyUriToFile: copied " + totalRead + " bytes");
      return dstFile;
    } catch (java.io.IOException e) {
      Logger.e(TAG, "copyUriToFile: Error copying URI to file", e);
      return null;
    } catch (SecurityException e) {
      Logger.e(TAG, "copyUriToFile: SecurityException reading URI", e);
      return null;
    }
  }
}
