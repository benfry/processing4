/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2019 The Processing Foundation

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License version 2
  as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.mode.java.preproc;

import java.text.SimpleDateFormat;
import java.util.*;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.Interval;

import org.antlr.v4.runtime.tree.ParseTree;
import processing.app.Preferences;
import processing.core.PApplet;
import processing.mode.java.pdex.TextTransform;
import processing.mode.java.preproc.PdePreprocessor.Mode;
import processing.mode.java.preproc.code.*;
import processing.mode.java.preproc.issue.PdePreprocessIssue;
import processing.mode.java.preproc.issue.PreprocessIssueMessageSimplifier;

/**
 * ANTLR tree traversal listener that preforms code rewrites as part of sketch preprocessing.
 *
 * <p>
 *   ANTLR tree traversal listener that preforms code rewrites as part of sketch preprocessing,
 *   turning sketch source into compilable Java code. Note that this emits both the Java source
 *   when using javac directly as part of {JavaBuild} as well as {TextTransform.Edit}s when using
 *   the JDT via the {PreprocessingService}.
 * </p>
 */
public class PdeParseTreeListener extends ProcessingBaseListener {

  private static final String VERSION_STR = "3.0.0";
  private static final String SIZE_METHOD_NAME = "size";
  private static final String FULLSCREEN_METHOD_NAME = "fullScreen";
  private final int tabSize;

  private String sketchName;
  private boolean isTesting;
  private TokenStreamRewriter rewriter;
  private Optional<String> destinationPackageName;

  private Mode mode = Mode.JAVA;
  private boolean foundMain;

  private int lineOffset;

  private ArrayList<String> coreImports = new ArrayList<>();
  private ArrayList<String> defaultImports = new ArrayList<>();
  private ArrayList<String> codeFolderImports = new ArrayList<>();
  private ArrayList<String> foundImports = new ArrayList<>();
  private ArrayList<TextTransform.Edit> edits = new ArrayList<>();

  private String sketchWidth;
  private String sketchHeight;
  private String sketchRenderer;

  private boolean sizeRequiresRewrite = false;
  private boolean sizeIsFullscreen = false;
  private RewriteResult headerResult;
  private RewriteResult footerResult;

  private String indent1;
  private String indent2;
  private String indent3;

  private Optional<PdeParseTreeErrorListener> pdeParseTreeErrorListenerMaybe;

  /**
   * Create a new listener.
   *
   * @param tokens The tokens over which to rewrite.
   * @param newSketchName The name of the sketch being traversed.
   * @param newTabSize Size of tab / indent.
   * @param newDestinationPackageName The package to which generated code should be assigned (the
   *    package to which the sketch code java file should be assigned).
   */
  public PdeParseTreeListener(TokenStream tokens, String newSketchName, int newTabSize,
        Optional<String> newDestinationPackageName) {

    rewriter = new TokenStreamRewriter(tokens);
    sketchName = newSketchName;
    tabSize = newTabSize;
    destinationPackageName = newDestinationPackageName;

    pdeParseTreeErrorListenerMaybe = Optional.empty();

    final char[] indentChars = new char[newTabSize];
    Arrays.fill(indentChars, ' ');
    indent1 = new String(indentChars);
    indent2 = indent1 + indent1;
    indent3 = indent2 + indent1;
  }

  /*
   * ===============================================
   * === Public interface for client code usage. ===
   * ===============================================
   */

  /**
   * Indicate imports for code folders.
   *
   * @param codeFolderImports List of imports for sources sitting in the sketch code folder.
   */
  public void setCodeFolderImports(List<String> codeFolderImports) {
    this.codeFolderImports.clear();
    this.codeFolderImports.addAll(codeFolderImports);
  }

  /**
   * Indicate list of imports required for all sketches to be inserted in preprocessing.
   *
   * @param coreImports The list of imports required for all sketches.
   */
  public void setCoreImports(String[] coreImports) {
    setCoreImports(Arrays.asList(coreImports));
  }

  /**
   * Indicate list of imports required for all sketches to be inserted in preprocessing.
   *
   * @param coreImports The list of imports required for all sketches.
   */
  public void setCoreImports(List<String> coreImports) {
    this.coreImports.clear();
    this.coreImports.addAll(coreImports);
  }

  /**
   * Indicate list of default convenience imports.
   *
   * <p>
   *    Indicate list of imports that are not required for sketch operation but included for the
   *    user's convenience regardless.
   * </p>
   *
   * @param defaultImports The list of imports to include for user convenience.
   */
  public void setDefaultImports(String[] defaultImports) {
    setDefaultImports(Arrays.asList(defaultImports));
  }

  /**
   * Indicate list of default convenience imports.
   *
   * <p>
   *    Indicate list of imports that are not required for sketch operation but included for the
   *    user's convenience regardless.
   * </p>
   *
   * @param defaultImports The list of imports to include for user convenience.
   */
  public void setDefaultImports(List<String> defaultImports) {
    this.defaultImports.clear();
    this.defaultImports.addAll(defaultImports);
  }

