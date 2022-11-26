package processing.mode.java.lsp;

import org.eclipse.lsp4j.services.WorkspaceService;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;

import java.net.URI;
import java.io.IOException;

class PdeWorkspaceService implements WorkspaceService {
  PdeLanguageServer pls;
  PdeWorkspaceService(PdeLanguageServer pls) {
    this.pls = pls;
  }

  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params) {
  }

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    System.out.println("didChangeWatchedFiles: " + params);
    for (var change : params.getChanges()) {
      URI uri = URI.create(change.getUri());
      pls.getAdapter(uri).ifPresent(adapter -> {
        switch (change.getType()) {
          case Created -> PdeAdapter.uriToPath(uri).ifPresent(path -> {
            adapter.sketch.loadNewTab(path.getName(), "pde", true);
            adapter.notifySketchChanged();
          });
          case Changed -> adapter.findCodeByUri(uri).ifPresent(code -> {
            try {
              code.load();
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
            adapter.notifySketchChanged();
          });
          case Deleted -> adapter.findCodeByUri(uri).ifPresent(code -> {
            adapter.sketch.removeCode(code);
            adapter.notifySketchChanged();
          });
        }
      });
    }
  }
}
