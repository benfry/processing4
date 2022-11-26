package processing.mode.java.lsp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import java.util.List;
import processing.app.Base;
import processing.app.Platform;
import processing.app.Preferences;
import processing.app.contrib.ModeContribution;
import processing.mode.java.JavaMode;
import java.io.File;
import processing.app.Sketch;
import processing.mode.java.CompletionGenerator;
import processing.mode.java.PreprocService;
import org.eclipse.lsp4j.services.LanguageClient;
import processing.mode.java.ErrorChecker;
import processing.app.Problem;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.DiagnosticSeverity;
import processing.mode.java.PreprocSketch;
import processing.mode.java.JavaTextArea;
import java.util.Collections;
import processing.mode.java.CompletionCandidate;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.CompletionItemKind;
import org.jsoup.Jsoup;
import java.net.URI;
import processing.app.SketchCode;
import org.eclipse.lsp4j.TextEdit;
import processing.mode.java.AutoFormat;
import java.util.Optional;
import java.util.HashSet;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Map;
import java.util.AbstractMap;
import java.util.Set;

class Offset {
  int line;
  int col;

  Offset(int line, int col) {
    this.line = line;
    this.col = col;
  }
}

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
  Set<URI> prevDiagnosticReportUris = new HashSet<URI>();
  

  PdeAdapter(File rootPath, LanguageClient client) {
    this.rootPath = rootPath;
    this.client = client;
    this.javaMode = (JavaMode) ModeContribution
      .load(
        null,
        Platform.getContentFile("modes/java"),
        "processing.mode.java.JavaMode"
      )
      .getMode();
    this.pdeFile = new File(rootPath, rootPath.getName() + ".pde");
    this.sketch = new Sketch(pdeFile.toString(), javaMode);
    this.completionGenerator = new CompletionGenerator(javaMode);
    this.preprocService = new PreprocService(javaMode, sketch);
    this.errorChecker = new ErrorChecker(
      this::updateProblems,
      preprocService
    );
    this.cps = CompletableFutures.computeAsync(_x -> {
      throw new RuntimeException("unreachable");
    });
    this.suggestionGenerator = new CompletionGenerator(this.javaMode);
    
    this.notifySketchChanged();
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

  static void init() {
    Base.setCommandLine();
    Platform.init();
    Preferences.init();
  }

  void notifySketchChanged() {
    CompletableFuture<PreprocSketch> cps = new CompletableFuture<PreprocSketch>();
    this.cps = cps;
    preprocService.notifySketchChanged();
    errorChecker.notifySketchChanged();
    preprocService.whenDone(ps -> {
      cps.complete(ps);
    });
  }

   Optional<SketchCode> findCodeByUri(URI uri) {
    return PdeAdapter.uriToPath(uri)
      .flatMap(path -> Arrays.stream(sketch.getCode())
        .filter(code -> code.getFile().equals(path))
        .findFirst()
      );
  }

   void updateProblems(List<Problem> probs) {
      Map<URI, List<Diagnostic>> dias = probs.stream()
      .map(prob -> {
        SketchCode code = sketch.getCode(prob.getTabIndex());
        Diagnostic dia = new Diagnostic(
          new Range(
            new Position(
              prob.getLineNumber(),
              PdeAdapter
                .toLineCol(code.getProgram(), prob.getStartOffset())
                .col - 1
            ),
            new Position(
              prob.getLineNumber(),
              PdeAdapter
                .toLineCol(code.getProgram(), prob.getStopOffset())
                .col - 1
            )
          ),
          prob.getMessage()
        );
        dia.setSeverity(
          prob.isError()
            ? DiagnosticSeverity.Error
            : DiagnosticSeverity.Warning
        );
        return new AbstractMap.SimpleEntry<URI, Diagnostic>(
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
      insert = "";
      for (char ch : chs) {
        switch (ch) {
          case ',': {
            n += 1;
            insert += ",$" + n;
          }
          default: insert += ch;
        }
      }
    }
    item.setInsertText(insert);
    CompletionItemKind kind;
    switch (c.getType()) {
      case 0: // PREDEF_CLASS
        kind = CompletionItemKind.Class;
        break;
      case 1: // PREDEF_FIELD
        kind = CompletionItemKind.Constant;
        break;
      case 2: // PREDEF_METHOD
        kind = CompletionItemKind.Function;
        break;
      case 3: // LOCAL_CLASS
        kind = CompletionItemKind.Class;
        break;
      case 4: // LOCAL_METHOD
        kind = CompletionItemKind.Method;
        break;
      case 5: // LOCAL_FIELD
        kind = CompletionItemKind.Field;
        break;
      case 6: // LOCAL_VARIABLE
        kind = CompletionItemKind.Variable;
        break;
      default:
        throw new IllegalArgumentException("Unknown completion type: " + c.getType());
    }
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
}