  /**
   * Indicate if running in unit tests.
   *
   * @param isTesting True if running as part of tests and false otherwise.
   */
  public void setTesting(boolean isTesting) {
    this.isTesting = isTesting;
  }

  /**
   * Indicate which listener should be informed of parse tree processing issues.
   *
   * @param newListener listener to be informed when an issue is encoutnered in processing the
   *    parse tree.
   */
  public void setTreeErrorListener(PdeParseTreeErrorListener newListener) {
    pdeParseTreeErrorListenerMaybe = Optional.of(newListener);
  }

  /**
   * Determine if the user provided their own "main" method.
   *
   * @return True if the sketch code provides a main method. False otherwise.
   */
  public boolean foundMain() {
    return foundMain;
  }

  /**
   * Get the sketch code transformed to grammatical Java.
   *
   * @return Complete sketch code as Java.
   */
  public String getOutputProgram() {
    return rewriter.getText();
  }

  /**
   * Get the rewriter used by this listener.
   *
   * @return Listener's rewriter.
   */
  public TokenStreamRewriter getRewriter() {
    return rewriter;
  }

  /**
   * Get the result of the last preprocessing.
   *
   * @return The result of the last preprocessing.
   */
  public PreprocessorResult getResult() {
    List<String> allImports = new ArrayList<>();

    allImports.addAll(coreImports);
    allImports.addAll(defaultImports);
    allImports.addAll(codeFolderImports);
    allImports.addAll(foundImports);

    List<TextTransform.Edit> allEdits = new ArrayList<>();
    allEdits.addAll(headerResult.getEdits());
    allEdits.addAll(edits);
    allEdits.addAll(footerResult.getEdits());

    return new PreprocessorResult(
        mode,
        lineOffset,
        sketchName,
        allImports,
        allEdits,
        sketchWidth,
        sketchHeight
    );
  }

  /*
   * =========================================================
   * === Implementation of the ANTLR 4 listener functions. ===
   * =========================================================
   */

  /**
   * Endpoint for ANTLR to call when having finished parsing a processing sketch.
   *
   * @param ctx The context from ANTLR for the processing sketch.
   */
  public void exitProcessingSketch(ProcessingParser.ProcessingSketchContext ctx) {
    // header
    headerResult = prepareHeader(rewriter);
    lineOffset = headerResult.getLineOffset();

    // footer
    TokenStream tokenStream = rewriter.getTokenStream();
    int tokens = tokenStream.size();
    int length = tokenStream.get(tokens-1).getStopIndex();

    footerResult = prepareFooter(rewriter, length);
  }

  /**
   * Endpoint for ANTLR to call when finished parsing a method invocatino.
   *
   * @param ctx The ANTLR context for the method call.
   */
  public void exitMethodCall(ProcessingParser.MethodCallContext ctx) {
    String methodName = ctx.getChild(0).getText();

    if (SIZE_METHOD_NAME.equals(methodName) || FULLSCREEN_METHOD_NAME.equals(methodName)) {
      handleSizeCall(ctx);
    }
  }

  /**
   * Endpoint for ANTLR to call when finished parsing an import declaration.
   *
   * <p>
   *    Endpoint for ANTLR to call when finished parsing an import declaration, remvoing those
   *    declarations from sketch body so that they can be included in the header.
   * </p>
   *
   * @param ctx ANTLR context for the import declaration.
   */
  public void exitImportDeclaration(ProcessingParser.ImportDeclarationContext ctx) {
    ProcessingParser.QualifiedNameContext startCtx = null;

    // Due to imports pre-procesing, cannot allow class-body imports
    if (ctx.getParent() instanceof ProcessingParser.ClassBodyDeclarationContext) {
      pdeParseTreeErrorListenerMaybe.ifPresent((listener) -> {
        Token token = ctx.getStart();
        int line = token.getLine();
        int charOffset = token.getCharPositionInLine();

        listener.onError(new PdePreprocessIssue(
          line,
          charOffset,
          PreprocessIssueMessageSimplifier.getLocalStr("editor.status.bad.import")
        ));
      });
    }

    for(int i = 0; i < ctx.getChildCount(); i++) {
      ParseTree candidate = ctx.getChild(i);
      if (candidate instanceof ProcessingParser.QualifiedNameContext) {
        startCtx = (ProcessingParser.QualifiedNameContext) ctx.getChild(i);
      }
    }

    if (startCtx == null) {
      return;
    }

    Interval interval =
        new Interval(startCtx.start.getStartIndex(), ctx.stop.getStopIndex());
    String importString = ctx.start.getInputStream().getText(interval);
    String importStringNoSemi = importString.substring(0, importString.length() - 1);
    foundImports.add(importStringNoSemi);

    delete(ctx.start, ctx.stop);
  }

