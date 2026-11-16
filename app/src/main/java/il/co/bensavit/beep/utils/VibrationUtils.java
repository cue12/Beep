package il.co.bensavit.beep.utils;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import il.co.bensavit.beep.data.SettingsManager;

public class VibrationUtils {
    private static final String TAG = "VibrationUtils";

    /**
     * Triggers a completion vibration pulse if vibration is enabled in Settings
     * and the device supports vibration.
     */
    public static void vibrateCompletion(Context context) {
        if (context == null) return;
        try {
            SettingsManager settingsManager = new SettingsManager(context);
            if (!settingsManager.isVibrationEnabled()) {
                return;
            }

            Vibrator vibrator = getVibrator(context);
            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (vibrator.areAllEffectsSupported(VibrationEffect.EFFECT_HEAVY_CLICK) == Vibrator.VIBRATION_EFFECT_SUPPORT_YES) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Waveform: 50ms pulse - 60ms pause - 50ms pulse
                long[] timings = new long[]{0, 50, 60, 50};
                int[] amplitudes = new int[]{0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE};
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
            } else {
                long[] pattern = new long[]{0, 50, 60, 50};
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception e) {
            Logger.e(TAG, "Failed to execute vibration", e);
        }
    }

    private static Vibrator getVibrator(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vibratorManager != null ? vibratorManager.getDefaultVibrator() : null;
        } else {
            return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }
}
