package processing.mode.java.languageServer;

import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.CompletionParams;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import java.util.List;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.TextEdit;
import java.io.File;
import processing.mode.java.AutoFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import java.util.Collections;
import processing.mode.java.CompletionGenerator;
import processing.mode.java.JavaTextArea;
import java.util.Arrays;
import processing.mode.java.CompletionCandidate;
import javax.swing.DefaultListModel;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.jsoup.Jsoup;
import org.eclipse.lsp4j.InsertTextFormat;
import java.net.URI;

class ProcessingTextDocumentService implements TextDocumentService {
  ProcessingLanguageServer pls;
  ProcessingTextDocumentService(ProcessingLanguageServer pls) {
    this.pls = pls;
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    System.out.println("didChange");
    URI uri = URI.create(params.getTextDocument().getUri());
    pls.getAdapter(uri).ifPresent(adapter -> {
      var change = params.getContentChanges().get(0);
      adapter.onChange(uri, change.getText());
    });
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    System.out.println("didClose");
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    System.out.println("didOpen");
    URI uri = URI.create(params.getTextDocument().getUri());
    pls.getAdapter(uri).ifPresent(adapter -> {
      adapter.onChange(uri, params.getTextDocument().getText());
    });
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    System.out.println("didSave");
  }

  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
    System.out.println("completion");
    URI uri = URI.create(params.getTextDocument().getUri());
    return pls.getAdapter(uri).map(adapter -> {
        CompletableFuture<Either<List<CompletionItem>, CompletionList>> result = adapter.generateCompletion(
            uri,
            params.getPosition().getLine(),
            params.getPosition().getCharacter()
          ).thenApply(Either::forLeft);
        return result;
      })
      .orElse(CompletableFutures.computeAsync(_x -> Either.forLeft(Collections.emptyList())));
  }

  @Override
  public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem params) {
    System.out.println("resolveCompletionItem");
    return CompletableFutures.computeAsync(_x -> {
      return params;
    });
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
    System.out.println("formatting");
    URI uri = URI.create(params.getTextDocument().getUri());
    return pls.getAdapter(uri).map(adapter -> {
        CompletableFuture<List<? extends TextEdit>> result = CompletableFutures.computeAsync(_x -> {
          return adapter.format(uri).map(Collections::singletonList).orElse(Collections.emptyList());
        });
        return result;
      })
      .orElse(CompletableFuture.completedFuture(Collections.emptyList()));
  }
}
