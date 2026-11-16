package il.co.bensavit.beep.ui.dialogs;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import il.co.bensavit.beep.BuildConfig;
import il.co.bensavit.beep.MainActivity;
import il.co.bensavit.beep.MainViewModel;
import il.co.bensavit.beep.R;
import il.co.bensavit.beep.utils.Logger;

public class AboutDialogFragment extends DialogFragment {

    private static final String CODEQL_WORKFLOW_URL = "https://github.com/cue12/Beep/actions/workflows/codeql.yml";
    private static final String CODEQL_BADGE_URL = "https://github.com/cue12/Beep/actions/workflows/codeql.yml/badge.svg";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.dialog_about, null);

        TextView messageView = view.findViewById(R.id.text_about_message);
        TextView codeQlLabel = view.findViewById(R.id.text_codeql_label);
        WebView codeQlBadgeView = view.findViewById(R.id.webview_codeql_badge);
        TextView feedbackLink = view.findViewById(R.id.text_request_feature);
        TextView viewLogsLink = view.findViewById(R.id.text_view_logs);

        if (messageView != null) {
            messageView.setText(getString(R.string.about_message_content, BuildConfig.VERSION_NAME, "whisper.cpp", "ggml", "whisper.android"));
        }

        if (codeQlLabel != null) {
            codeQlLabel.setOnClickListener(v -> openUrl(CODEQL_WORKFLOW_URL));
        }

        if (codeQlBadgeView != null) {
            codeQlBadgeView.setBackgroundColor(Color.TRANSPARENT);
            codeQlBadgeView.setVerticalScrollBarEnabled(false);
            codeQlBadgeView.setHorizontalScrollBarEnabled(false);

            String htmlData = "<html><head><style>" +
                    "body { margin: 0; padding: 4px 0 0 0; background-color: transparent; display: flex; align-items: center; }" +
                    "img { height: 24px; cursor: pointer; }" +
                    "</style></head><body>" +
                    "<a href='" + CODEQL_WORKFLOW_URL + "'><img src='" + CODEQL_BADGE_URL + "' alt='CodeQL Status'/></a>" +
                    "</body></html>";

            codeQlBadgeView.loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null);
            codeQlBadgeView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                    openUrl(request.getUrl().toString());
                    return true;
                }

                @SuppressWarnings("deprecation")
                @Override
                public boolean shouldOverrideUrlLoading(WebView v, String url) {
                    openUrl(url);
                    return true;
                }
            });
        }

        if (feedbackLink != null) {
            feedbackLink.setOnClickListener(v -> openPlayStoreFeedback());
        }

        if (viewLogsLink != null) {
            viewLogsLink.setOnClickListener(v -> {
                dismiss();
                MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
                viewModel.requestShowLogWindow();
            });
        }

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.about_title)
                .setView(view)
                .setPositiveButton(R.string.button_ok, null)
                .setNeutralButton(R.string.about_licenses, (dialog, which) -> showLicensesDialog())
                .setNegativeButton(R.string.check_for_updates, (dialog, which) -> {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).checkForUpdatesManually();
                    }
                })
                .create();
    }

    @Override
    public void onDestroyView() {
        if (getDialog() != null) {
            WebView codeQlBadgeView = getDialog().findViewById(R.id.webview_codeql_badge);
            if (codeQlBadgeView != null) {
                if (codeQlBadgeView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) codeQlBadgeView.getParent()).removeView(codeQlBadgeView);
                }
                codeQlBadgeView.stopLoading();
                codeQlBadgeView.destroy();
            }
        }
        super.onDestroyView();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Logger.e("AboutDialog", "Unable to open URL: " + url, e);
        }
    }

    private void openPlayStoreFeedback() {
        String packageName = requireContext().getPackageName();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)));
        }
    }

    private void showLicensesDialog() {
        try {
            InputStream is = requireContext().getAssets().open("NOTICE.txt");
            Scanner s = new Scanner(is).useDelimiter("\\A");
            String content = s.hasNext() ? s.next() : "";

            View view = getLayoutInflater().inflate(R.layout.dialog_log_window, null);
            TextView textView = view.findViewById(R.id.text_logs);
            if (textView != null) {
                textView.setText(content);
            }

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.about_licenses)
                    .setView(view)
                    .setPositiveButton(R.string.button_close, null)
                    .show();
        } catch (IOException e) {
            Logger.e("AboutDialog", "Error reading NOTICE.txt", e);
        }
    }
}
