/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
Part of the Processing project - http://processing.org
Copyright (c) 2012-23 The Processing Foundation

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License version 2
as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software Foundation, Inc.
51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
*/

package processing.mode.java;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.compiler.IProblem;

import com.google.classpath.ClassPath;
import com.google.classpath.ClassPathFactory;
import com.google.classpath.RegExpResourceFilter;

import processing.app.Language;
import processing.app.Problem;


public class ErrorChecker {
  // Delay delivering error check result after last sketch change
  // https://github.com/processing/processing/issues/2677
  private final static long DELAY_BEFORE_UPDATE = 650;

  final private ScheduledExecutorService scheduler;
  private volatile ScheduledFuture<?> scheduledUiUpdate = null;
  private volatile long nextUiUpdate = 0;
  private volatile boolean enabled;

  private final Consumer<PreprocSketch> errorHandlerListener =
    this::handleSketchProblems;

  final private Consumer<List<Problem>> editor;
  final private PreprocService pps;


  public ErrorChecker(Consumer<List<Problem>> editor, PreprocService pps) {
    this.editor = editor;
    this.pps = pps;

    scheduler = Executors.newSingleThreadScheduledExecutor();
    enabled = JavaMode.errorCheckEnabled;
    if (enabled) {
      pps.registerListener(errorHandlerListener);
    }
  }


  public void notifySketchChanged() {
    nextUiUpdate = System.currentTimeMillis() + DELAY_BEFORE_UPDATE;
  }


  public void preferencesChanged() {
    if (enabled != JavaMode.errorCheckEnabled) {
      enabled = JavaMode.errorCheckEnabled;
      if (enabled) {
        pps.registerListener(errorHandlerListener);
      } else {
        pps.unregisterListener(errorHandlerListener);
        editor.accept(Collections.emptyList());
        nextUiUpdate = 0;
      }
    }
  }


  public void dispose() {
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
  }


