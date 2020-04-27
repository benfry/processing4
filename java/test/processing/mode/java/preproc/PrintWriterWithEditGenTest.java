package processing.mode.java.preproc;

import org.antlr.v4.runtime.TokenStreamRewriter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import processing.mode.java.TextTransform;

import java.util.List;

import static org.junit.Assert.*;

public class PrintWriterWithEditGenTest {

  private TokenStreamRewriter tokenStreamRewriter;
  private RewriteResultBuilder rewriteResultBuilder;

  @Before
  public void setUp() {
    tokenStreamRewriter = Mockito.mock(TokenStreamRewriter.class);
    rewriteResultBuilder = new RewriteResultBuilder();
  }

  @Test
  public void addEmptyLineBefore() {
    PdeParseTreeListener.PrintWriterWithEditGen editGen = createGen(true);
    editGen.addEmptyLine();
    editGen.finish();

    List<TextTransform.Edit> edits = rewriteResultBuilder.getEdits();
    Assert.assertEquals(1, edits.size());

    Mockito.verify(tokenStreamRewriter).insertBefore(5, "\n");
  }

  @Test
  public void addCodeLineBefore() {
    PdeParseTreeListener.PrintWriterWithEditGen editGen = createGen(true);
    editGen.addCodeLine("test");
    editGen.finish();

    List<TextTransform.Edit> edits = rewriteResultBuilder.getEdits();
    Assert.assertEquals(1, edits.size());

    Mockito.verify(tokenStreamRewriter).insertBefore(5, "test\n");
  }

  @Test
  public void addCodeBefore() {
    PdeParseTreeListener.PrintWriterWithEditGen editGen = createGen(true);
    editGen.addCode("test");
    editGen.finish();

    List<TextTransform.Edit> edits = rewriteResultBuilder.getEdits();
    Assert.assertEquals(1, edits.size());

    Mockito.verify(tokenStreamRewriter).insertBefore(5, "test");
  }

  @Test
  public void addEmptyLineAfter() {
    PdeParseTreeListener.PrintWriterWithEditGen editGen = createGen(false);
    editGen.addEmptyLine();
    editGen.finish();

    List<TextTransform.Edit> edits = rewriteResultBuilder.getEdits();
    Assert.assertEquals(1, edits.size());

    Mockito.verify(tokenStreamRewriter).insertAfter(5, "\n");
  }

  @Test
  public void addCodeLineAfter() {
    PdeParseTreeListener.PrintWriterWithEditGen editGen = createGen(false);
    editGen.addCodeLine("test");
    editGen.finish();

    List<TextTransform.Edit> edits = rewriteResultBuilder.getEdits();
    Assert.assertEquals(1, edits.size());

    Mockito.verify(tokenStreamRewriter).insertAfter(5, "test\n");
  }

  @Test
  public void addCodeAfter() {
    PdeParseTreeListener.PrintWriterWithEditGen editGen = createGen(false);
    editGen.addCode("test");
    editGen.finish();

    List<TextTransform.Edit> edits = rewriteResultBuilder.getEdits();
    Assert.assertEquals(1, edits.size());

    Mockito.verify(tokenStreamRewriter).insertAfter(5, "test");
  }

  private PdeParseTreeListener.PrintWriterWithEditGen createGen(boolean before) {
    return new PdeParseTreeListener.PrintWriterWithEditGen(
        tokenStreamRewriter,
        rewriteResultBuilder,
        5,
        before
    );
  }

}