  /**
   * Endpoint for ANTLR to call after parsing a decimal point literal.
   *
   * <p>
   *   Endpoint for ANTLR to call when finished parsing a floating point literal, adding an 'f' at
   *   the end to force it float instead of double for API compatability.
   * </p>
   *
   * @param ctx ANTLR context for the literal.
   */
  public void exitFloatLiteral(ProcessingParser.FloatLiteralContext ctx) {
    String cTxt = ctx.getText().toLowerCase();
    if (!cTxt.endsWith("f") && !cTxt.endsWith("d")) {
      insertAfter(ctx.stop, "f");
    }
  }

  /**
   * Endpoint for ANTLR to call after parsing a static processing sketch.
   *
   * <p>
   *   Endpoint for ANTLR to call after parsing a static processing sketch, informing this parser
   *   that it is operating on a static sketch (no method or class declarations) so that it writes
   *   the correct header / footer.
   * </p>
   *
   * @param ctx ANTLR context for the sketch.
   */
  public void exitStaticProcessingSketch(ProcessingParser.StaticProcessingSketchContext ctx) {
    mode = Mode.STATIC;
  }

  /**
   * Endpoint for ANTLR to call after parsing a "active" processing sketch.
   *
   * <p>
   *   Endpoint for ANTLR to call after parsing a "active" processing sketch, informing this parser
   *   that it is operating on an active sketch so that it writes the correct header / footer.
   * </p>
   *
   * @param ctx ANTLR context for the sketch.
   */
  public void exitActiveProcessingSketch(ProcessingParser.ActiveProcessingSketchContext ctx) {
    mode = Mode.ACTIVE;
  }

  /**
   * Endpoint for ANTLR to call after parsing a method declaration.
   *
   * <p>
   *   Endpoint for ANTLR to call after parsing a method declaration, making any method "public"
   *   that has:
   *
   *   <ul>
   *     <li>no other access modifier</li>
   *     <li>return type "void"</li>
   *     <li>is either in the context of the sketch class</li>
   *     <li>is in the context of a class definition that extends PApplet</li>
   *   </ul>
   * </p>
   *
   * @param ctx ANTLR context for the method declaration
   */
  public void exitMethodDeclaration(ProcessingParser.MethodDeclarationContext ctx) {
    ParserRuleContext memCtx = ctx.getParent();
    ParserRuleContext clsBdyDclCtx = memCtx.getParent();
    ParserRuleContext clsBdyCtx = clsBdyDclCtx.getParent();
    ParserRuleContext clsDclCtx = clsBdyCtx.getParent();

    boolean inSketchContext =
      clsBdyCtx instanceof ProcessingParser.StaticProcessingSketchContext ||
      clsBdyCtx instanceof ProcessingParser.ActiveProcessingSketchContext;

    boolean inPAppletContext =
      inSketchContext || (
        clsDclCtx instanceof ProcessingParser.ClassDeclarationContext &&
        clsDclCtx.getChildCount() >= 4 &&
        clsDclCtx.getChild(2).getText().equals("extends") &&
        clsDclCtx.getChild(3).getText().endsWith("PApplet"));

    // Find modifiers
    ParserRuleContext possibleModifiers = ctx;

    while (!(possibleModifiers instanceof ProcessingParser.ClassBodyDeclarationContext)) {
      possibleModifiers = possibleModifiers.getParent();
    }

    // Look for visibility modifiers and annotations
    boolean hasVisibilityModifier = false;

    int numChildren = possibleModifiers.getChildCount();

    ParserRuleContext annoationPoint = null;

    for (int i = 0; i < numChildren; i++) {
      boolean childIsVisibility;

      ParseTree child = possibleModifiers.getChild(i);
      String childText = child.getText();

      childIsVisibility = childText.equals("public");
      childIsVisibility = childIsVisibility || childText.equals("private");
      childIsVisibility = childIsVisibility || childText.equals("protected");

      hasVisibilityModifier = hasVisibilityModifier || childIsVisibility;

      boolean isModifier = child instanceof ProcessingParser.ModifierContext;
      if (isModifier && isAnnotation((ProcessingParser.ModifierContext) child)) {
        annoationPoint = (ParserRuleContext) child;
      }
    }

    // Insert at start of method or after annoation
    if (!hasVisibilityModifier) {
      if (annoationPoint == null) {
        insertBefore(possibleModifiers.getStart(), " public ");
      } else {
        insertAfter(annoationPoint.getStop(), " public ");
      }
    }

    // Check if this was main
    if ((inSketchContext || inPAppletContext) &&
        hasVisibilityModifier &&
        ctx.getChild(1).getText().equals("main")) {
      foundMain = true;
    }
  }

