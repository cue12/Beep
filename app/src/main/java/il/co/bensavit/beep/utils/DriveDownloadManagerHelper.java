package il.co.bensavit.beep.utils;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class DriveDownloadManagerHelper {
    private static final String TAG = "DriveDownloadHelper";

    public static class ResolvedUrlInfo {
        public final String url;
        public final String cookies;
        public final boolean requiresHtmlConfirmation;

        public ResolvedUrlInfo(String url, String cookies, boolean requiresHtmlConfirmation) {
            this.url = url;
            this.cookies = cookies;
            this.requiresHtmlConfirmation = requiresHtmlConfirmation;
        }
    }

    /**
     * Enqueues a direct download using DownloadManager.
     */
    public static long enqueueDirectDownload(Context context, String directUrl, String cookies, String fileName) {
        Logger.i(TAG, "Enqueuing direct download for " + fileName + " from: " + directUrl);

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(directUrl));

        // Essential: Set User-Agent so Google Drive treats it as a standard browser session
        request.addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36");

        if (cookies != null && !cookies.isEmpty()) {
            request.addRequestHeader("Cookie", cookies);
        }

        request.setTitle(fileName);
        request.setDescription("Downloading model file...");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        // Save to public Downloads directory
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

    public static ResolvedUrlInfo resolveDirectUrl(String fileId) {
        String initialUrl = "https://drive.google.com/uc?export=download&id=" + fileId;
        HttpURLConnection connection = null;
        StringBuilder cookiesBuilder = new StringBuilder();

        try {
            URL url = new URL(initialUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36");
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.connect();

            // Capture cookies if Google Drive sets any session or download_warning cookies
            Map<String, List<String>> headerFields = connection.getHeaderFields();
            if (headerFields != null) {
                List<String> cookiesHeader = headerFields.get("Set-Cookie");
                if (cookiesHeader != null) {
                    for (String cookie : cookiesHeader) {
                        if (cookiesBuilder.length() > 0) {
                            cookiesBuilder.append("; ");
                        }
                        int cookieEnd = cookie.indexOf(';');
                        if (cookieEnd != -1) {
                            cookiesBuilder.append(cookie.substring(0, cookieEnd));
                        } else {
                            cookiesBuilder.append(cookie);
                        }
                    }
                }
            }

            int responseCode = connection.getResponseCode();
            String contentType = connection.getContentType();

            // If it's HTML, Google Drive returned the virus scan warning page! Require framed WebView confirmation.
            if (responseCode == HttpURLConnection.HTTP_OK && contentType != null && contentType.contains("text/html")) {
                Logger.i(TAG, "HTML warning page detected for file ID: " + fileId + ". Requiring WebView confirmation.");
                return new ResolvedUrlInfo(initialUrl, cookiesBuilder.toString(), true);
            }

            // If it already followed redirects to a direct download URL
            String finalUrl = connection.getURL().toString();
            if (!finalUrl.equals(initialUrl)) {
                Logger.i(TAG, "Followed redirect to final direct download URL: " + finalUrl);
                return new ResolvedUrlInfo(finalUrl, cookiesBuilder.toString(), false);
            }

        } catch (IOException e) {
            Logger.e(TAG, "Error resolving Google Drive direct link for ID: " + fileId, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        // Fallback: require framed WebView confirmation if content type was html or connection failed
        return new ResolvedUrlInfo(initialUrl, cookiesBuilder.toString(), true);
    }
}
