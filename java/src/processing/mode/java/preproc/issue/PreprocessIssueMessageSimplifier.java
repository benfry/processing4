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
along with this program; if not, write to the Free Software Foundation,
Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.mode.java.preproc.issue;


import processing.app.Language;
import processing.app.Platform;
import processing.mode.java.preproc.code.SyntaxUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Facade that tries to create a better error message for syntax issues in input source.
 *
 * <p>
 * Facade that interprets error messages from ANTLR in an attempt to generate an improved error
 * message when describing grammatically incorrect input. This is distinct from compiler errors
 * caused after generating an AST.
 * </p>
 *
 * <p>
 * Note that this is distinct from the {CompileErrorMessageSimplifier}. This operates on issues
 * caused in parsing and services all users whereas the {CompileErrorMessageSimplifier} only
 * operates on issues generated after preprocessing has been successful.
 * </p>
 */
public class PreprocessIssueMessageSimplifier {

  private static AtomicReference<PreprocessIssueMessageSimplifier> instance = new AtomicReference<>();

  // List of strategies to use when trying to simplify an error message.
  private List<PreprocIssueMessageSimplifierStrategy> strategies;


  /* ========================
   * === Public interface ===
   * ========================
   */

  /**
   * Get a shared instance of this singleton.
   *
   * @return Shared instance of this singleton, creating that shared instance if one did not exist
   *    previously.
   */
  public static PreprocessIssueMessageSimplifier get() {
    instance.compareAndSet(null, new PreprocessIssueMessageSimplifier());
    return instance.get();
  }

  /**
   * Get a localized template string.
   *
   * @param stringName Name of the template.
   * @return The template's contents prior to rendering.
   */
  public static String getLocalStr(String stringName) {
    String errStr;
    String retStr;

    if (Platform.isInit()) {
      errStr = Language.text("editor.status.error.syntax");
      retStr = Language.text(stringName);
    } else {
      errStr = DefaultErrorLocalStrSet.get().get("editor.status.error.syntax").orElse("Error");
      retStr = DefaultErrorLocalStrSet.get().get(stringName).orElse(stringName);
    }

    return String.format(errStr, retStr);
  }

  /**
   * Attempt to improve an error message.
   *
   * @param originalMessage Error message generated from ANTLR.
   * @return An improved error message or the originalMessage if no improvements could be made.
   */
  public IssueMessageSimplification simplify(String originalMessage) {
    Optional<IssueMessageSimplification> matching = strategies.stream()
        .map((x) -> x.simplify(originalMessage))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();

    return matching.orElse(new IssueMessageSimplification(originalMessage));
  }

  /* ============================
   * === Enumerate strategies ===
   * ============================
   */

  /**
   * Create a new syntax issue message simplifier with the default simplifier strategies.
   */
  private PreprocessIssueMessageSimplifier() {
    strategies = new ArrayList<>();
    strategies.add(createMissingCurlyAtStartSimplifierStrategy());
    strategies.add(createMissingCurlyAtSemicolonSimplifierStrategy());
    strategies.add(createInvalidGenericDefinitionStrategy());
    strategies.add(createMissingIdentifierSimplifierStrategy());
    strategies.add(createKnownMissingSimplifierStrategy());
    strategies.add(createExtraneousInputSimplifierStrategy());
    strategies.add(createMismatchedInputSimplifierStrategy());
    strategies.add(createInvalidAssignmentStrategy());
    strategies.add(createVariableDeclarationMissingTypeStrategy());
    strategies.add(createInvalidIdentifierStrategy());
    strategies.add(createMissingClassNameStrategy());
    strategies.add(createMethodMissingNameStrategy());
    strategies.add(createErrorOnParameterStrategy());
    strategies.add(createMissingDoubleQuoteStrategy());
    strategies.add(createMissingSingleQuoteStrategy());
    strategies.add(createUnbalancedCurlyStrategy());
    strategies.add(createUnbalancedParenStrategy());
    strategies.add(createUnbalancedChevStrategy());
    strategies.add(new DefaultMessageSimplifier());
  }

