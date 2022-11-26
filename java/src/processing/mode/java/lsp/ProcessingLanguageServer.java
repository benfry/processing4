package processing.mode.java.lsp;


import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializeParams;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.CompletionOptions;

import java.io.File;

import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageClient;

import java.net.URI;
import java.util.Optional;
import java.util.HashMap;
import java.util.Arrays;

class ProcessingLanguageServer implements LanguageServer, LanguageClientAware {
  static Optional<String> lowerExtension(File file) {
    String s = file.toString();
    int dot = s.lastIndexOf('.');
    if (dot == -1) return Optional.empty();
    else return Optional.of(s.substring(dot + 1).toLowerCase());
  }

  HashMap<File, ProcessingAdapter> adapters = new HashMap<>();
  LanguageClient client = null;
  ProcessingTextDocumentService textDocumentService = new ProcessingTextDocumentService(this);
  ProcessingWorkspaceService workspaceService = new ProcessingWorkspaceService(this);

  @Override
  public void exit() {
    System.out.println("exit");
  }

  @Override
  public TextDocumentService getTextDocumentService() {
    return textDocumentService;
  }

  @Override
  public WorkspaceService getWorkspaceService() {
    return workspaceService;
  }

  Optional<ProcessingAdapter> getAdapter(URI uri) {
    return ProcessingAdapter.uriToPath(uri).filter(file -> {
      String ext = lowerExtension(file).orElse("");
      return ext.equals("pde") || ext.equals("java");
    }).map(file -> {
      File rootDir = file.getParentFile();
      return adapters.computeIfAbsent(rootDir, _k -> new ProcessingAdapter(rootDir, client));
    });
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    ProcessingAdapter.init();
    System.out.println("initialize");
    var capabilities = new ServerCapabilities();
    capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);

    var completionOptions = new CompletionOptions();
    completionOptions.setResolveProvider(true);
    completionOptions.setTriggerCharacters(
      Arrays.asList(
        "."
      )
    );
    capabilities.setCompletionProvider(completionOptions);
    capabilities.setDocumentFormattingProvider(true);
    var result = new InitializeResult(capabilities);
    return CompletableFuture.completedFuture(result);
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    System.out.println("shutdown");
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void connect(LanguageClient client) {
    this.client = client;
  }
}
