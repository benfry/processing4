package processing.mode.java.lsp;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageClient;


class PdeLanguageServer implements LanguageServer, LanguageClientAware {
  Map<File, PdeAdapter> adapters = new HashMap<>();
  LanguageClient client = null;
  PdeTextDocumentService textDocumentService = new PdeTextDocumentService(this);
  PdeWorkspaceService workspaceService = new PdeWorkspaceService(this);


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


  static Optional<String> lowerExtension(File file) {
    String s = file.toString();
    int dot = s.lastIndexOf('.');
    if (dot == -1) return Optional.empty();
    else return Optional.of(s.substring(dot + 1).toLowerCase());
  }


  Optional<PdeAdapter> getAdapter(URI uri) {
    return PdeAdapter.uriToPath(uri).filter(file -> {
      String ext = lowerExtension(file).orElse("");
      return ext.equals("pde") || ext.equals("java");
    }).map(file -> {
      File rootDir = file.getParentFile();
      return adapters.computeIfAbsent(rootDir, _k -> new PdeAdapter(rootDir, client));
    });
  }


  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    PdeAdapter.init();
    System.out.println("initialize");
    var capabilities = new ServerCapabilities();
    capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);

    var completionOptions = new CompletionOptions();
    completionOptions.setResolveProvider(true);
    completionOptions.setTriggerCharacters(List.of("."));
    capabilities.setCompletionProvider(completionOptions);
    capabilities.setDocumentFormattingProvider(true);
    capabilities.setDeclarationProvider(true);
    capabilities.setReferencesProvider(true);
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


  static public void main(String[] args) {
    var input = System.in;
    var output = System.out;
    System.setOut(System.err);

    var server = new PdeLanguageServer();
    var launcher =
      LSPLauncher.createServerLauncher(
        server,
        input,
        output
      );
    var client = launcher.getRemoteProxy();
    server.connect(client);
    launcher.startListening();
  }
}
