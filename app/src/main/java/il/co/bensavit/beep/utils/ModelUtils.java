package il.co.bensavit.beep.utils;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

import il.co.bensavit.beep.R;

public class ModelUtils {
  public static String formatModelDisplayName(Context context, String modelId) {
    if (modelId == null || modelId.isEmpty()) return "";
    String cleanModelId = modelId.startsWith("MODEL_SELECTED:") 
        ? modelId.substring("MODEL_SELECTED:".length()) 
        : modelId;
    cleanModelId = new File(cleanModelId.trim()).getName();
    if ("auto".equals(cleanModelId)) return context.getString(R.string.gemini_auto);

    String name = cleanModelId.replace(".bin", "").replace("ggml-", "").replace("-", " ").replace("_", " ");
    String[] parts = name.split(" ");
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (!part.isEmpty()) {
        sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
      }
    }
    return sb.toString().trim();
  }

  public static boolean isTinyModel(String modelName) {
    return modelName != null && modelName.toLowerCase().contains("tiny");
  }

  public static String formatModelSubtitle(Context context, String modelName, boolean isOnlineMode, boolean isReady) {
    if (isOnlineMode) {
      String name = (modelName == null || modelName.trim().isEmpty()) 
          ? context.getString(R.string.mode_online) 
          : modelName;
      if (isReady) {
        return context.getString(R.string.model_subtitle, name);
      } else {
        String status = context.getString(R.string.model_status_not_configured);
        return context.getString(R.string.model_subtitle_format, name, status);
      }
    } else {
      if (modelName == null || modelName.trim().isEmpty()) {
        return context.getString(R.string.model_none_loaded);
      }
      String whisperModelName = modelName.toLowerCase().startsWith("whisper") 
          ? modelName 
          : "Whisper " + modelName;
      if (isReady) {
        return context.getString(R.string.model_subtitle, whisperModelName);
      } else {
        String status = context.getString(R.string.model_status_not_loaded);
        return context.getString(R.string.model_subtitle_format, whisperModelName, status);
      }
    }
  }

  public static String getFileSha1(File file) {
    try (InputStream is = new FileInputStream(file)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      byte[] buffer = new byte[8192];
      int read;
      while ((read = is.read(buffer)) > 0) {
        digest.update(buffer, 0, read);
      }
      byte[] hash = digest.digest();
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      Logger.e("ModelUtils", "Error calculating SHA1", e);
      return null;
    }
  }

  public static String getModelLanguage(Context context, String modelName) {
    if (modelName == null || modelName.isEmpty()) return null;
    String fileName = new File(modelName.trim()).getName();
    int dotIndex = fileName.lastIndexOf('.');
    if (dotIndex > 0) {
      fileName = fileName.substring(0, dotIndex);
    }
    String[] tokens = fileName.split("[._-]");
    if (context != null) {
      String[] languageCodes = context.getResources().getStringArray(R.array.language_codes);
      for (String token : tokens) {
        String candidate = token.toLowerCase();
        if ("auto".equalsIgnoreCase(candidate)) continue;
        for (String code : languageCodes) {
          if (code.equalsIgnoreCase(candidate)) {
            return candidate;
          }
        }
      }
    } else {
      for (String token : tokens) {
        String candidate = token.toLowerCase();
        if (candidate.matches("^[a-z]{2}$") && !"bin".equals(candidate)) {
          return candidate;
        }
      }
    }
    return null;
  }

  public static boolean isLanguageConflicting(Context context, String modelName, String languageCode) {
    if (languageCode == null || languageCode.isEmpty() || "auto".equalsIgnoreCase(languageCode)) {
      return false;
    }
    String modelLang = getModelLanguage(context, modelName);
    if (modelLang == null) {
      return false;
    }
    return !modelLang.equalsIgnoreCase(languageCode);
  }
}