  /**
   * Endpoint for ANTLR to call after parsing a primitive type name.
   *
   * <p>
   *   Endpoint for ANTLR to call after parsing a primitive type name, possibly converting that type
   *   to a parse function as part of the Processing API.
   * </p>
   *
   * @param ctx ANTLR context for the primitive name token.
   */
  public void exitFunctionWithPrimitiveTypeName(
      ProcessingParser.FunctionWithPrimitiveTypeNameContext ctx) {

    String fn = ctx.getChild(0).getText();
    if (!fn.equals("color")) {
      fn = "PApplet.parse" + fn.substring(0,1).toUpperCase() + fn.substring(1);
      insertBefore(ctx.start, fn);
      delete(ctx.start);
    }
  }

  /**
   * Endpoint for ANTLR to call after parsing a color primitive token.
   *
   * <p>
   *   Endpoint for ANTLR to call after parsing a color primitive token, fixing "color type" to be
   *   "int" as part of the processing API.
   * </p>
   *
   * @param ctx ANTLR context for the type token.
   */
  public void exitColorPrimitiveType(ProcessingParser.ColorPrimitiveTypeContext ctx) {
    if (ctx.getText().equals("color")) {
      insertBefore(ctx.start, "int");
      delete(ctx.start, ctx.stop);
    }
  }

  /**
   * Endpoint for ANTLR to call after parsing a hex color literal.
   *
   * @param ctx ANTLR context for the literal.
   */
  public void exitHexColorLiteral(ProcessingParser.HexColorLiteralContext ctx) {
    if (ctx.getText().length() == 7) {
      insertBefore(
          ctx.start,
          ctx.getText().toUpperCase().replace("#","0xFF")
      );
    } else {
      insertBefore(
          ctx.start,
          ctx.getText().toUpperCase().replace("#", "0x")
      );
    }

    delete(ctx.start, ctx.stop);
  }

  /* ===========================================================
   * === Helper functions to parse and manage tree listeners ===
   * ===========================================================
   */

  /**
   * Manage parsing out a size or fullscreen call.
   *
   * @param ctx The context of the call.
   */
  protected void handleSizeCall(ParserRuleContext ctx) {
    ParserRuleContext testCtx = ctx.getParent()
        .getParent()
        .getParent()
        .getParent();

    boolean isInGlobal =
        testCtx instanceof ProcessingParser.StaticProcessingSketchContext;

    boolean isInSetup;
    if (!isInGlobal) {
      ParserRuleContext methodDeclaration = testCtx.getParent()
          .getParent();

      isInSetup = isMethodSetup(methodDeclaration);
    } else {
      isInSetup = false;
    }

    ParseTree argsContext = ctx.getChild(2);

    boolean thisRequiresRewrite = false;

    boolean isSize = ctx.getChild(0).getText().equals(SIZE_METHOD_NAME);
    boolean isFullscreen = ctx.getChild(0).getText().equals(FULLSCREEN_METHOD_NAME);

    if (isInGlobal || isInSetup) {
      thisRequiresRewrite = true;

      if (isSize && argsContext.getChildCount() > 2) {
        sketchWidth = argsContext.getChild(0).getText();
        if (PApplet.parseInt(sketchWidth, -1) == -1 &&
            !sketchWidth.equals("displayWidth")) {
          thisRequiresRewrite = false;
        }

        sketchHeight = argsContext.getChild(2).getText();
        if (PApplet.parseInt(sketchHeight, -1) == -1 &&
            !sketchHeight.equals("displayHeight")) {
          thisRequiresRewrite = false;
        }

        if (argsContext.getChildCount() > 3) {
          sketchRenderer = argsContext.getChild(4).getText();
          if (!(sketchRenderer.equals("P2D") ||
              sketchRenderer.equals("P3D") ||
              sketchRenderer.equals("OPENGL") ||
              sketchRenderer.equals("JAVA2D") ||
              sketchRenderer.equals("FX2D"))) {
            thisRequiresRewrite = false;
          }
        }
      }

      if (isFullscreen) {
        sketchWidth = "displayWidth";
        sketchWidth = "displayHeight";

        thisRequiresRewrite = true;
        sizeIsFullscreen = true;

        if (argsContext.getChildCount() > 0) {
          sketchRenderer = argsContext.getChild(0).getText();
          if (!(sketchRenderer.equals("P2D") ||
              sketchRenderer.equals("P3D") ||
              sketchRenderer.equals("OPENGL") ||
              sketchRenderer.equals("JAVA2D") ||
              sketchRenderer.equals("FX2D"))) {
            thisRequiresRewrite = false;
          }
        }
      }
    }

    if (thisRequiresRewrite) {
      delete(ctx.start, ctx.stop);
      insertAfter(ctx.stop, "/* size commented out by preprocessor */");
      sizeRequiresRewrite = true;
    }
  }

