package processing.mode.java;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import processing.app.Problem;
import processing.app.ui.Editor;
import processing.mode.java.preproc.PdePreprocessIssue;


/**
 * Factory which helps create {Problem}s during preprocessing.
 */
public class ProblemFactory {

  /**
   * Create a new {Problem}.
   *
   * @param pdePreprocessIssue The preprocess issue found.
   * @param tabStarts The list of line numbers on which each tab starts.
   * @return Newly created problem.
   */
  public static Problem build(PdePreprocessIssue pdePreprocessIssue, List<Integer> tabStarts) {
    int line = pdePreprocessIssue.getLine();

    TabLine tabLine = getTab(tabStarts, line);

    int tab = tabLine.getTab();
    int localLine = tabLine.getLineInTab();
    int col = pdePreprocessIssue.getCharPositionInLine();

    String message = pdePreprocessIssue.getMsg();

    if (col == 0) {
      col = 1;
    }

    return new SyntaxProblem(
        tab,
        localLine,
        message,
        0,
        col
    );
  }

  /**
   * Get the local tab and line number for a global line.
   *
   * @param tabStarts The lines on which each tab starts.
   * @param line The global line to locate as a local line.
   * @return The local tab number and local line number.
   */
  protected static TabLine getTab(List<Integer> tabStarts, int line) {
    OptionalInt tabMaybe = IntStream.range(0, tabStarts.size())
        .filter((index) -> line >= tabStarts.get(index))
        .max();

    int tab = tabMaybe.orElse(0);

    int localLine = line - tabStarts.get(tab);

    return new TabLine(tab, line, localLine);
  }

}
