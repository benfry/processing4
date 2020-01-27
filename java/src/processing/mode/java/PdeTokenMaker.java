package processing.mode.java;

import java.util.Map;

import org.fife.ui.rsyntaxtextarea.TokenMap;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.fife.ui.rsyntaxtextarea.modes.JavaTokenMaker;


public class PdeTokenMaker extends JavaTokenMaker {
  //static TokenMap extraTokens = new TokenMap(false);
  TokenMap extraTokens;


  public PdeTokenMaker(Map<String, Integer> lookup) {
    //extraTokens = getKeywords();
    extraTokens = new TokenMap(false);
    for (Map.Entry<String, Integer> entry : lookup.entrySet()) {
      extraTokens.put(entry.getKey(), entry.getValue());
    }
  }


  @Override
  public void addToken(char[] array, int start, int end,
                       int tokenType, int startOffset, boolean hyperlink) {
    // This assumes all of your extra tokens would normally be scanned as IDENTIFIER.
    if (tokenType == TokenTypes.IDENTIFIER) {
      int newType = extraTokens.get(array, start, end);
      if (newType > -1) {
        tokenType = newType;
      }
    }
    super.addToken(array, start, end, tokenType, startOffset, hyperlink);
  }


  /*
  public void addKeyword(String keyword, int type) {
    extraTokens.put(keyword, type);
  }


  public void clear() {
    extraTokens = new TokenMap();
  }


  static public TokenMap getKeywords() {
    if (extraTokens == null) {
      try {
        extraTokens = new TokenMap(false);

        HashMap<String, Integer> keywords = PdeKeywords.get();
        Set<String> keys = keywords.keySet();
        for (String key : keys) {
          extraTokens.put(key, keywords.get(key));
        }

      } catch (Exception e) {
        Base.showError("Problem loading keywords",
                       "Could not load keywords.txt,\n" +
                           "please re-install Arduino.", e);
        System.exit(1);
      }
    }
    return extraTokens;
  }
  */
}