  /**
   * Determine if a method declaration is for setup.
   *
   * @param declaration The method declaration to parse.
   * @return True if setup and false otherwise.
   */
  protected boolean isMethodSetup(ParserRuleContext declaration) {
    if (declaration.getChildCount() < 2) {
      return false;
    }
    return declaration.getChild(1).getText().equals("setup");
  }

  /**
   * Check if this contains an annation.
   *
   * @param context The modifier context to check.
   * @return True if annotation. False otherwise
   */
  protected boolean isAnnotation(ProcessingParser.ModifierContext context) {
    if (context.getChildCount() == 0) {
      return false;
    }

    ProcessingParser.ClassOrInterfaceModifierContext classModifierCtx;
    if (!(context.getChild(0) instanceof ProcessingParser.ClassOrInterfaceModifierContext)) {
      return false;
    }

    classModifierCtx = (ProcessingParser.ClassOrInterfaceModifierContext) context.getChild(0);

    return classModifierCtx.getChild(0) instanceof ProcessingParser.AnnotationContext;
  }

  /* ====================================================================================
   * === Utility functions to perform code edit operations and generate rewrite info. ===
   * ====================================================================================
   *
   * Utility functions to generate and perform code edit operations, performing the edit immediately
   * within a ANTLR rewriter but also generating a {TextTransform.Edit} for use with the JDT. Some
   * of these are left protected for subclasses of PdeParseTreeListener access.
   */

  /**
   * Insert text before a token.
   *
   * @param location The token before which code should be added.
   * @param text The text to add.
   */
  protected void insertBefore(Token location, String text) {
    edits.add(createInsertBefore(location, text, rewriter));
  }

  /**
   * Insert text before a location in code.
   *
   * @param locationToken Character offset from start.
   * @param locationOffset
   * @param text Text to add.
   */
  protected void insertBefore(int locationToken, int locationOffset, String text) {
    edits.add(createInsertBefore(locationToken, locationOffset, text, rewriter));
  }

  /**
   * Insert text after a location in code.
   *
   * @param location The token after which to insert code.
   * @param text The text to insert.
   */
  protected void insertAfter(Token location, String text) {
    edits.add(createInsertAfter(location, text, rewriter));
  }

  /**
   * Delete from a token to a token inclusive.
   *
   * @param start First token to delete.
   * @param stop Last token to delete.
   */
  protected void delete(Token start, Token stop) {
    edits.add(createDelete(start, stop, rewriter));
  }

  /**
   * Delete a single token.
   *
   * @param location Token to delete.
   */
  protected void delete(Token location) {
    edits.add(createDelete(location, rewriter));
  }

  /*
   * =========================================
   * === Code generation utility functions ===
   * =========================================
   */

  /**
   * Prepare preface code to wrap sketch code so that it is contained within a proper Java
   * definition.
   *
   * @param headerWriter The writer into which the header should be written.
   * @return Information about the completed rewrite.
   */
  protected RewriteResult prepareHeader(TokenStreamRewriter headerWriter) {

    RewriteResultBuilder resultBuilder = new RewriteResultBuilder();

    PrintWriterWithEditGen decoratedWriter = new PrintWriterWithEditGen(
        headerWriter,
        resultBuilder,
        0,
        true
    );

    writeHeaderContents(decoratedWriter, resultBuilder);

    decoratedWriter.finish();

    return resultBuilder.build();
  }

  /**
   * Prepare the footer for a sketch (finishes the constructs introduced in header like class def).
   *
   * @param footerWriter The writer through which the footer should be introduced.
   * @param insertPoint The loction at which the footer should be written.
   * @return Information about the completed rewrite.
   */
  protected RewriteResult prepareFooter(TokenStreamRewriter footerWriter, int insertPoint) {

    RewriteResultBuilder resultBuilder = new RewriteResultBuilder();

    PrintWriterWithEditGen decoratedWriter = new PrintWriterWithEditGen(
        footerWriter,
        resultBuilder,
        insertPoint,
        false
    );

    writeFooterContents(decoratedWriter, resultBuilder);

    decoratedWriter.finish();

    return resultBuilder.build();
  }

  /**
   * Write the contents of the header using a prebuilt print writer.
   *
   * @param decoratedWriter he writer though which the comment should be introduced.
   * @param resultBuilder Builder for reporting out results to the caller.
   */
  protected void writeHeaderContents(PrintWriterWithEditGen decoratedWriter,
        RewriteResultBuilder resultBuilder) {

    if (destinationPackageName.isPresent()) {
      decoratedWriter.addCodeLine("package " + destinationPackageName.get() + ";");
      decoratedWriter.addEmptyLine();
    }

    if (!isTesting) {
      writePreprocessorComment(decoratedWriter, resultBuilder);
    }

    writeImports(decoratedWriter, resultBuilder);

    boolean requiresClassHeader = mode == PdePreprocessor.Mode.STATIC;
    requiresClassHeader = requiresClassHeader || mode == PdePreprocessor.Mode.ACTIVE;

    boolean requiresStaticSketchHeader = mode == PdePreprocessor.Mode.STATIC;

    if (requiresClassHeader) {
      writeClassHeader(decoratedWriter, resultBuilder);
    }

    if (requiresStaticSketchHeader) {
      writeStaticSketchHeader(decoratedWriter, resultBuilder);
    }
  }

