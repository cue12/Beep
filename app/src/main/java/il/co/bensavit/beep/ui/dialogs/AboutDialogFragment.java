package il.co.bensavit.beep.ui.dialogs;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import il.co.bensavit.beep.BuildConfig;
import il.co.bensavit.beep.MainActivity;
import il.co.bensavit.beep.R;
import il.co.bensavit.beep.utils.Logger;

public class AboutDialogFragment extends DialogFragment {

    private static final String CODEQL_WORKFLOW_URL = "https://github.com/cue12/Beep/actions/workflows/codeql.yml";
    private static final String CODEQL_BADGE_URL = "https://github.com/cue12/Beep/actions/workflows/codeql.yml/badge.svg";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        layout.setPadding(padding, padding / 2, padding, 0);

        TextView messageView = new TextView(requireContext());
        messageView.setText(getString(R.string.about_message_content, BuildConfig.VERSION_NAME, "whisper.cpp", "ggml", "whisper.android"));
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        layout.addView(messageView);

        // CodeQL Security Badge Section
        LinearLayout codeQlLayout = new LinearLayout(requireContext());
        codeQlLayout.setOrientation(LinearLayout.VERTICAL);
        codeQlLayout.setPadding(0, padding, 0, 0);

        TextView codeQlLabel = new TextView(requireContext());
        codeQlLabel.setText(R.string.about_codeql_badge);
        codeQlLabel.setTextColor(getResources().getColor(android.R.color.holo_blue_dark, requireContext().getTheme()));
        codeQlLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        codeQlLabel.setOnClickListener(v -> openUrl(CODEQL_WORKFLOW_URL));
        codeQlLayout.addView(codeQlLabel);

        WebView codeQlBadgeView = new WebView(requireContext());
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
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                openUrl(request.getUrl().toString());
                return true;
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                openUrl(url);
                return true;
            }
        });

        int badgeHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, badgeHeight);
        codeQlBadgeView.setLayoutParams(badgeParams);
        codeQlLayout.addView(codeQlBadgeView);

        layout.addView(codeQlLayout);

        TextView feedbackLink = new TextView(requireContext());
        feedbackLink.setText(R.string.request_feature);
        feedbackLink.setTextColor(getResources().getColor(android.R.color.holo_blue_dark, requireContext().getTheme()));
        feedbackLink.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        feedbackLink.setPadding(0, padding / 2, 0, 0);
        feedbackLink.setOnClickListener(v -> openPlayStoreFeedback());
        layout.addView(feedbackLink);

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.addView(layout);

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.about_title)
                .setView(scrollView)
                .setPositiveButton(R.string.button_ok, null)
                .setNeutralButton(R.string.about_licenses, (dialog, which) -> showLicensesDialog())
                .setNegativeButton(R.string.check_for_updates, (dialog, which) -> {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).checkForUpdatesManually();
                    }
                })
                .create();
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

            TextView textView = new TextView(requireContext());
            textView.setText(content);
            textView.setPadding(48, 48, 48, 48);
            textView.setTypeface(Typeface.MONOSPACE);
            textView.setTextSize(10);

            ScrollView scrollView = new ScrollView(requireContext());
            scrollView.addView(textView);

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.about_licenses)
                    .setView(scrollView)
                    .setPositiveButton(R.string.button_close, null)
                    .show();
        } catch (IOException e) {
            Logger.e("AboutDialog", "Error reading NOTICE.txt", e);
        }
    }
}
