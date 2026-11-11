package il.co.bensavit.beep.data;

import java.io.File;

public class HistoryItem {
    private final File file;
    private final String duration;
    private final long date;

    public HistoryItem(File file, String duration, long date) {
        this.file = file;
        this.duration = duration;
        this.date = date;
    }

    public File getFile() {
        return file;
    }

    public String getDuration() {
        return duration;
    }

    public long getDate() {
        return date;
    }

    public String getName() {
        return file.getName();
    }
}
