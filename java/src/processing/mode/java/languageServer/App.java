package processing.mode.java.languageServer;

import org.eclipse.lsp4j.launch.LSPLauncher;
import java.io.File;
import java.net.ServerSocket;
import java.io.InputStream;
import java.io.OutputStream;

public class App {
  public static void main(String[] args) {
    var input = System.in;
    var output = System.out;
    System.setOut(System.err);

    var server = new ProcessingLanguageServer();
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
