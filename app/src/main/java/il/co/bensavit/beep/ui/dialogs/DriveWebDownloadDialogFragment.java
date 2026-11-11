package il.co.bensavit.beep.ui.dialogs;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import il.co.bensavit.beep.MainViewModel;
import il.co.bensavit.beep.R;
import il.co.bensavit.beep.utils.Logger;

public class DriveWebDownloadDialogFragment extends DialogFragment {

    private static final String TAG = "DriveWebDownloadDialog";
    private static final String ARG_MODEL_NAME = "model_name";
    private static final String ARG_DRIVE_URL = "drive_url";

    private MainViewModel viewModel;
    private WebView webView;
    private ProgressBar progressBar;
    private boolean isResolved = false;

    public static DriveWebDownloadDialogFragment newInstance(String modelName, String driveUrl) {
        DriveWebDownloadDialogFragment fragment = new DriveWebDownloadDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MODEL_NAME, modelName);
        args.putString(ARG_DRIVE_URL, driveUrl);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String modelName = getArguments() != null ? getArguments().getString(ARG_MODEL_NAME) : "";
        String driveUrl = getArguments() != null ? getArguments().getString(ARG_DRIVE_URL) : "";

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);

        progressBar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        container.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        webView = new WebView(requireContext());
        LinearLayout.LayoutParams webViewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 360, getResources().getDisplayMetrics())
        );
        container.addView(webView, webViewParams);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                Logger.i(TAG, "Navigating to URL: " + url);

                if (!isResolved && (url.contains("confirm=") || url.contains("export=download") || url.contains("googleusercontent.com"))) {
                    String cookies = CookieManager.getInstance().getCookie(url);
                    Logger.i(TAG, "Detected direct download link: " + url + " with cookies: " + cookies);
                    isResolved = true;
                    viewModel.onDriveUrlResolved(modelName, url, cookies);
                    dismissAllowingStateLoss();
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
                if (webView != null) {
                    webView.setVisibility(View.VISIBLE);
                }

                if (!isResolved && url.contains("confirm=")) {
                    String cookies = CookieManager.getInstance().getCookie(url);
                    Logger.i(TAG, "Page finished with confirm URL: " + url);
                    isResolved = true;
                    viewModel.onDriveUrlResolved(modelName, url, cookies);
                    dismissAllowingStateLoss();
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            Logger.i(TAG, "Download trigger detected in WebView listener: " + url);
            if (!isResolved) {
                isResolved = true;
                String cookies = CookieManager.getInstance().getCookie(url);
                viewModel.onDriveUrlResolved(modelName, url, cookies);
                dismissAllowingStateLoss();
            }
        });

        if (driveUrl != null && !driveUrl.isEmpty()) {
            webView.loadUrl(driveUrl);
        }

        String title = getString(R.string.dialog_download_model_title) + " - " + modelName;

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(container)
                .setCancelable(true)
                .setNegativeButton(R.string.button_cancel, (d, which) -> {
                    if (!isResolved) {
                        viewModel.cancelDownload();
                    }
                })
                .create();

        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        if (!isResolved) {
            viewModel.cancelDownload();
        }
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            if (webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroyView();
    }
}
