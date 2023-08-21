package processing.mode.java.lsp;

import java.io.File;
import java.net.URI;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.TextEdit;
import org.jsoup.Jsoup;

import processing.app.Base;
import processing.app.contrib.ModeContribution;
import processing.app.Platform;
import processing.app.Preferences;
import processing.app.Problem;
import processing.app.Sketch;
import processing.app.SketchCode;
import processing.mode.java.AutoFormat;
import processing.mode.java.CompletionCandidate;
import processing.mode.java.CompletionGenerator;
import processing.mode.java.ErrorChecker;
import processing.mode.java.JavaMode;
import processing.mode.java.JavaTextArea;
import processing.mode.java.PreprocService;
import processing.mode.java.PreprocSketch;

import static java.util.Arrays.copyOfRange;

class PdeAdapter {
  File rootPath;
  LanguageClient client;
  JavaMode javaMode;
  File pdeFile;
  Sketch sketch;
  CompletionGenerator completionGenerator;
  PreprocService preprocService;
  ErrorChecker errorChecker;
  CompletableFuture<PreprocSketch> cps;
  CompletionGenerator suggestionGenerator;
  Set<URI> prevDiagnosticReportUris = new HashSet<>();
  PreprocSketch ps;
  

  PdeAdapter(File rootPath, LanguageClient client) {
    this.rootPath = rootPath;
    this.client = client;

    File location = Platform.getContentFile("modes/java");
    ModeContribution mc =
      ModeContribution.load(null, location, JavaMode.class.getName());
    if (mc == null) {
      // Shouldn't be possible but IntelliJ is complaining about it,
      // and we may run into path issues when running externally [fry 221126]
      throw new RuntimeException("Could not load Java Mode from " + location);
    }
    javaMode = (JavaMode) mc.getMode();

    pdeFile = new File(rootPath, rootPath.getName() + ".pde");
    sketch = new Sketch(pdeFile.toString(), javaMode);
    completionGenerator = new CompletionGenerator(javaMode);
    preprocService = new PreprocService(javaMode, sketch);
    errorChecker = new ErrorChecker(this::updateProblems, preprocService);
    cps = CompletableFutures.computeAsync(_x -> {
      throw new RuntimeException("unreachable");
    });
    suggestionGenerator = new CompletionGenerator(javaMode);

    notifySketchChanged();
  }


  static Optional<File> uriToPath(URI uri) {
    try {
      return Optional.of(new File(uri));
    } catch (Exception e) {
      return Optional.empty();
    }
  }


  static URI pathToUri(File path) {
    return path.toURI();
  }


  static Offset toLineCol(String s, int offset) {
    int line = (int)s.substring(0, offset).chars().filter(c -> c == '\n').count();
    int col = offset - s.substring(0, offset).lastIndexOf('\n');
    return new Offset(line, col);
  }

  static Offset toLineEndCol(String s, int offset) {
    Offset before = toLineCol(s, offset);
    return new Offset(before.line, Integer.MAX_VALUE);
  }

  
  /**
   * Converts a tabOffset to a position within a tab
   * @param program current code(text) from a tab
   * @param tabOffset character offset inside a tab
   * @return Position(line and col) within the tab
   */
  static Position toPosition(String program, int tabOffset){
    Offset offset = toLineCol(program, tabOffset);
    return new Position(offset.line, offset.col-1);
  }
  
  
  /**
   * Converts a range (start to end offset) to a location.
   * @param program current code(text) from a tab
   * @param startTabOffset starting character offset inside a tab
   * @param stopTabOffset ending character offset inside a tab
   * @param uri uri from a tab
   * @return Range inside a file
   */
  static Location toLocation(
    String program,
    int startTabOffset,
    int stopTabOffset,
    URI uri
  ){
    Position startPos = toPosition(program, startTabOffset);
    Position stopPos = toPosition(program, stopTabOffset);
    
    Range range = new Range(startPos, stopPos);
    return new Location(uri.toString(), range);
  }

  
  static void init() {
    Base.setCommandLine();
    Platform.init();
    Preferences.init();
  }

