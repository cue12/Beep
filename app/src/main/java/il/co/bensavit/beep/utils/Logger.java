package il.co.bensavit.beep.utils;

import android.util.Log;
import java.util.concurrent.CopyOnWriteArrayList;

public class Logger {
    private static final StringBuilder logBuffer = new StringBuilder();
    private static final CopyOnWriteArrayList<LogListener> listeners = new CopyOnWriteArrayList<>();

    public interface LogListener {
        void onLog(String msg);
    }

    public static void i(String tag, String msg) {
        log(tag, msg, Log.INFO);
    }

    public static void e(String tag, String msg) {
        log(tag, msg, Log.ERROR);
    }

    public static void e(String tag, String msg, Throwable t) {
        log(tag, msg + "\n" + Log.getStackTraceString(t), Log.ERROR);
    }

    public static void w(String tag, String msg) {
        log(tag, msg, Log.WARN);
    }

    public static void d(String tag, String msg) {
        log(tag, msg, Log.DEBUG);
    }

    private static void log(String tag, String msg, int level) {
        String line = "[" + tag + "] " + msg + "\n";
        logBuffer.append(line);
        Log.println(level, tag, msg);
        for (LogListener listener : listeners) {
            listener.onLog(line);
        }
    }

    public static String getLogs() {
        return logBuffer.toString();
    }

    public static void clear() {
        logBuffer.setLength(0);
    }

    public static void addListener(LogListener listener) {
        listeners.add(listener);
    }

    public static void removeListener(LogListener listener) {
        listeners.remove(listener);
    }
}
