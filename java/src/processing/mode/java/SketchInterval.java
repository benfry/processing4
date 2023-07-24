package processing.mode.java;

public class SketchInterval {

  public static final SketchInterval BEFORE_START = new SketchInterval(-1, -1, -1, -1, -1);

  SketchInterval(int tabIndex,
                         int startTabOffset, int stopTabOffset,
                         int startPdeOffset, int stopPdeOffset) {
    this.tabIndex = tabIndex;
    this.startTabOffset = startTabOffset;
    this.stopTabOffset = stopTabOffset;
    this.startPdeOffset = startPdeOffset;
    this.stopPdeOffset = stopPdeOffset;
  }

  public final int tabIndex;
  public final int startTabOffset;
  public final int stopTabOffset;

  public final int startPdeOffset;
  public final int stopPdeOffset;
}