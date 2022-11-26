package processing.mode.java.lsp;

import org.eclipse.lsp4j.launch.LSPLauncher;

public class App {
  public static void main(String[] args) {
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
