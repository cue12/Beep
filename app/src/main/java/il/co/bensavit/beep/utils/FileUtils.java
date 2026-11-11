package il.co.bensavit.beep.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;

import il.co.bensavit.beep.R;

public class FileUtils {
    private static final String TAG = "FileUtils";

    public interface OnFileRenamedListener {
        void onFileRenamed(File newFile);
    }

    public static void showRenameDialog(Context context, File file, OnFileRenamedListener listener) {
        String originalName = file.getName();
        int dotIndex = originalName.lastIndexOf('.');
        String baseName = (dotIndex > 0) ? originalName.substring(0, dotIndex) : originalName;
        String extension = (dotIndex > 0) ? originalName.substring(dotIndex) : "";

        FrameLayout container = new FrameLayout(context);
        int paddingHorizontal = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, context.getResources().getDisplayMetrics());
        int paddingVertical = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, context.getResources().getDisplayMetrics());
        container.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, 0);

        TextInputLayout inputLayout = new TextInputLayout(context);
        if (!extension.isEmpty()) {
            inputLayout.setSuffixText(extension);
        }

        TextInputEditText input = new TextInputEditText(inputLayout.getContext());
        input.setText(baseName);
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);

        inputLayout.addView(input);
        container.addView(inputLayout);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.rename_file_title)
                .setView(container)
                .setPositiveButton(R.string.rename, (dialog, which) -> {
                    String newBaseName = input.getText() != null ? input.getText().toString().trim() : "";
                    if (!newBaseName.isEmpty()) {
                        if (!extension.isEmpty() && newBaseName.toLowerCase().endsWith(extension.toLowerCase())) {
                            newBaseName = newBaseName.substring(0, newBaseName.length() - extension.length()).trim();
                        }
                        if (!newBaseName.isEmpty()) {
                            String newName = newBaseName + extension;
                            if (!newName.equals(file.getName())) {
                                File newFile = new File(file.getParentFile(), newName);
                                if (file.renameTo(newFile)) {
                                    if (listener != null) {
                                        listener.onFileRenamed(newFile);
                                    }
                                } else {
                                    Toast.makeText(context, R.string.rename_failed, Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            } catch (Exception e) {
                Logger.e(TAG, "Error getting filename from content URI", e);
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        if (result != null) {
            // Sanitize illegal filesystem characters while preserving Unicode/non-Latin letters and numbers
            result = result.replaceAll("[\u0000-\u001F/\\\\:*?\"<>|]", "_");
        }
        return result;
    }
}
