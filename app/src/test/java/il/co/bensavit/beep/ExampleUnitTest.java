package il.co.bensavit.beep;

import org.junit.Test;
import java.io.File;

import il.co.bensavit.beep.data.TaskStatus;

import static org.junit.Assert.*;

public class ExampleUnitTest {

    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void taskEntry_snapshotCopy_createsDistinctInstanceWithEqualContents() {
        File file = new File("/path/to/audio.wav");
        MainViewModel.TaskEntry original = new MainViewModel.TaskEntry(
                file, "key_1", TaskStatus.RUNNING, null, "work_1", 1000L, true, true, "model_a", 5000L
        );

        MainViewModel.TaskEntry snapshot = new MainViewModel.TaskEntry(original);

        assertNotSame("Snapshot must be a distinct object instance for DiffUtil", original, snapshot);
        assertEquals(original.taskKey, snapshot.taskKey);
        assertEquals(original.status, snapshot.status);
        assertEquals(original.resultText, snapshot.resultText);
        assertEquals(original.file, snapshot.file);
        assertEquals(original.modelName, snapshot.modelName);
        assertEquals(original.elapsedTimeMs, snapshot.elapsedTimeMs);
        assertEquals(original.workId, snapshot.workId);
        assertEquals(original.isSummaryExpanded, snapshot.isSummaryExpanded);
        assertEquals(original.isTranscriptionExpanded, snapshot.isTranscriptionExpanded);
        assertEquals(original.timestamp, snapshot.timestamp);
    }
}
