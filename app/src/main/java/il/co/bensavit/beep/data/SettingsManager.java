package il.co.bensavit.beep.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class SettingsManager {
    private static final String TAG = "SettingsManager";
    private static final String PREFS_NAME = "BeepPrefs";
    private static final String SECURE_PREFS_NAME = "BeepSecurePrefs";

    private static final String KEY_MODEL_NAME = "model_name";
    private static final String KEY_GEMINI_MODEL_NAME = "gemini_model_name";
    private static final String KEY_IS_GEMINI = "is_gemini";
    private static final String KEY_GEMINI_API_KEY = "gemini_api_key";
    private static final String KEY_TUTORIAL_SHOWN = "tutorial_shown";
    private static final String KEY_LANGUAGE = "language";

    private final Context appContext;
    private final SharedPreferences prefs;
    private SharedPreferences securePrefs;

    public SettingsManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private synchronized SharedPreferences getSecurePrefs() {
        if (securePrefs == null) {
            try {
                MasterKey masterKey = new MasterKey.Builder(appContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();

                securePrefs = EncryptedSharedPreferences.create(
                        appContext,
                        SECURE_PREFS_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize EncryptedSharedPreferences, falling back to standard prefs", e);
                return prefs;
            }
        }
        return securePrefs;
    }

    public boolean isGeminiSelected() {
        return prefs.getBoolean(KEY_IS_GEMINI, false);
    }

    public void setGeminiSelected(boolean selected) {
        prefs.edit().putBoolean(KEY_IS_GEMINI, selected).apply();
    }

    public String getModelName() {
        return prefs.getString(KEY_MODEL_NAME, "ggml-base.bin");
    }

    public void setModelName(String modelName) {
        prefs.edit().putString(KEY_MODEL_NAME, modelName).apply();
    }

    public String getGeminiModelName() {
        return prefs.getString(KEY_GEMINI_MODEL_NAME, "auto");
    }

    public void setGeminiModelName(String modelName) {
        prefs.edit().putString(KEY_GEMINI_MODEL_NAME, modelName).apply();
    }

    public String getGeminiApiKey() {
        SharedPreferences secure = getSecurePrefs();
        String secureKey = secure.getString(KEY_GEMINI_API_KEY, null);

        if (secureKey != null) {
            return secureKey;
        }

        // Migration from legacy unencrypted SharedPreferences if exists
        if (prefs.contains(KEY_GEMINI_API_KEY)) {
            String legacyKey = prefs.getString(KEY_GEMINI_API_KEY, "");
            if (!legacyKey.isEmpty()) {
                secure.edit().putString(KEY_GEMINI_API_KEY, legacyKey).apply();
            }
            prefs.edit().remove(KEY_GEMINI_API_KEY).apply();
            return legacyKey;
        }

        return "";
    }

    public void setGeminiApiKey(String key) {
        getSecurePrefs().edit().putString(KEY_GEMINI_API_KEY, key != null ? key : "").apply();
        // Remove from legacy unencrypted prefs if present
        if (prefs.contains(KEY_GEMINI_API_KEY)) {
            prefs.edit().remove(KEY_GEMINI_API_KEY).apply();
        }
    }

    public boolean isTutorialShown() {
        return prefs.getBoolean(KEY_TUTORIAL_SHOWN, false);
    }

    public void setTutorialShown(boolean shown) {
        prefs.edit().putBoolean(KEY_TUTORIAL_SHOWN, shown).apply();
    }

    public String getLanguage() {
        return prefs.getString(KEY_LANGUAGE, "auto");
    }

    public void setLanguage(String language) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
    }
}
