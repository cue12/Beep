package il.co.bensavit.beep.utils;

import android.app.Activity;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;

import il.co.bensavit.beep.R;

public class InAppUpdateManager {

    private static final String TAG = "InAppUpdateManager";

    private final Activity activity;
    private final AppUpdateManager appUpdateManager;
    private final ActivityResultLauncher<IntentSenderRequest> updateActivityResultLauncher;
    private InstallStateUpdatedListener installStateUpdatedListener;

    public InAppUpdateManager(Activity activity, ActivityResultLauncher<IntentSenderRequest> updateActivityResultLauncher) {
        this.activity = activity;
        this.updateActivityResultLauncher = updateActivityResultLauncher;
        this.appUpdateManager = AppUpdateManagerFactory.create(activity);

        setupInstallStateListener();
    }

    private void setupInstallStateListener() {
        installStateUpdatedListener = state -> {
            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                showUpdateDownloadedSnackbar();
            }
        };
        appUpdateManager.registerListener(installStateUpdatedListener);
    }

    public void checkForUpdates(boolean isManualCheck) {
        if (isManualCheck) {
            Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show();
        }

        appUpdateManager.getAppUpdateInfo().addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                startUpdateFlow(appUpdateInfo);
            } else if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startUpdateFlow(appUpdateInfo);
            } else if (isManualCheck) {
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_NOT_AVAILABLE) {
                    Toast.makeText(activity, R.string.update_latest, Toast.LENGTH_LONG).show();
                }
            }
        }).addOnFailureListener(e -> {
            Logger.e(TAG, "Failed to check for updates", e);
            if (isManualCheck) {
                Toast.makeText(activity, R.string.update_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void checkOnResume() {
        appUpdateManager.getAppUpdateInfo().addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                showUpdateDownloadedSnackbar();
            }
        });
    }

    private void startUpdateFlow(AppUpdateInfo appUpdateInfo) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    updateActivityResultLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build());
        } catch (Exception e) {
            Logger.e(TAG, "Error starting in-app update flow", e);
        }
    }

    private void showUpdateDownloadedSnackbar() {
        Snackbar snackbar = Snackbar.make(
                activity.findViewById(android.R.id.content),
                R.string.update_downloaded,
                Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.update_restart, v -> appUpdateManager.completeUpdate());
        snackbar.show();
    }

    public void unregister() {
        if (installStateUpdatedListener != null) {
            appUpdateManager.unregisterListener(installStateUpdatedListener);
        }
    }
}