  /**
   * Write the contents of the footer using a prebuilt print writer.
   *
   * @param decoratedWriter he writer though which the comment should be introduced.
   * @param resultBuilder Builder for reporting out results to the caller.
   */
  protected void writeFooterContents(PrintWriterWithEditGen decoratedWriter,
        RewriteResultBuilder resultBuilder) {

    decoratedWriter.addEmptyLine();

    boolean requiresStaticSketchFooter = mode == PdePreprocessor.Mode.STATIC;
    boolean requiresClassWrap = mode == PdePreprocessor.Mode.STATIC;
    requiresClassWrap = requiresClassWrap || mode == PdePreprocessor.Mode.ACTIVE;

    if (requiresStaticSketchFooter) {
      writeStaticSketchFooter(decoratedWriter, resultBuilder);
    }

    if (requiresClassWrap) {
      writeExtraFieldsAndMethods(decoratedWriter, resultBuilder);
      if (!foundMain) {
        writeMain(decoratedWriter, resultBuilder);
      }
      writeClassFooter(decoratedWriter, resultBuilder);
    }
  }

  /**
   * Comment out sketch code before it is moved elsewhere in resulting Java.
   *
   * @param headerWriter The writer though which the comment should be introduced.
   * @param resultBuilder Builder for reporting out results to the caller.
   */
  protected void writePreprocessorComment(PrintWriterWithEditGen headerWriter,
        RewriteResultBuilder resultBuilder) {

    String dateStr = new SimpleDateFormat("YYYY-MM-dd").format(new Date());

    String newCode = String.format(
        "/* autogenerated by Processing preprocessor v%s on %s */",
        VERSION_STR,
        dateStr
    );

    headerWriter.addCodeLine(newCode);
  }

  /**
   * Add imports as part of conversion from processing sketch to Java code.
   *
   * @param headerWriter The writer though which the imports should be introduced.
   * @param resultBuilder Builder for reporting out results to the caller.
   */
  protected void writeImports(PrintWriterWithEditGen headerWriter,
        RewriteResultBuilder resultBuilder) {

    writeImportList(headerWriter, coreImports, resultBuilder);
    writeImportList(headerWriter, codeFolderImports, resultBuilder);
    writeImportList(headerWriter, foundImports, resultBuilder);
    writeImportList(headerWriter, defaultImports, resultBuilder);
  }

  /**
   * Write a list of imports.
   *
   * @param headerWriter The writer though which the imports should be introduced.
   * @param imports Collection of imports to introduce.
   * @param resultBuilder Builder for reporting out results to the caller.
   */
  protected void writeImportList(PrintWriterWithEditGen headerWriter, List<String> imports,
      RewriteResultBuilder resultBuilder) {

    writeImportList(headerWriter, imports.toArray(new String[0]), resultBuilder);
  }

  /**
   * Write a list of imports.
   *
   * @param headerWriter The writer though which the imports should be introduced.
   * @param imports Collection of imports to introduce.
   * @param resultBuilder Builder for reporting out results to the caller.
   */
  protected void writeImportList(PrintWriterWithEditGen headerWriter, String[] imports,
        RewriteResultBuilder resultBuilder) {

    for (String importDecl : imports) {
      headerWriter.addCodeLine("import " + importDecl + ";");
    }
    if (imports.length > 0) {
      headerWriter.addEmptyLine();
    }
  }

  /**
   * Write the prefix which defines the enclosing class for the sketch.
   *
   * @param headerWriter The writer through which the header should be introduced.
   * @param resultBuilder Builder for reporting out results to the caller.
   */
  protected void writeClassHeader(PrintWriterWithEditGen headerWriter,
        RewriteResultBuilder resultBuilder) {

    headerWriter.addCodeLine("public class " + sketchName + " extends PApplet {");

    headerWriter.addEmptyLine();
  }

  /**
   * Write the header for a static sketch (no methods).
   *
   * @param headerWriter The writer through which the header should be introduced.
   * @param resultBuilder Builder for reporting out results to the caller.
   */
  protected void writeStaticSketchHeader(PrintWriterWithEditGen headerWriter,
        RewriteResultBuilder resultBuilder) {

    headerWriter.addCodeLine(indent1 + "public void setup() {");
  }

  /**
   * Write the bottom of the sketch code for static mode.
   *
   * @param footerWriter The footer into which the text should be written.
   * @param resultBuilder Builder for reporting out results to the caller.
   */
  protected void writeStaticSketchFooter(PrintWriterWithEditGen footerWriter,
        RewriteResultBuilder resultBuilder) {

    footerWriter.addCodeLine(indent2 +   "noLoop();");
    footerWriter.addCodeLine(indent1 + "}");
  }

