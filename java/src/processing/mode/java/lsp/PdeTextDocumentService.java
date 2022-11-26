package processing.mode.java.lsp;

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

import java.util.Collections;
import java.net.URI;

class PdeTextDocumentService implements TextDocumentService {
  PdeLanguageServer pls;
  PdeTextDocumentService(PdeLanguageServer pls) {
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
    return pls.getAdapter(uri).map(adapter -> adapter.generateCompletion(
        uri,
        params.getPosition().getLine(),
        params.getPosition().getCharacter()
      ).<Either<List<CompletionItem>, CompletionList>>thenApply(Either::forLeft))
      .orElse(CompletableFutures.computeAsync(_x -> Either.forLeft(Collections.emptyList())));
  }

  @Override
  public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem params) {
    System.out.println("resolveCompletionItem");
    return CompletableFutures.computeAsync(_x -> params);
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
    System.out.println("formatting");
    URI uri = URI.create(params.getTextDocument().getUri());
    return pls.getAdapter(uri).map(adapter -> {
      return CompletableFutures.<List<? extends TextEdit>>computeAsync(_x -> {
        return adapter.format(uri).map(Collections::singletonList).orElse(Collections.emptyList());
      });
    })
    .orElse(CompletableFuture.completedFuture(Collections.emptyList()));
  }
}
