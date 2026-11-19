package il.co.bensavit.beep.bean;

public class WhisperSegment {
  private final long start;
  private final long end;
  private final String sentence;

  public WhisperSegment(long start, long end, String sentence) {
    this.start = start;
    this.end = end;
    this.sentence = sentence;
  }

  public long getStart() {
    return start;
  }

  public long getEnd() {
    return end;
  }

  public String getSentence() {
    return sentence;
  }

  @Override
  public String toString() {
    return sentence;
  }
}
