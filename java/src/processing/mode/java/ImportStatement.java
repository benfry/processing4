/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
Part of the Processing project - http://processing.org
Copyright (c) 2012-19 The Processing Foundation

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License version 2
as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software Foundation, Inc.
51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
*/

package processing.mode.java;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;

/**
 * Wrapper for import statements
 */
public class ImportStatement {

  private static final String importKw = "import";
  private static final String staticKw = "static";

//  private boolean isClass;
  private boolean isStarred;
  private boolean isStatic;

  /**
   * Full class name of the import with all packages
   * Ends with star for starred imports
   */
  private String memberName;

  /**
   * Name of the package e.g. everything before last dot
   */
  private String packageName;

  private ImportStatement() { }

  /**
   * Create an import statement for a full package.
   *
   * @param cls The fully qualified name of the package.
   * @return ImportStatement which imports all package members in a non-static context using a wildcard.
   */
  public static ImportStatement wholePackage(String pckg) {
    ImportStatement is = new ImportStatement();
    is.packageName = pckg;
    is.memberName = "*";
    is.isStarred = true;
    return is;
  }

  /**
   * Create an import statement for a single class.
   *
   * @param cls The fully qualified name of the class.
   * @return ImportStatement which imports the class in a non-static context.
   */
  public static ImportStatement singleClass(String cls) {
    ImportStatement is = new ImportStatement();
    int lastDot = cls.lastIndexOf('.');
    is.memberName = lastDot >= 0 ? cls.substring(lastDot+1) : cls;
    is.packageName = lastDot >= 0 ? cls.substring(0, lastDot) : "";
//    is.isClass = true;
    return is;
  }

  /**
   * Prase an import statement from a fully qualified name.
   *
   * @param importString The fully qualified name from which an import statement should be built. This supports static
   *    prepended so both "java.util.List" and "static org.processing.package.Factory.build" are supported but
   *    "import java.util.List;" is not. Note that the static prepending is required if the import is static.
   * @return Newly parsed import statement information.
   */
  public static ImportStatement parse(String importString) {
    Matcher matcher = SourceUtil.IMPORT_REGEX_NO_KEYWORD.matcher(importString);
    if (!matcher.find()) return null;

    return parse(matcher.toMatchResult());
  }

  /**
   * Parse an import statement from a regex match found via SourceUtils.*REGEX*.
   *
   * @param match The regex match from which an import statement should be built. Can be from IMPORT_REGEX_NO_KEYWORD or
   *    IMPORT_REGEX or equivalent.
   * @return Newly parsed import statement information.
   */
  public static ImportStatement parse(MatchResult match) {
    ImportStatement is = new ImportStatement();

    is.isStatic = match.group(2) != null;
    String pckg = match.group(3);
    pckg = (pckg == null) ? "" : pckg.replaceAll("\\s","");

    String memberName = match.group(4);
    is.isStarred = memberName.equals("*");

    // Deal with static member imports whose "className" is actually a "member name".
    boolean endsWithPeriod = pckg.endsWith(".");
    if (is.isStatic) {
      String withContainingTypeNameAtEnd = endsWithPeriod ? pckg.substring(0, pckg.length() - 1) : pckg;
      int periodOfContainingTypeName = withContainingTypeNameAtEnd.lastIndexOf(".");
      String containingTypeName = withContainingTypeNameAtEnd.substring(periodOfContainingTypeName + 1);
      is.packageName = withContainingTypeNameAtEnd.substring(0, periodOfContainingTypeName);
      is.memberName = containingTypeName + "." + memberName;
    } else {
      is.packageName = endsWithPeriod ? pckg.substring(0, pckg.length() - 1) : pckg;
      is.memberName = memberName;
    }

    return is;
  }

  /**
   * Get the source line needed to execute this import.
   *
   * @return The java code required for executing this import.
   */
  public String getFullSourceLine() {
    return importKw + " " + (isStatic ? (staticKw + " ") : "") + packageName + "." + memberName + ";";
  }

  /**
   * Get the fully qualified member name which includes the package path.
   *
   * @return The fully qualified member name including the parent class. This is "java.util.List" in the case of
   *    "import java.util.List". Note that, in the case of static imports, it will include the member imported so
   *    "org.processing.package.Factory.build" would be returned for
   *    "import static org.processing.package.Factory.build".
   */
  public String getFullMemberName(){
    return packageName + "." + memberName;
  }

  /**
   * Get the end of the import statement with the type to be imported.
   *
   * @return This is the class name (List) in the case of "import java.util.List" or "*" in the case of a wildcard. For
   *    static imports this will be the member within the containing class (Factory.build in the case of
   *    "import static org.processing.package.Factory.build").
   */
  public String getMemberName(){
    return memberName;
  }

  /**
   * Get the package from which the import is being made.
   *
   * @return The package "java.util" from "import java.util.List". Note that, in the case of wildcards, the wildcard
   *    will be in the member name and not the class name.
   */
  public String getPackageName(){
    return packageName;
  }

  /**
   * Determine if this import is a wildcard import.
   *
   * @return True if the FQN (fully qualified name) ends in a wildcard and false otherwise.
   */
  public boolean isStarredImport() {
    return isStarred;
  }

  /**
   * Determine if this import statement is a static import.
   *
   * @return True if of the form "import static {FQN}" where FQN refers to the fully qualified name and false otherwise.
   */
  public boolean isStaticImport() {
    return isStatic;
  }

  /**
   * Check if the import statements refer to the same import target.
   *
   * @param is The other import statement.
   * @return True of the two ImportStatements refer to the same import target and false otherwise.
   */
  public boolean isSameAs(ImportStatement is) {
    return packageName.equals(is.packageName) &&
        memberName.equals(is.memberName) &&
        isStatic == is.isStatic;
  }
}