  /* ===========================
   * ==== Utility Functions ====
   * ===========================
   *
   * Misc utility functions for message simplification where some are protected to support unit
   * testing.
   */

  /**
   * Get the snippet of "offending code" from an error message if given.
   *
   * @param area The area from which to extract the offending code.
   * @return The offending code described in the error message or the original message if the subset
   *    describing the offending code could not be found.
   */
  protected static String getOffendingArea(String area) {
    return getOffendingArea(area, true);
  }

  /**
   * Get the snippet of "offending code" from an error message if given.
   *
   * @param area The area from which to extract the offending code.
   * @param removeNewline Flag indicating if newlines should be removed or not.
   * @return The offending code described in the error message or the original message if the subset
   *    describing the offending code could not be found.
   */
  private static String getOffendingArea(String area, boolean removeNewline) {
    if (!area.contains("viable alternative")) {
      return area;
    }

    String content = area.replace("no viable alternative at input \'", "");

    if (removeNewline) {
      String[] contentLines = content.replace("\n", "\\n").split("\\\\n");
      content = contentLines[contentLines.length - 1];
    }

    if (content.endsWith("'")) {
      return content.substring(0, content.length() - 1);
    } else {
      return content;
    }
  }

  /**
   * Generate an generic error message.
   *
   * @param unlocalized The unlocalized string. Will be included in resulting message but with
   *    surrounding localized text.
   * @return Semi-localized message.
   */
  private static String getLocalizedGenericError(String unlocalized) {
    String template = getLocalStr("editor.status.error_on");
    return String.format(template, unlocalized);
  }

  /* ==================================
   * ==== Interface for Strategies ====
   * ==================================
   */

  /**
   * Interface for strategies that improve preprocess error messages before showing them to the user.
   */
  protected interface PreprocIssueMessageSimplifierStrategy {

    /**
     * Attempt to simplify an error message.
     *
     * @param message The message to be simplified.
     * @return An optional with an improved message or an empty optional if no improvements could be
     *    made by this strategy.
     */
    Optional<IssueMessageSimplification> simplify(String message);

  }

  /* ==================================================================
   * ==== Strategies where the same character must appear in pairs ====
   * ==================================================================
   *
   * Strategies detecting issues reported in ANTLR where a character was expected to appear an even
   * number of times. This is like double or single quotes. Note that some are left protected to
   * support unit testing.
   */

  /**
   * Strategy to check to make sure that the number of occurrences of a token are even.
   *
   * <p>
   *   Strategy to ensure that there are an even number of tokens like even number of double quotes
   *   for example.
   * </p>
   */
  protected static class EvenCountTemplateMessageSimplifierStrategy
      implements PreprocIssueMessageSimplifierStrategy {

    private final String token;
    private final Optional<String> filter;

    /**
     * Create a new even count simplifier strategy.
     *
     * @param newToken The token that needs to be balanced.
     */
    protected EvenCountTemplateMessageSimplifierStrategy(String newToken) {
      token = newToken;
      filter = Optional.empty();
    }

    /**
     * Create a new even count simplifier strategy where some text needs to be filtered out prior to
     * checking if the simplifier is relevant.
     *
     * @param newToken The token that needs to be balanced.
     * @param newFilter The text to be filtered out.
     */
    protected EvenCountTemplateMessageSimplifierStrategy(String newToken, String newFilter) {
      token = newToken;
      filter = Optional.of(newFilter);
    }

    @Override
    public Optional<IssueMessageSimplification> simplify(String message) {
      String messageContent = getOffendingArea(message);

      if (filter.isPresent()) {
        messageContent = messageContent.replace(filter.get(), "");
      }

      int count = SyntaxUtil.getCount(messageContent, token);

      if (count % 2 == 0) {
        return Optional.empty();
      } else {
        String newMessage = String.format(
            getLocalStr("editor.status.missing.default").replace("%c", "%s"),
            token
        );
        return Optional.of(
            new IssueMessageSimplification(newMessage)
        );
      }
    }

  }