  /**
   * Write code supporting special functions like size.
   *
   * @param classBodyWriter The writer into which the code should be written. Should be for class
   *    body.
   * @param resultBuilder Builder for reporting out results to the caller.
   */
  protected void writeExtraFieldsAndMethods(PrintWriterWithEditGen classBodyWriter,
        RewriteResultBuilder resultBuilder) {

    if (!sizeRequiresRewrite) {
      return;
    }

    String settingsOuterTemplate = indent1 + "public void settings() { %s }";

    String settingsInner;
    if (sizeIsFullscreen) {
      String fullscreenInner = sketchRenderer == null ? "" : sketchRenderer;
      settingsInner = String.format("fullScreen(%s);", fullscreenInner);
    } else {

      if (sketchWidth.isEmpty() || sketchHeight.isEmpty()) {
        return;
      }

      StringJoiner argJoiner = new StringJoiner(",");
      argJoiner.add(sketchWidth);
      argJoiner.add(sketchHeight);

      if (sketchRenderer != null) {
        argJoiner.add(sketchRenderer);
      }

      settingsInner = String.format("size(%s);", argJoiner.toString());
    }


    String newCode = String.format(settingsOuterTemplate, settingsInner);

    classBodyWriter.addEmptyLine();
    classBodyWriter.addCodeLine(newCode);
  }

  /**
   * Write the main method.
   *
   * @param footerWriter The writer into which the footer should be written.
   * @param resultBuilder Builder for reporting out results to the caller.
   */
  protected void writeMain(PrintWriterWithEditGen footerWriter,
        RewriteResultBuilder resultBuilder) {

    footerWriter.addEmptyLine();
    footerWriter.addCodeLine(indent1 + "static public void main(String[] passedArgs) {");
    footerWriter.addCode(indent2 +   "String[] appletArgs = new String[] { ");

    { // assemble line with applet args
      if (Preferences.getBoolean("export.application.fullscreen")) {
        footerWriter.addCode("\"" + PApplet.ARGS_FULL_SCREEN + "\", ");

        String bgColor = Preferences.get("run.present.bgcolor");
        footerWriter.addCode("\"" + PApplet.ARGS_BGCOLOR + "=" + bgColor + "\", ");

        if (Preferences.getBoolean("export.application.stop")) {
          String stopColor = Preferences.get("run.present.stop.color");
          footerWriter.addCode("\"" + PApplet.ARGS_STOP_COLOR + "=" + stopColor + "\", ");
        } else {
          footerWriter.addCode("\"" + PApplet.ARGS_HIDE_STOP + "\", ");
        }
      }
      footerWriter.addCode("\"" + sketchName + "\"");
    }

    footerWriter.addCodeLine(" };");

    footerWriter.addCodeLine(indent2 +   "if (passedArgs != null) {");
    footerWriter.addCodeLine(indent3 +     "PApplet.main(concat(appletArgs, passedArgs));");
    footerWriter.addCodeLine(indent2 +   "} else {");
    footerWriter.addCodeLine(indent3 +     "PApplet.main(appletArgs);");
    footerWriter.addCodeLine(indent2 +   "}");
    footerWriter.addCodeLine(indent1 + "}");
  }

  /**
   * Write the end of the class body for the footer.
   *
   * @param footerWriter The writer into which the footer should be written.
   * @param resultBuilder Builder for reporting out results to the caller.
   */
  protected void writeClassFooter(PrintWriterWithEditGen footerWriter,
        RewriteResultBuilder resultBuilder) {

    footerWriter.addCodeLine("}");
  }

  /*
   * =========================
   * === Supporting types. ===
   * =========================
   */

  /**
   * Listener for issues encountered while processing a valid pde parse tree.
   */
  public static interface PdeParseTreeErrorListener {

    /**
     * Callback to invoke when an issue is encountered while processing a valid PDE parse tree.
     *
     * @param issue The issue reported.
     */
    void onError(PdePreprocessIssue issue);
  }

  /**
   * Decorator around a {TokenStreamRewriter}.
   *
   * <p>
   *   Decorator around a {TokenStreamRewriter} which converts input commands into something that the
   *   rewriter can understand but also generates edits saved to an input RewriteResultBuilder.
   *   Requires a call to finish() after completion of preprocessing.
   * </p>
   */
  public static class PrintWriterWithEditGen {

    private final TokenStreamRewriter writer;
    private final RewriteResultBuilder rewriteResultBuilder;
    private final int insertPoint;
    private final StringBuilder editBuilder;
    private final boolean before;

