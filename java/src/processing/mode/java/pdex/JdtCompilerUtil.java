package processing.mode.java.pdex;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


/**
 * Utility to help run a compilation through the JDT.
 */
public class JdtCompilerUtil {

  /**
   * Create a JDT compilation unit.
   *
   * @param parser The parser to use to read the source.
   * @param source The source after processing with ANTLR.
   * @param options The JDT compiler options.
   * @return The JDT parsed compilation unit.
   */
  public static CompilationUnit makeAST(ASTParser parser,
                                         char[] source,
                                         Map<String, String> options) {
    parser.setSource(source);
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setCompilerOptions(options);
    parser.setStatementsRecovery(true);

    return (CompilationUnit) parser.createAST(null);
  }

  /**
   * Establish parser options before creating a JDT compilation unit.
   *
   * @param parser The parser to use to read the source.
   * @param source The source after processing with ANTLR.
   * @param options The JDT compiler options.
   * @param className The name of the sketch.
   * @param classPath The classpath to use in compliation.
   * @return The JDT parsed compilation unit.
   */
  public static CompilationUnit makeASTWithBindings(ASTParser parser,
                                                     char[] source,
                                                     Map<String, String> options,
                                                     String className,
                                                     String[] classPath) {
    parser.setSource(source);
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setCompilerOptions(options);
    parser.setStatementsRecovery(true);
    parser.setUnitName(className);
    parser.setEnvironment(classPath, null, null, false);
    parser.setResolveBindings(true);

    return (CompilationUnit) parser.createAST(null);
  }


  static public final Map<String, String> COMPILER_OPTIONS;
  static {
    Map<String, String> compilerOptions = new HashMap<>();

    compilerOptions.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_11);
    compilerOptions.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_11);
    compilerOptions.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_11);

    // See http://help.eclipse.org/mars/index.jsp?topic=%2Forg.eclipse.jdt.doc.isv%2Fguide%2Fjdt_api_options.htm&anchor=compiler

    final String[] generate = {
        JavaCore.COMPILER_LINE_NUMBER_ATTR,
        JavaCore.COMPILER_SOURCE_FILE_ATTR
    };

    final String[] ignore = {
        JavaCore.COMPILER_PB_UNUSED_IMPORT,
        JavaCore.COMPILER_PB_MISSING_SERIAL_VERSION,
        JavaCore.COMPILER_PB_RAW_TYPE_REFERENCE,
        JavaCore.COMPILER_PB_REDUNDANT_TYPE_ARGUMENTS,
        JavaCore.COMPILER_PB_UNCHECKED_TYPE_OPERATION
    };

    final String[] warn = {
        JavaCore.COMPILER_PB_NO_EFFECT_ASSIGNMENT,
        JavaCore.COMPILER_PB_NULL_REFERENCE,
        JavaCore.COMPILER_PB_POTENTIAL_NULL_REFERENCE,
        JavaCore.COMPILER_PB_REDUNDANT_NULL_CHECK,
        JavaCore.COMPILER_PB_POSSIBLE_ACCIDENTAL_BOOLEAN_ASSIGNMENT,
        JavaCore.COMPILER_PB_UNUSED_LABEL,
        JavaCore.COMPILER_PB_UNUSED_LOCAL,
        JavaCore.COMPILER_PB_UNUSED_OBJECT_ALLOCATION,
        JavaCore.COMPILER_PB_UNUSED_PARAMETER,
        JavaCore.COMPILER_PB_UNUSED_PRIVATE_MEMBER
    };

    for (String s : generate) compilerOptions.put(s, JavaCore.GENERATE);
    for (String s : ignore)   compilerOptions.put(s, JavaCore.IGNORE);
    for (String s : warn)     compilerOptions.put(s, JavaCore.WARNING);

    COMPILER_OPTIONS = Collections.unmodifiableMap(compilerOptions);
  }

}