  /**
   * Strategy to detect uneven single quotes.
   */
  protected PreprocIssueMessageSimplifierStrategy createMissingSingleQuoteStrategy() {
    return new EvenCountTemplateMessageSimplifierStrategy("\'", "\\'");
  }

  /**
   * Strategy to detect uneven double quotes.
   */
  protected PreprocIssueMessageSimplifierStrategy createMissingDoubleQuoteStrategy() {
    return new EvenCountTemplateMessageSimplifierStrategy("\"", "\\\"");
  }

  /* ======================================================
   * ==== Strategies where tokens must appear in pairs ====
   * ======================================================
   *
   * Strategies detecting issues reported in ANTLR where a character was expected to appear in a
   * pair with another character. This is like open and close parans. Note that some are left
   * protected to support unit testing.
   */

  /**
   * Template class for checking that two tokens appear in pairs.
   *
   * <p>
   * Template class for message simplification strategies that check for an equal number of
   * occurrences for two characters like "(" and ")".
   * </p>
   */
  protected static class TokenPairTemplateMessageSimplifierStrategy
      implements PreprocIssueMessageSimplifierStrategy {

    private final String token1;
    private final String token2;

    /**
     * Create a new token pair issue detector.
     *
     * @param newToken1 The opening token like "(".
     * @param newToken2 The closing token like ")".
     */
    public TokenPairTemplateMessageSimplifierStrategy(String newToken1, String newToken2) {
      token1 = newToken1;
      token2 = newToken2;
    }

    @Override
    public Optional<IssueMessageSimplification> simplify(String message) {
      String messageContent = getOffendingArea(message);

      int count1 = SyntaxUtil.getCount(messageContent, token1);
      int count2 = SyntaxUtil.getCount(messageContent, token2);

      if (count1 == count2) {
        return Optional.empty();
      }

      String missingToken;
      if (count1 < count2) {
        missingToken = token1;
      } else {
        missingToken = token2;
      }

      String newMessage = String.format(
          getLocalStr("editor.status.missing.default")
              .replace("%c", "%s"), missingToken);

      return Optional.of(
          new IssueMessageSimplification(newMessage)
      );
    }

  }

  /**
   * Strategy for unbalanced curly braces.
   */
  protected PreprocIssueMessageSimplifierStrategy createUnbalancedCurlyStrategy() {
    return new TokenPairTemplateMessageSimplifierStrategy("{", "}");
  }

  /**
   * Strategy for unbalanced chevrons.
   */
  protected PreprocIssueMessageSimplifierStrategy createUnbalancedChevStrategy() {
    return new TokenPairTemplateMessageSimplifierStrategy("<", ">");
  }

  /**
   * Strategy for unbalanced parens.
   */
  protected PreprocIssueMessageSimplifierStrategy createUnbalancedParenStrategy() {
    return new TokenPairTemplateMessageSimplifierStrategy("(", ")");
  }

  /* ================================================================
   * ==== Strategies using regex against the ANTLR error message ====
   * ================================================================
   *
   * Strategies detecting issues reported in ANTLR by using a regex over the ANTLR error message.
   * Note that some are left protected to support unit testing.
   */

  /**
   * Strategy that cleans up errors based on a regex matching the error message.
   */
  protected static class RegexTemplateMessageSimplifierStrategy
      implements PreprocIssueMessageSimplifierStrategy {

    private final Pattern pattern;
    private final String hintTemplate;

    /**
     * Create a new instance of this strategy.
     *
     * @param newRegex The regex that should be matched in order to activate this strategy.
     * @param newHintTemplate template string with a "%s" where the "offending snippet of code" can
     *    be inserted where the resulting rendered template can be used as an error hint for the
     *    user. For example, "Invalid identifier near %s" may be rendered to the user like "Syntax
     *    error. Hint: Invalid identifier near ,1a);".
     */
    public RegexTemplateMessageSimplifierStrategy(String newRegex, String newHintTemplate) {
      pattern = Pattern.compile(newRegex);
      hintTemplate = newHintTemplate;
    }

    @Override
    public Optional<IssueMessageSimplification> simplify(String message) {
      if (pattern.matcher(message).find()) {
        String newMessage = String.format(
            hintTemplate,
            getOffendingArea(message)
        );

        return Optional.of(
            new IssueMessageSimplification(newMessage, getAttributeToPrior())
        );
      } else {
        return Optional.empty();
      }
    }

    /**
     * Determine if this issue should be attributed to the prior token.
     *
     * @return True if should be attributed to prior token. False otherwise.
     */
    public boolean getAttributeToPrior() {
      return false;
    }

  }