    /**
     * Create a new edit generator decorator.
     *
     * @param writer The writer to which edits should be immediately made.
     * @param newRewriteResultBuilder The builder to which edits should be saved.
     * @param newInsertPoint The point at which new values should be inserted.
     * @param newBefore If true, the values will be inserted before the given insert point. If false,
     *    will, insert after the insertion point.
     */
    public PrintWriterWithEditGen(TokenStreamRewriter writer,
                                  RewriteResultBuilder newRewriteResultBuilder, int newInsertPoint, boolean newBefore) {

      this.writer = writer;
      rewriteResultBuilder = newRewriteResultBuilder;
      insertPoint = newInsertPoint;
      editBuilder = new StringBuilder();
      before = newBefore;
    }

    /**
     * Add an empty line into the code.
     */
    public void addEmptyLine() {
      addCode("\n");
    }

    /**
     * Add code with a newline automatically appended.
     *
     * @param newCode The code to add.
     */
    public void addCodeLine(String newCode) {
      addCode(newCode + "\n");
    }

    /**
     * Add code without a new line.
     *
     * @param newCode The code to add.
     */
    public void addCode(String newCode) {
      editBuilder.append(newCode);
    }

    /**
     * Finalize edits made through this decorator.
     */
    public void finish() {
      String newCode = editBuilder.toString();

      if (before) {
        rewriteResultBuilder.addEdit(PdeParseTreeListener.createInsertBefore(
            insertPoint,
            insertPoint,
            newCode,
            writer
        ));
      } else {
        rewriteResultBuilder.addEdit(PdeParseTreeListener.insertAfter(
            insertPoint,
            newCode,
            writer
        ));
      }

      rewriteResultBuilder.addOffset(SyntaxUtil.getCount(newCode, "\n"));
    }

  }

  /*
   * ===========================================================
   * === Utility functions to generate code edit operations. ===
   * ===========================================================
   */

  /**
   * Delete tokens between a start end end token inclusive.
   *
   * @param start The token to be deleted.
   * @param stop The final token to be deleted.
   * @param rewriter The rewriter with which to make the edit.
   * @return The {TextTransform.Edit} corresponding to this change.
   */
  protected static TextTransform.Edit createDelete(Token start, Token stop,
                                                   TokenStreamRewriter rewriter) {

    rewriter.delete(start, stop);

    int startIndex = start.getStartIndex();
    int length = stop.getStopIndex() - startIndex + 1;

    return TextTransform.Edit.delete(
        startIndex,
        length
    );
  }

  /**
   * Insert text after a token.
   *
   * @param start The token after which the text should be inserted.
   * @param text The text to insert.
   * @param rewriter The rewriter with which to make the edit.
   * @return The {TextTransform.Edit} corresponding to this change.
   */
  protected static TextTransform.Edit createInsertAfter(Token start, String text,
                                                        TokenStreamRewriter rewriter) {

    rewriter.insertAfter(start, text);

    return TextTransform.Edit.insert(
        start.getStopIndex() + 1,
        text
    );
  }

  /**
   * Insert text before a token.
   *
   * @param before Token before which the text should be inserted.
   * @param text The text to insert.
   * @param rewriter The rewriter with which to make the edit.
   * @return The {TextTransform.Edit} corresponding to this change.
   */
  protected static TextTransform.Edit createInsertBefore(Token before, String text,
                                                         TokenStreamRewriter rewriter) {

    rewriter.insertBefore(before, text);

    return TextTransform.Edit.insert(
        before.getStartIndex(),
        text
    );
  }

  /**
   * Insert text before a position in code.
   *
   * @param before The location before which to insert the text in tokens.
   * @param beforeOffset THe location before which to insert the text in chars.
   * @param text The text to insert.
   * @param rewriter The rewriter with which to make the edit.
   * @return The {TextTransform.Edit} corresponding to this change.
   */
  protected static TextTransform.Edit createInsertBefore(int before, int beforeOffset, String text,
                                                         TokenStreamRewriter rewriter) {

    rewriter.insertBefore(before, text);

    return TextTransform.Edit.insert(
        beforeOffset,
        text
    );
  }

  /**
   * Insert text after a token.
   *
   * @param start The position after which the text should be inserted.
   * @param text The text to insert.
   * @param rewriter The rewriter with which to make the edit.
   * @return The {TextTransform.Edit} corresponding to this change.
   */
  protected static TextTransform.Edit insertAfter(int start, String text,
                                                  TokenStreamRewriter rewriter) {
    rewriter.insertAfter(start, text);

    return TextTransform.Edit.insert(
        start + 1,
        text
    );
  }

  /**
   * Delete a single token.
   *
   * @param start The token to be deleted.
   * @param rewriter The rewriter with which to make the edit.
   * @return The {TextTransform.Edit} corresponding to this change.
   */
  protected static TextTransform.Edit createDelete(Token start, TokenStreamRewriter rewriter) {
    rewriter.delete(start);
    return TextTransform.Edit.delete(start.getStartIndex(), start.getText().length());
  }

}
