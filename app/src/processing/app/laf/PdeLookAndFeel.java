package processing.app.laf;

import javax.swing.plaf.basic.BasicLookAndFeel;


/**
 * Custom Look and Feel class. Not currently in use:
 * for now, only individual component UIs are being overridden.
 * https://github.com/AdoptOpenJDK/openjdk-jdk8u/blob/master/jdk/src/share/classes/javax/swing/plaf/basic/BasicLookAndFeel.java
 */
public class PdeLookAndFeel extends BasicLookAndFeel {

  public String getDescription( ) {
    return "The Processing Look and Feel";
  }


  public String getID( ) {
    return "Processing";
  }


  public String getName( ) {
    return "Processing";
  }


  public boolean isNativeLookAndFeel( ) {
    return false;
  }


  public boolean isSupportedLookAndFeel( ) {
    return true;
  }
}