  /**
   * Shortcut to create a regex matcher with a localized error message.
   *
   * @param regex The regex to match.
   * @param localStr The localized string identifier to use when the regex matches.
   * @return Newly created simplifier strategy.
   */
  protected PreprocIssueMessageSimplifierStrategy createRegexStrategyUsingLocalStr(String regex,
      String localStr) {

    return new RegexTemplateMessageSimplifierStrategy(
        regex,
        getLocalStr(localStr)
    );
  }

  /**
   * Strategy for invalid parameter.
   */
  protected PreprocIssueMessageSimplifierStrategy createErrorOnParameterStrategy() {
    return createRegexStrategyUsingLocalStr(
        "([a-zA-Z0-9_]+\\s*,|[a-zA-Z0-9_]+\\)|\\([^\\)]+)",
        "editor.status.bad.parameter"
    );
  }

  /**
   * Strategy for missing method name.
   */
  protected PreprocIssueMessageSimplifierStrategy createMethodMissingNameStrategy() {
    return createRegexStrategyUsingLocalStr(
        "[a-zA-Z0-9_]+\\s*\\(.*\\)\\s*\\{",
        "editor.status.missing.name"
    );
  }

  /**
   * Strategy for missing class name.
   */
  protected PreprocIssueMessageSimplifierStrategy createMissingClassNameStrategy() {
    return createRegexStrategyUsingLocalStr(
        ".*(class|interface)\\s*[a-zA-Z0-9_]*\\s+(extends|implements|<.*>)?\\s*[a-zA-Z0-9_]*\\s*\\{.*",
        "editor.status.missing.name"
    );
  }

  /**
   * Strategy for invalid identifier.
   */
  protected PreprocIssueMessageSimplifierStrategy createInvalidIdentifierStrategy() {
    return createRegexStrategyUsingLocalStr(
        "([.\\s]*[0-9]+[a-zA-Z_<>]+[0-9a-zA-Z_<>]*|\\s+\\d+[a-zA-Z_<>]+|[0-9a-zA-Z_<>]+\\s+[0-9]+)",
        "editor.status.bad.identifier"
    );
  }

  /**
   * Strategy for invalid variable declaration.
   */
  protected PreprocIssueMessageSimplifierStrategy createVariableDeclarationMissingTypeStrategy() {
    return createRegexStrategyUsingLocalStr(
        "[a-zA-Z_]+[0-9a-zA-Z_]*\\s*(=[^\n\\n;]*)?;'?$",
        "editor.status.missing.type"
    );
  }

  /**
   * Strategy for invalid assignment.
   */
  protected PreprocIssueMessageSimplifierStrategy createInvalidAssignmentStrategy() {
    return createRegexStrategyUsingLocalStr(
        "[.\\n]*[0-9a-zA-Z\\_<>]+\\s*=[\\s';]*$",
        "editor.status.bad.assignment"
    );
  }

  /**
   * Strategy for invalid generic.
   */
  protected PreprocIssueMessageSimplifierStrategy createInvalidGenericDefinitionStrategy() {
    return createRegexStrategyUsingLocalStr(
        "<>'?$",
        "editor.status.bad.generic"
    );
  }

  /* ==========================
   * ==== Other Strategies ====
   * ==========================
   *
   * Strategies that cannot work using some of the more generalized methods implemented above.
   * Note that some are protected to support unit testing.
   */