  void notifySketchChanged() {
    CompletableFuture<PreprocSketch> cps = new CompletableFuture<>();
    this.cps = cps;
    preprocService.notifySketchChanged();
    errorChecker.notifySketchChanged();
    preprocService.whenDone(cps::complete);
    try { ps = cps.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  Optional<SketchCode> findCodeByUri(URI uri) {
    return PdeAdapter.uriToPath(uri)
      .flatMap(path -> Arrays.stream(sketch.getCode())
        .filter(code -> code.getFile().equals(path))
        .findFirst()
      );
  }

  
  /**
   * Looks for the tab number for a given text
   * @param code text(code) from a tab
   * @return tabIndex where the code belongs to, or empty
   */
  public Optional<Integer> findTabIndex(SketchCode code){
    int tabsCount = sketch.getCodeCount();
    java.util.OptionalInt optionalTabIndex;
      optionalTabIndex =  IntStream.range(0, tabsCount)
        .filter(i -> sketch.getCode(i).equals(code))
        .findFirst();
    
    if(optionalTabIndex.isEmpty()){
      return Optional.empty();
    }
    
    return Optional.of(optionalTabIndex.getAsInt());
  }
  
  
  /**
   * Looks for the javaOffset, this offset is the character position inside the
   * full java file. The position can be used by the AST to find a node.
   * @param uri uri of the file(tab) where to look
   * @param line line number
   * @param col column number
   * @return character offset within the full AST
   */
  public Optional<Integer> findJavaOffset(URI uri, int line, int col){
    
    Optional<SketchCode> optionalCode =  findCodeByUri(uri);
    if(optionalCode.isEmpty()){
      System.out.println("couldn't find sketch code");
      return Optional.empty();
    }
    SketchCode code = optionalCode.get();
    
    Optional<Integer> optionalTabIndex = findTabIndex(code);
    if (optionalTabIndex.isEmpty()){
      System.out.println("couldn't find tab index");
      return  Optional.empty();
    }
    int tabIndex = optionalTabIndex.get();
    
    String[] codeLines = copyOfRange(code.getProgram().split("\n"), 0,line);
    String codeString = String.join("\n", codeLines);
    int tabOffset = codeString.length() + col;
    
    return Optional.of(ps.tabOffsetToJavaOffset(tabIndex, tabOffset));
  }

  
   void updateProblems(List<Problem> problems) {
      Map<URI, List<Diagnostic>> dias = problems.stream()
      .map(prob -> {
        SketchCode code = sketch.getCode(prob.getTabIndex());

        int startOffset = prob.getStartOffset();
        int endOffset = prob.getStopOffset();

        Position startPosition = new Position(
          prob.getLineNumber(),
          PdeAdapter
            .toLineCol(code.getProgram(), startOffset)
            .col - 1
        );

        Position stopPosition;
        if (endOffset == -1) {
          stopPosition = new Position(
            prob.getLineNumber(),
            PdeAdapter
              .toLineEndCol(code.getProgram(), startOffset)
              .col - 1
          );
        } else {
          stopPosition = new Position(
            prob.getLineNumber(),
            PdeAdapter
              .toLineCol(code.getProgram(), endOffset)
              .col - 1
          );
        }

        Diagnostic dia = new Diagnostic(
          new Range(startPosition, stopPosition),
          prob.getMessage()
        );
        dia.setSeverity(
          prob.isError()
            ? DiagnosticSeverity.Error
            : DiagnosticSeverity.Warning
        );
        return new AbstractMap.SimpleEntry<>(
          PdeAdapter.pathToUri(code.getFile()),
          dia
        );
      })
      .collect(Collectors.groupingBy(
        AbstractMap.SimpleEntry::getKey,
        Collectors.mapping(
          AbstractMap.SimpleEntry::getValue,
          Collectors.toList()
        )
      ));

    for (Map.Entry<URI, List<Diagnostic>> entry : dias.entrySet()) {
      PublishDiagnosticsParams params = new PublishDiagnosticsParams();
      params.setUri(entry.getKey().toString());
      params.setDiagnostics(entry.getValue());
      client.publishDiagnostics(params);
    }

    for (URI uri : prevDiagnosticReportUris) {
      if (!dias.containsKey(uri)) {
        PublishDiagnosticsParams params = new PublishDiagnosticsParams();
        params.setUri(uri.toString());
        params.setDiagnostics(Collections.emptyList());
        client.publishDiagnostics(params);
      }
    }
    prevDiagnosticReportUris = dias.keySet();
  }

  CompletionItem convertCompletionCandidate(CompletionCandidate c) {
    CompletionItem item = new CompletionItem();
    item.setLabel(c.getElementName());
    item.setInsertTextFormat(InsertTextFormat.Snippet);
    String insert = c.getCompletionString();
    if (insert.contains("( )")) {
      insert = insert.replace("( )", "($1)");
    } else if (insert.contains(",")) {
      int n = 1;
      char[] chs = insert.replace("(,", "($1,").toCharArray();
      //insert = "";
      StringBuilder newInsert = new StringBuilder();
      for (char ch : chs) {
        if (ch == ',') {
          n += 1;
          //insert += ",$" + n;
          newInsert.append(",$").append(n);
        }
        //insert += ch;
        newInsert.append(ch);
      }
      insert = newInsert.toString();
    }
    item.setInsertText(insert);
    CompletionItemKind kind = switch (c.getType()) {
      case 0 -> // PREDEF_CLASS
        CompletionItemKind.Class;
      case 1 -> // PREDEF_FIELD
        CompletionItemKind.Constant;
      case 2 -> // PREDEF_METHOD
        CompletionItemKind.Function;
      case 3 -> // LOCAL_CLASS
        CompletionItemKind.Class;
      case 4 -> // LOCAL_METHOD
        CompletionItemKind.Method;
      case 5 -> // LOCAL_FIELD
        CompletionItemKind.Field;
      case 6 -> // LOCAL_VARIABLE
        CompletionItemKind.Variable;
      default -> throw new IllegalArgumentException("Unknown completion type: " + c.getType());
    };
    item.setKind(kind);
    item.setDetail(Jsoup.parse(c.getLabel()).text());
    return item;
   }

  Optional<String> parsePhrase(String text) {
    return Optional.ofNullable(JavaTextArea.parsePhrase(text));
  }

  List<CompletionCandidate> filterPredictions(
    List<CompletionCandidate> candidates
  ) {
    return Collections.list(CompletionGenerator.filterPredictions(candidates).elements());
  }

  CompletableFuture<List<CompletionItem>> generateCompletion(
    URI uri,
    int line,
    int col
  ) {
    return cps.thenApply(ps -> {
      Optional<List<CompletionItem>> result =
        findCodeByUri(uri)
          .flatMap(code -> {
            int codeIndex = IntStream.range(0, sketch.getCodeCount())
              .filter(i -> sketch.getCode(i).equals(code))
              .findFirst()
              .getAsInt();
            int lineStartOffset = String.join(
                "\n",
                Arrays.copyOfRange(code.getProgram().split("\n"), 0, line + 1)
              )
              .length();
            int lineNumber = ps.tabOffsetToJavaLine(codeIndex, lineStartOffset);

            String text = code.getProgram()
              .split("\n")[line] // TODO: 範囲外のエラー処理
              .substring(0, col);
            return parsePhrase(text)
              .map(phrase -> {
                System.out.println("phrase: " + phrase);
                System.out.println("lineNumber: " + lineNumber);
                return Optional.ofNullable(
                  suggestionGenerator
                    .preparePredictions(ps, phrase, lineNumber)
                )
                  .filter(x -> !x.isEmpty())
                  .map(candidates -> {
                    Collections.sort(candidates);
                    System.out.println("candidates: " + candidates);
                    List<CompletionCandidate> filtered = filterPredictions(candidates);
                    System.out.println("filtered: " + filtered);
                    return filtered.stream()
                      .map(this::convertCompletionCandidate)
                      .collect(Collectors.toList());
                  });
              })
              .orElse(Optional.empty());
          });

      return result.orElse(Collections.emptyList());
    });
  }

  void onChange(URI uri, String text) {
    findCodeByUri(uri)
      .ifPresent(code -> {
        code.setProgram(text);
        notifySketchChanged();
      });
  }

  Optional<TextEdit> format(URI uri) {
    return findCodeByUri(uri)
      .map(SketchCode::getProgram)
      .map(code -> {
        String newCode = new AutoFormat().format(code);
        Offset end = PdeAdapter.toLineCol(code, code.length());
        return new TextEdit(
          new Range(
            new Position(0, 0),
            new Position(end.line, end.col)
          ),
          newCode
        );
      });
  }


  static class Offset {
    int line;
    int col;

    Offset(int line, int col) {
      this.line = line;
      this.col = col;
    }
  }
}
