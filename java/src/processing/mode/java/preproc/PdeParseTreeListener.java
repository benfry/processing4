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
import java.util.stream.Collectors;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.Interval;

import org.antlr.v4.runtime.tree.ParseTree;

import processing.app.Base;
import processing.app.Preferences;
import processing.core.PApplet;
import processing.mode.java.ImportStatement;
import processing.mode.java.SourceUtil;
import processing.mode.java.TextTransform;
import processing.mode.java.preproc.PdePreprocessor.Mode;

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

  private static final String SIZE_METHOD_NAME = "size";
  private static final String SMOOTH_METHOD_NAME = "smooth";
  private static final String NO_SMOOTH_METHOD_NAME = "noSmooth";
  private static final String PIXEL_DENSITY_METHOD_NAME = "pixelDensity";
  private static final String FULLSCREEN_METHOD_NAME = "fullScreen";
  private static final boolean SIMULATE_MULTILINE_STRINGS = true;

  final private String sketchName;
  private boolean isTesting;
  final private TokenStreamRewriter rewriter;
  private Optional<String> destinationPackageName;

  private Mode mode = Mode.JAVA;
  private boolean foundMain;

  private int lineOffset;

  private List<ImportStatement> coreImports = new ArrayList<>();
  private List<ImportStatement> defaultImports = new ArrayList<>();
  private List<ImportStatement> codeFolderImports = new ArrayList<>();
  private List<ImportStatement> foundImports = new ArrayList<>();
  private List<TextTransform.Edit> edits = new ArrayList<>();

  private String sketchWidth;
  private String sketchHeight;
  private String pixelDensity;
  private String smoothParam;
  private String sketchRenderer = null;
  private String fullscreenArgs = "";
  private String sketchOutputFilename = null;

  private boolean sizeRequiresRewrite = false;
  private boolean pixelDensityRequiresRewrite = false;
  private boolean sizeIsFullscreen = false;
  private boolean noSmoothRequiresRewrite = false;
  private boolean smoothRequiresRewrite = false;
  private boolean userImportingManually = false;
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
   * Indicate imports for code folders given those imports' fully qualified names.
   *
   * @param codeFolderImports List of imports for sources sitting in the sketch code folder. Note that these will be
   *    interpreted as non-static imports.
   */
  public void setCodeFolderImports(List<String> codeFolderImports) {
    setCodeFolderImportInfo(createPlainImportStatementInfos(codeFolderImports));
  }

  /**
   * Indicate imports for code folders given full import statement information.
   * names.
   *
   * @param codeFolderImports List of import statement info for sources sitting in the sketch code folder.
   */
  public void setCodeFolderImportInfo(List<ImportStatement> codeFolderImports) {
    this.codeFolderImports.clear();
    this.codeFolderImports.addAll(codeFolderImports);
  }

  /**
   * Indicate list of imports required for all sketches to be inserted in preprocessing given those imports' fully
   * qualified names.
   *
   * @param coreImports The list of imports required for all sketches. Note that these will be interpreted as non-static
   *    imports.
   */
  public void setCoreImports(String[] coreImports) {
    setCoreImports(Arrays.asList(coreImports));
  }

  /**
   * Indicate list of imports required for all sketches to be inserted in preprocessing given those imports' fully
   * qualified names.
   *
   * @param coreImports The list of imports required for all sketches. Note that these will be interpreted as non-static
   *    imports.
   */
  public void setCoreImports(List<String> coreImports) {
    setCoreImportInfo(createPlainImportStatementInfos(coreImports));
  }

  public void setCoreImportInfo(List<ImportStatement> coreImports) {
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
   *    user's convenience regardless given those imports' fully qualified names.
   * </p>
   *
   * @param defaultImports The list of imports to include for user convenience. Note that these will be interpreted as
   *    non-static imports.
   */
  public void setDefaultImports(List<String> defaultImports) {
    setDefaultImportInfo(createPlainImportStatementInfos(defaultImports));
  }

  public void setDefaultImportInfo(List<ImportStatement> defaultImports) {
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
    return getResult(new ArrayList<>());
  }

  /**
   * Get the result of the last preprocessing with optional error.
   *
   * @param issues The errors (if any) encountered.
   * @return The result of the last preprocessing.
   */
  public PreprocessorResult getResult(List<PdePreprocessIssue> issues) {
    List<ImportStatement> allImports = new ArrayList<>();

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
        sketchHeight,
        sketchRenderer,
        issues
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
   * Detect if the user is programming with "mixed" modes.
   *
   * <p>Detect if the user is programming with "mixed" modes where they are
   * combining active and static mode features. This may be, for example, a
   * method call followed by method definition.</p>
   *
   * @param ctx The context from ANTLR for the mixed modes sketch.
   */
  public void enterWarnMixedModes(ProcessingParser.WarnMixedModesContext ctx) {
    pdeParseTreeErrorListenerMaybe.ifPresent((listener) -> {
      Token token = ctx.getStart();
      int line = token.getLine();
      int charOffset = token.getCharPositionInLine();

      listener.onError(new PdePreprocessIssue(
        line,
        charOffset,
        PreprocessIssueMessageSimplifier.getLocalStr(
            "editor.status.bad.mixed_mode"
        )
      ));
    });
  }

  /**
   * Endpoint for ANTLR to call when finished parsing a method invocatino.
   *
   * @param ctx The ANTLR context for the method call.
   */
  public void exitMethodCall(ProcessingParser.MethodCallContext ctx) {
    String methodName = ctx.getChild(0).getText();

    // Check if calling on something other than this.
    boolean impliedThis = ctx.getParent().getChildCount() == 1;
    boolean usesThis;
    if (impliedThis) {
      usesThis = true;
    } else {
      String statmentTarget = ctx.getParent().getChild(0).getText();
      boolean explicitThis = statmentTarget.equals("this");
      boolean explicitSuper = statmentTarget.equals("super");
      usesThis = explicitThis || explicitSuper;
    }

    // If not using this or super, no rewrite as the user is calling their own
    // declaration or instance of PGraphics.
    if (!usesThis) {
      return;
    }

    // If referring to the applet, check for rewrites.
    if (SIZE_METHOD_NAME.equals(methodName) || FULLSCREEN_METHOD_NAME.equals(methodName)) {
      handleSizeCall(ctx);
    } else if (PIXEL_DENSITY_METHOD_NAME.equals(methodName)) {
      handlePixelDensityCall(ctx);
    } else if (NO_SMOOTH_METHOD_NAME.equals(methodName)) {
      handleNoSmoothCall(ctx);
    } else if (SMOOTH_METHOD_NAME.equals(methodName)) {
      handleSmoothCall(ctx);
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

    // Find the start of the fully qualified name.
    boolean isStaticImport = false;
    for(int i = 0; i < ctx.getChildCount(); i++) {
      ParseTree candidate = ctx.getChild(i);
      String candidateText = candidate.getText().toLowerCase();
      boolean childIsStatic = (candidateText.equals("static"));
      isStaticImport = isStaticImport || childIsStatic;
      if (candidate instanceof ProcessingParser.QualifiedNameContext) {
        startCtx = (ProcessingParser.QualifiedNameContext) ctx.getChild(i);
      }
    }

    if (startCtx == null) {
      return;
    }

    // Extract the fully qualified name
    Interval interval = new Interval(
        startCtx.start.getStartIndex(),
        ctx.stop.getStopIndex()
    );

    String importString = ctx.start.getInputStream().getText(interval);
    int endImportIndex = importString.length() - 1;
    String importStringNoSemi = importString.substring(0, endImportIndex);

    // Check for static import
    if (isStaticImport) {
      importStringNoSemi = "static " + importStringNoSemi;
    }

    foundImports.add(ImportStatement.parse(importStringNoSemi));

    if (importStringNoSemi.startsWith("processing.core.")) {
      userImportingManually = true;
    }

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
   * Endpoint for ANTLR to call after parsing a String literal.
   *
   * <p>
   *   Endpoint for ANTLR to call when finished parsing a string literal, simulating multiline
   *   strings if configured to do so.
   * </p>
   *
   * @param ctx ANTLR context for the literal.
   */
  public void exitMultilineStringLiteral(ProcessingParser.MultilineStringLiteralContext ctx) {
    String fullLiteral = ctx.getText();
    if (SIMULATE_MULTILINE_STRINGS) {
      delete(ctx.start, ctx.stop);
      int endIndex = fullLiteral.length() - 3;
      String literalContents = fullLiteral.substring(3, endIndex);
      String newLiteralContents = literalContents
          .replace("\n", "\\n")
          .replace("\"", "\\\"");
      insertAfter(ctx.stop, "\"" + newLiteralContents + "\"");
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
    mode = foundMain ? Mode.JAVA : Mode.STATIC;
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

    ParserRuleContext annotationPoint = null;

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
        annotationPoint = (ParserRuleContext) child;
      }
    }

    // Insert at start of method or after annoation
    if (!hasVisibilityModifier) {
      if (annotationPoint == null) {
        insertBefore(possibleModifiers.getStart(), "public ");
      } else {
        insertAfter(annotationPoint.getStop(), " public ");
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
    boolean isQualifiedName = ctx.getParent() instanceof ProcessingParser.QualifiedNameContext; 
    if (ctx.getText().equals("color") && !isQualifiedName) {
      insertAfter(ctx.stop, "int");
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
   * <p>
   * The size call will need to be rewritten if it is in global or setup, having it hoisted up to
   * settings body.
   * </p>
   *
   * @param ctx The context of the call.
   */
  protected void handleSizeCall(ParserRuleContext ctx) {
    // Check that this is the size call for processing and not a user defined size method.
    if (!calledFromGlobalOrSetup(ctx)) {
      return;
    }

    ParseTree argsContext = ctx.getChild(2);

    boolean thisRequiresRewrite = false;

    boolean isSize = ctx.getChild(0).getText().equals(SIZE_METHOD_NAME);
    boolean isFullscreen = ctx.getChild(0).getText().equals(FULLSCREEN_METHOD_NAME);

    if (isSize && argsContext.getChildCount() > 2) {
      thisRequiresRewrite = true;

      boolean widthValid = sizeParamValid(argsContext.getChild(0));
      if (widthValid) {
        sketchWidth = argsContext.getChild(0).getText();
      } else {
        thisRequiresRewrite = false;
      }

      boolean validHeight = sizeParamValid(argsContext.getChild(2));
      if (validHeight) {
        sketchHeight = argsContext.getChild(2).getText();
      } else {
        thisRequiresRewrite = false;
      }

      if (argsContext.getChildCount() > 3) {
        sketchRenderer = argsContext.getChild(4).getText();
      }

      if (argsContext.getChildCount() > 5) {
        sketchOutputFilename = argsContext.getChild(6).getText();
      }

      if (argsContext.getChildCount() > 7) {
        thisRequiresRewrite = false; // Uesr may have overloaded size.
      }
    }

    if (isFullscreen) {
      sketchWidth = "displayWidth";
      sketchHeight = "displayHeight";

      thisRequiresRewrite = true;
      sizeIsFullscreen = true;

      StringJoiner fullscreenArgsBuilder = new StringJoiner(", ");

      // First arg can be either screen or renderer
      if (argsContext.getChildCount() > 0) {
        String firstArg = argsContext.getChild(0).getText();
        boolean isNumeric = firstArg.matches("\\d+");
        boolean isSpan = firstArg.equals("SPAN");
        boolean isRenderer = !isNumeric && !isSpan;

        fullscreenArgsBuilder.add(firstArg);
        if (isRenderer) {
          sketchRenderer = firstArg;
        }
      }

      // Second arg can only be screen
      if (argsContext.getChildCount() > 2) {
        fullscreenArgsBuilder.add(argsContext.getChild(2).getText());
      }

      fullscreenArgs = fullscreenArgsBuilder.toString();
    }

    if (thisRequiresRewrite) {
      delete(ctx.getParent().start, ctx.getParent().stop);
      insertAfter(ctx.stop, "/* size commented out by preprocessor */");
      sizeRequiresRewrite = true;
    }
  }

  protected void handlePixelDensityCall(ParserRuleContext ctx) {
    // Check that this is a call for processing and not a user defined method.
    if (!calledFromGlobalOrSetup(ctx)) {
      return;
    }

    ParseTree argsContext = ctx.getChild(2);
    if (argsContext.getChildCount() == 0 || argsContext.getChildCount() > 3) {
      return; // User override of pixel density.
    }

    pixelDensity = argsContext.getChild(0).getText();

    delete(ctx.getParent().start, ctx.getParent().stop);
    insertAfter(ctx.getParent().stop, "/* pixelDensity commented out by preprocessor */");
    pixelDensityRequiresRewrite = true;
  }

  protected void handleNoSmoothCall(ParserRuleContext ctx) {
    // Check that this is a call for processing and not a user defined method.
    if (!calledFromGlobalOrSetup(ctx)) {
      return;
    }

    ParseTree argsContext = ctx.getChild(2);
    if (argsContext.getChildCount() > 0) {
      return; // User override of noSmooth.
    }

    delete(ctx.getParent().start, ctx.getParent().stop);
    insertAfter(
      ctx.getParent().stop,
      "/* noSmooth commented out by preprocessor */"
    );
    noSmoothRequiresRewrite = true;
  }

  protected void handleSmoothCall(ParserRuleContext ctx) {
    // Check that this is a call for processing and not a user defined size method.
    if (!calledFromGlobalOrSetup(ctx)) {
      return;
    }

    ParseTree argsContext = ctx.getChild(2);
    if (argsContext.getChildCount() > 2) {
      return; // User may have overloaded smooth;
    }

    if (argsContext.getChildCount() > 0) {
      smoothParam = argsContext.getChild(0).getText();
    } else {
      smoothParam = "";
    }

    delete(ctx.getParent().start, ctx.getParent().stop);
    insertAfter(
      ctx.getParent().stop,
      "/* smooth commented out by preprocessor */"
    );
    smoothRequiresRewrite = true;
  }

  protected boolean calledFromGlobalOrSetup(ParserRuleContext callContext) {
    ParserRuleContext outerContext = callContext.getParent()
        .getParent()
        .getParent()
        .getParent();

    // Check static context first (global)
    if (outerContext instanceof ProcessingParser.StaticProcessingSketchContext) {
      return true;
    }

    // Otherwise check if method called form setup
    ParserRuleContext methodDeclaration = outerContext.getParent()
        .getParent();

    return isMethodSetup(methodDeclaration);
  }

  /**
   * Determine if a method declaration is for setup.
   *
   * @param declaration The method declaration to parse.
   * @return True if setup and false otherwise.
   */
  protected boolean isMethodSetup(ParserRuleContext declaration) {
    if (declaration == null || declaration.getChildCount() < 2) {
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
        "/* autogenerated by Processing revision %04d on %s */",
        Base.getRevision(),
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
  protected void writeImportList(PrintWriterWithEditGen headerWriter, List<ImportStatement> imports,
      RewriteResultBuilder resultBuilder) {

    writeImportList(headerWriter, imports.toArray(new ImportStatement[0]), resultBuilder);
  }

  /**
   * Write a list of imports.
   *
   * @param headerWriter The writer though which the imports should be introduced.
   * @param imports Collection of imports to introduce.
   * @param resultBuilder Builder for reporting out results to the caller.
   */
  protected void writeImportList(PrintWriterWithEditGen headerWriter, ImportStatement[] imports,
        RewriteResultBuilder resultBuilder) {

    for (ImportStatement importDecl : imports) {
      headerWriter.addCodeLine(importDecl.getFullSourceLine());
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

    // First check that a settings method is required at all
    boolean noRewriteRequired = !sizeRequiresRewrite;
    noRewriteRequired = noRewriteRequired && !pixelDensityRequiresRewrite;
    noRewriteRequired = noRewriteRequired && !noSmoothRequiresRewrite;
    noRewriteRequired = noRewriteRequired && !smoothRequiresRewrite;
    if (noRewriteRequired) {
      return;
    }

    // If needed, add the components via a string joiner.
    String settingsOuterTemplate = indent1 + "public void settings() { %s }";

    StringJoiner settingsInner = new StringJoiner("\n");

    if (sizeRequiresRewrite) {
      if (sizeIsFullscreen) {
        settingsInner.add(String.format("fullScreen(%s);", fullscreenArgs));
      } else {

        if (sketchWidth.isEmpty() || sketchHeight.isEmpty()) {
          return;
        }

        StringJoiner argJoiner = new StringJoiner(", ");
        argJoiner.add(sketchWidth);
        argJoiner.add(sketchHeight);

        if (sketchRenderer != null) {
          argJoiner.add(sketchRenderer);
        }

        if (sketchOutputFilename != null) {
          argJoiner.add(sketchOutputFilename);
        }

        settingsInner.add(String.format("size(%s);", argJoiner.toString()));
      }
    }

    if (pixelDensityRequiresRewrite) {
      settingsInner.add(String.format("pixelDensity(%s);", pixelDensity));
    }

    if (noSmoothRequiresRewrite) {
      settingsInner.add("noSmooth();");
    }

    if (smoothRequiresRewrite) {
      settingsInner.add(String.format("smooth(%s);", smoothParam));
    }

    String newCode = String.format(settingsOuterTemplate, settingsInner.toString());

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
      StringJoiner argsJoiner = new StringJoiner(", ");

      boolean shouldFullScreen = Preferences.getBoolean("export.application.present");
      shouldFullScreen = shouldFullScreen || Preferences.getBoolean("export.application.fullscreen");
      if (shouldFullScreen) {
        argsJoiner.add("\"" + PApplet.ARGS_FULL_SCREEN + "\"");

        String bgColor = Preferences.get("run.present.bgcolor");
        argsJoiner.add("\"" + PApplet.ARGS_BGCOLOR + "=" + bgColor + "\"");

        if (Preferences.getBoolean("export.application.stop")) {
          String stopColor = Preferences.get("run.present.stop.color");
          argsJoiner.add("\"" + PApplet.ARGS_STOP_COLOR + "=" + stopColor + "\"");
        } else {
          argsJoiner.add("\"" + PApplet.ARGS_HIDE_STOP + "\"");
        }
      }
      
      argsJoiner.add("\"" + sketchName + "\"");
      footerWriter.addCode(argsJoiner.toString());
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

      rewriteResultBuilder.addOffset(SourceUtil.getCount(newCode, "\n"));
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

  /*
   * ================================================
   * === Utility functions for import statements. ===
   * ================================================
   */

  /**
   * Create a set of non-static imports given the fully qualified names (FQNs) for the types to be imported.
   *
   * @param fullyQualifiedNames The fully qualified names of the types to be imported. This should be like
   *    "java.util.List". Supports wildcards.
   * @return Import statements for the listed types.
   */
  private List<ImportStatement> createPlainImportStatementInfos(List<String> fullyQualifiedNames) {
    return fullyQualifiedNames.stream().map(this::createPlainImportStatementInfo).collect(Collectors.toList());
  }

  /**
   * Create a single non-static import given the fully qualified name (FQN)
   *
   * @param fullyQualifiedName The fully qualified name of the types to be imported. This should be like
   *    "java.util.List". Supports wildcards.
   * @return Newly created ImportStatement.
   */
  private ImportStatement createPlainImportStatementInfo(String fullyQualifiedName) {
    return ImportStatement.parse(fullyQualifiedName);
  }

  private boolean isMethodCall(ParseTree ctx) {
    return ctx instanceof ProcessingParser.MethodCallContext;
  }

  private boolean isVariable(ParseTree ctx) {
    boolean isPrimary = ctx instanceof ProcessingParser.PrimaryContext;
    if (!isPrimary) {
      return false;
    }

    String text = ctx.getText();
    boolean startsWithAlpha = text.length() > 0 && Character.isAlphabetic(text.charAt(0));
    return startsWithAlpha;
  }

  private boolean sizeParamValid(ParseTree ctx) {
    // Method calls and variables not allowed.
    if (isMethodCall(ctx) || isVariable(ctx)) {
      return false;
    }
    
    // If user passed an expression, check subexpressions.
    for (int i = 0; i < ctx.getChildCount(); i++) {
      if (!sizeParamValid(ctx.getChild(i))) {
        return false;
      }
    }
    
    // If all sub-expressions passed and not identifier, is valid.
    return true;
  }

}
