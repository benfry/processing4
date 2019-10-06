package processing.mode.java.preproc.code;


/**
 * Utility to assist with preprocessing imports.
 */
public class ImportUtil {

  /**
   * Get the imports required by processing itself.
   *
   * @return List of imports required by processing itself.
   */
  public static String[] getCoreImports() {
    return new String[] {
      "processing.core.*",
      "processing.data.*",
      "processing.event.*",
      "processing.opengl.*"
    };
  }

  /**
   * Get the list of imports included by default on behalf of the user.
   *
   * @return List of "default" imports not required for processing but included for user
   *    convenience.
   */
  public static String[] getDefaultImports() {
    // These may change in-between (if the prefs panel adds this option)
    //String prefsLine = Preferences.get("preproc.imports");
    //return PApplet.splitTokens(prefsLine, ", ");
    return new String[] {
      "java.util.HashMap",
      "java.util.ArrayList",
      "java.io.File",
      "java.io.BufferedReader",
      "java.io.PrintWriter",
      "java.io.InputStream",
      "java.io.OutputStream",
      "java.io.IOException"
    };
  }

}