  /**
   * Strategy to catch a missing curly that got caught by ANTLR at the start of a statement.
   */
  protected PreprocIssueMessageSimplifierStrategy createMissingCurlyAtStartSimplifierStrategy() {

    return message -> {
      boolean matches = message.endsWith("expecting {'throws', '{'}");
      matches = matches || message.endsWith("expecting {'throws', '{', '[', ';'}");

      if (!matches) {
        return Optional.empty();
      }

      return Optional.of(new IssueMessageSimplification(
          getLocalStr("editor.status.missing.left_curly_bracket")
      ));
    };
  }

  /**
   * Strategy to catch a missing curly at a semicolon.
   */
  protected PreprocIssueMessageSimplifierStrategy createMissingCurlyAtSemicolonSimplifierStrategy() {
    return message -> {
      if (!message.equals("missing ';' at '{'")) {
        return Optional.empty();
      }

      return Optional.of(new IssueMessageSimplification(
          getLocalStr("editor.status.missing.right_curly_bracket")
      ));
    };
  }

  /**
   * Strategy to check for an error indicating that an identifier was expected but not given.
   */
  protected PreprocIssueMessageSimplifierStrategy createMissingIdentifierSimplifierStrategy() {
    return message -> {
      if (message.toLowerCase().contains("missing identifier at")) {
        String newMessage = String.format(
            getLocalStr("editor.status.missing.name"),
            message.replace("missing Identifier at", "")
        );
        return Optional.of(
            new IssueMessageSimplification(newMessage)
        );
      } else {
        return Optional.empty();
      }
    };
  }

  /**
   * Strategy to handle missing token messages.
   */
  protected PreprocIssueMessageSimplifierStrategy createKnownMissingSimplifierStrategy() {
    final Pattern parsePattern = Pattern.compile(".*missing '(.*)' at .*");
    return message -> {
      if (message.toLowerCase().contains("missing")) {
        String missingPiece;
        Matcher matcher = parsePattern.matcher(message);
        if (matcher.find()) {
          missingPiece = matcher.group(1);
        } else {
          missingPiece = "character";
        }

        String langTemplate = getLocalStr("editor.status.missing.default")
            .replace("%c", "%s");

        String newMessage = String.format(langTemplate, missingPiece);

        return Optional.of(new IssueMessageSimplification(newMessage));
      } else {
        return Optional.empty();
      }
    };
  }

  /**
   * Strategy to handle extraneous input messages.
   */
  protected PreprocIssueMessageSimplifierStrategy createExtraneousInputSimplifierStrategy() {
    return message -> {
      if (message.toLowerCase().contains("extraneous")) {
        String innerMsg = getOffendingArea(message);

        String newMessageOuter = getLocalStr("editor.status.extraneous");
        String newMessage = String.format(newMessageOuter, innerMsg);

        return Optional.of(
            new IssueMessageSimplification(newMessage)
        );
      } else {
        return Optional.empty();
      }
    };
  }

  /**
   * Strategy to explain a mismatched input issue.
   */
  protected PreprocIssueMessageSimplifierStrategy createMismatchedInputSimplifierStrategy() {
    final Pattern parser = Pattern.compile("mismatched input '(.*)' expecting ");
    return message -> {
      if (message.toLowerCase().contains("mismatched input")) {
        Matcher matcher = parser.matcher(message);

        String newMessage = String.format(
            getLocalStr("editor.status.mismatched"),
            matcher.find() ? matcher.group(1) : message
        );

        return Optional.of(
            new IssueMessageSimplification(
                newMessage
            )
        );
      } else {
        return Optional.empty();
      }
    };
  }

  /**
   * Default strategy to use if other message simplification strategies have failed.
   */
  protected static class DefaultMessageSimplifier implements PreprocIssueMessageSimplifierStrategy {

    @Override
    public Optional<IssueMessageSimplification> simplify(String message) {
      if (message.contains("viable alternative")) {
        String newMessage = String.format(
            getLocalizedGenericError("%s"),
            getOffendingArea(message)
        );
        return Optional.of(
            new IssueMessageSimplification(newMessage)
        );
      } else {
        return Optional.of(
            new IssueMessageSimplification(message)
        );
      }
    }
  }

}