  private void handleSketchProblems(PreprocSketch ps) {
    Map<String, String[]> suggestCache =
      JavaMode.importSuggestEnabled ? new HashMap<>() : Collections.emptyMap();

    List<IProblem> iproblems;
    if (ps.compilationUnit == null) {
      iproblems = new ArrayList<>();
    } else {
      iproblems = ps.iproblems;
    }

    final List<Problem> problems = new ArrayList<>(ps.otherProblems);

    if (problems.isEmpty()) { // Check for curly quotes
      List<JavaProblem> curlyQuoteProblems = checkForCurlyQuotes(ps);
      problems.addAll(curlyQuoteProblems);
    }

    if (problems.isEmpty()) {
      AtomicReference<ClassPath> searchClassPath =
        new AtomicReference<>(null);

      List<Problem> cuProblems = iproblems.stream()
        // Filter Warnings if they are not enabled
        .filter(iproblem -> !(iproblem.isWarning() && !JavaMode.warningsEnabled))
        .filter(iproblem -> !(isIgnorableProblem(iproblem)))
        // Transform into our Problems
        .map(iproblem -> {
          JavaProblem p = convertIProblem(iproblem, ps);

          // Handle import suggestions
          if (p != null && JavaMode.importSuggestEnabled && isUndefinedTypeProblem(iproblem)) {
            ClassPath cp = searchClassPath.updateAndGet(prev -> prev != null ?
                prev : new ClassPathFactory().createFromPaths(ps.searchClassPathArray));
            String[] s = suggestCache.computeIfAbsent(iproblem.getArguments()[0],
                                                   name -> getImportSuggestions(cp, name));
            p.setImportSuggestions(s);
          }
          return p;
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

      problems.addAll(cuProblems);
    }

    if (scheduledUiUpdate != null) {
      scheduledUiUpdate.cancel(true);
    }
    // https://github.com/processing/processing/issues/2677
    long delay = nextUiUpdate - System.currentTimeMillis();
    Runnable uiUpdater = () -> {
      if (nextUiUpdate > 0 && System.currentTimeMillis() >= nextUiUpdate) {
        EventQueue.invokeLater(() -> editor.accept(problems));
      }
    };
    scheduledUiUpdate =
      scheduler.schedule(uiUpdater, delay, TimeUnit.MILLISECONDS);
  }


  /**
   * Determine if a problem can be suppressed from the user.
   *
   * <p>
   * Determine if one can ignore an errors where an ignorable error is one
   * "fixed" in later pipeline steps but can make JDT angry or do not actually
   * cause issues when reaching javac.
   * </p>
   *
   * @return True if ignorable and false otherwise.
   */
  static private boolean isIgnorableProblem(IProblem iproblem) {
    String message = iproblem.getMessage();

    // Hide a useless error which is produced when a line ends with
    // an identifier without a semicolon. "Missing a semicolon" is
    // also produced and is preferred over this one.
    // (Syntax error, insert ":: IdentifierOrNew" to complete Expression)
    // See: https://bugs.eclipse.org/bugs/show_bug.cgi?id=405780
    boolean ignorable =
      message.contains("Syntax error, insert \":: IdentifierOrNew\"");

    // It's ok if the file names do not line up during preprocessing.
    ignorable |= message.contains("must be defined in its own file");

    return ignorable;
  }


  static private JavaProblem convertIProblem(IProblem iproblem, PreprocSketch ps) {
    SketchInterval in = ps.mapJavaToSketch(iproblem);
    if (in != SketchInterval.BEFORE_START) {
      String badCode = ps.getPdeCode(in);
      int line = ps.tabOffsetToTabLine(in.tabIndex, in.startTabOffset);
      JavaProblem p = JavaProblem.fromIProblem(iproblem, in.tabIndex, line, badCode);
      p.setPDEOffsets(0, -1);
      return p;
    }
    return null;
  }


  static private boolean isUndefinedTypeProblem(IProblem iproblem) {
    int id = iproblem.getID();
    return id == IProblem.UndefinedType ||
        id == IProblem.UndefinedName ||
        id == IProblem.UnresolvedVariable;
  }


  static private boolean isMissingBraceProblem(IProblem iproblem) {
    if (iproblem.getID() == IProblem.ParsingErrorInsertToComplete) {
      char brace = iproblem.getArguments()[0].charAt(0);
      return brace == '{' || brace == '}';

    } else if (iproblem.getID() == IProblem.ParsingErrorInsertTokenAfter) {
      char brace = iproblem.getArguments()[1].charAt(0);
      return brace == '{' || brace == '}';
    }
    return false;
  }


  static private final Pattern CURLY_QUOTE_REGEX =
    Pattern.compile("([“”‘’])", Pattern.UNICODE_CHARACTER_CLASS);

  /**
   * Check the scrubbed code for curly quotes.
   * They are a common copy/paste error, especially on macOS.
   */
  static private List<JavaProblem> checkForCurlyQuotes(PreprocSketch ps) {
    if (ps.compilationUnit == null) {
      return new ArrayList<>();
    }

    List<JavaProblem> problems = new ArrayList<>(0);

    Matcher matcher = CURLY_QUOTE_REGEX.matcher(ps.scrubbedPdeCode);
    while (matcher.find()) {
      int pdeOffset = matcher.start();
      String q = matcher.group();

      int tabIndex = ps.pdeOffsetToTabIndex(pdeOffset);
      int tabOffset = ps.pdeOffsetToTabOffset(tabIndex, pdeOffset);
      int tabLine = ps.tabOffsetToTabLine(tabIndex, tabOffset);

      String message = Language.interpolate("editor.status.bad_curly_quote", q);
      JavaProblem problem = new JavaProblem(message, JavaProblem.ERROR, tabIndex, tabLine);

      problems.add(problem);
    }

    // Go through iproblems and look for problems involving curly quotes
    List<JavaProblem> problems2 = new ArrayList<>(0);
    IProblem[] iproblems = ps.compilationUnit.getProblems();

    for (IProblem iproblem : iproblems) {
      switch (iproblem.getID()) {
        case IProblem.ParsingErrorDeleteToken,
             IProblem.ParsingErrorDeleteTokens,
             IProblem.ParsingErrorInvalidToken,
             IProblem.ParsingErrorReplaceTokens,
             IProblem.UnterminatedString -> {
          SketchInterval in = ps.mapJavaToSketch(iproblem);
          if (in == SketchInterval.BEFORE_START) continue;
          String badCode = ps.getPdeCode(in);
          matcher.reset(badCode);
          while (matcher.find()) {
            int offset = matcher.start();
            String q = matcher.group();
            int tabStart = in.startTabOffset + offset;
            int tabStop = tabStart + 1;
            int line = ps.tabOffsetToTabLine(in.tabIndex, tabStart);
            
            // Prevent duplicate problems
            boolean isDupe = problems.stream()
              .filter(p -> p.getTabIndex() == in.tabIndex)
              .filter(p -> p.getLineNumber() == line)
              .findAny()
              .isPresent();

            if (isDupe) {
              String message;
              if (iproblem.getID() == IProblem.UnterminatedString) {
                message = Language.interpolate("editor.status.unterm_string_curly", q);
              } else {
                message = Language.interpolate("editor.status.bad_curly_quote", q);
              }
              JavaProblem p = new JavaProblem(message, JavaProblem.ERROR, in.tabIndex, line);
              problems2.add(p);
            }
          }
        }
      }
    }
    problems.addAll(problems2);
    return problems;
  }


  static public String[] getImportSuggestions(ClassPath cp, String className) {
    className = className.replace("[", "\\[").replace("]", "\\]");
    RegExpResourceFilter filter = new RegExpResourceFilter(
        Pattern.compile(".*"),
        Pattern.compile("(.*\\$)?" + className + "\\.class",
                        Pattern.CASE_INSENSITIVE));

    String[] resources = cp.findResources("", filter);
    return Arrays.stream(resources)
      // remove ".class" suffix
      .map(res -> res.substring(0, res.length() - 6))
      // replace path separators with dots
      .map(res -> res.replace('/', '.'))
      // replace inner class separators with dots
      .map(res -> res.replace('$', '.'))
      // sort, prioritize classes from java. package
      .map(res -> res.startsWith("classes.") ? res.substring(8) : res)
      .sorted((o1, o2) -> {
        // put java.* first, should be prioritized more
        boolean o1StartsWithJava = o1.startsWith("java");
        boolean o2StartsWithJava = o2.startsWith("java");
        if (o1StartsWithJava != o2StartsWithJava) {
          if (o1StartsWithJava) return -1;
          return 1;
        }
        return o1.compareTo(o2);
      })
      .toArray(String[]::new);
  }
}
