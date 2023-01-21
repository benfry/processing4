/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2015 The Processing Foundation

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU General Public License
  version 2, as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.app;

import processing.app.ui.Toolkit;

import java.awt.EventQueue;
import java.awt.Frame;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

public class Messages {
  /**
   * "No cookie for you" type messages. Nothing fatal or all that
   * much of a bummer, but something to notify the user about.
   */
  static public void showMessage(String title, String message) {
    if (title == null) title = "Message";

    if (Base.isCommandLine()) {
      System.out.println(title + ": " + message);

    } else {
      JOptionPane.showMessageDialog(new Frame(), message, title,
                                    JOptionPane.INFORMATION_MESSAGE);
    }
  }


  /**
   * Non-fatal error message.
   */
  static public void showWarning(String title, String message) {
    showWarning(title, message, null);
  }

  /**
   * Non-fatal error message with optional stack trace side dish.
   */
  static public void showWarning(String title, String message, Throwable e) {
    if (title == null) title = "Warning";

    if (Base.isCommandLine()) {
      System.out.println(title + ": " + message);

    } else {
      JOptionPane.showMessageDialog(new Frame(), message, title,
                                    JOptionPane.WARNING_MESSAGE);
    }
    if (e != null) e.printStackTrace();
  }


  /**
   * Non-fatal error message with two levels of formatting.
   * Unlike the others, this is non-blocking and will run later on the EDT.
   */
  static public void showWarningTiered(String title,
                                       String primary, String secondary,
                                       Throwable e) {
    if (Base.isCommandLine()) {
      // TODO All these messages need to be handled differently for
      //      proper parsing on the command line. Many have \n in them.
      System.out.println(title + ": " + primary + "\n" + secondary);

    } else {
      EventQueue.invokeLater(() -> {
        JOptionPane.showMessageDialog(new JFrame(),
          Toolkit.formatMessage(primary, secondary),
          title, JOptionPane.WARNING_MESSAGE);
      });
    }
    if (e != null) e.printStackTrace();
  }


  /**
   * Show an error message that's actually fatal to the program.
   * This is an error that can't be recovered. Use showWarning()
   * for errors that allow P5 to continue running.
   */
  static public void showError(String title, String message, Throwable e) {
    if (title == null) title = "Error";

    if (Base.isCommandLine()) {
      System.err.println(title + ": " + message);

    } else {
      JOptionPane.showMessageDialog(new Frame(), message, title,
                                    JOptionPane.ERROR_MESSAGE);
    }
    if (e != null) e.printStackTrace();
    System.exit(1);
  }


  /**
   * Warning window that includes the stack trace.
   */
  static public void showTrace(String title, String message,
                               Throwable t, boolean fatal) {
    if (title == null) title = fatal ? "Error" : "Warning";

    if (Base.isCommandLine()) {
      System.err.println(title + ": " + message);
      if (t != null) {
        t.printStackTrace();
      }

    } else {
      StringWriter sw = new StringWriter();
      t.printStackTrace(new PrintWriter(sw));

      JOptionPane.showMessageDialog(new Frame(),
                                    // first <br/> clears to the next line
                                    // second <br/> is a shorter height blank space before the trace
                                    Toolkit.formatMessage(message + "<br/><tt><br/>" + sw + "</tt>"),
                                    title,
                                    fatal ?
                                    JOptionPane.ERROR_MESSAGE :
                                    JOptionPane.WARNING_MESSAGE);

      if (fatal) {
        System.exit(1);
      }
    }
  }


  static public int showYesNoQuestion(Frame editor, String title,
                                      String primary, String secondary) {
    if (!Platform.isMacOS()) {
      return JOptionPane.showConfirmDialog(editor,
                                           Toolkit.formatMessage(primary, secondary),
                                           //"<html><body>" +
                                           //"<b>" + primary + "</b>" +
                                           //"<br>" + secondary,
                                           title,
                                           JOptionPane.YES_NO_OPTION,
                                           JOptionPane.QUESTION_MESSAGE);
    } else {
      int result = showCustomQuestion(editor, title, primary, secondary,
          0, "Yes", "No");
      if (result == 0) {
        return JOptionPane.YES_OPTION;
      } else if (result == 1) {
        return JOptionPane.NO_OPTION;
      } else {
        return JOptionPane.CLOSED_OPTION;
      }
    }
  }


  /**
   * @param highlight A valid array index for options[] that specifies the
   *                  default (i.e. safe) choice.
   * @return The (zero-based) index of the selected value, -1 otherwise.
   */
  static public int showCustomQuestion(Frame editor, String title,
                                       String primary, String secondary,
                                       int highlight, String... options) {
    Object result;
    if (!Platform.isMacOS()) {
      return JOptionPane.showOptionDialog(editor,
        Toolkit.formatMessage(primary, secondary), title,
          JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
          options, options[highlight]);
    } else {
      JOptionPane pane =
        new JOptionPane(Toolkit.formatMessage(primary, secondary),
                        JOptionPane.QUESTION_MESSAGE);

      pane.setOptions(options);

      // highlight the safest option ala apple hig
      pane.setInitialValue(options[highlight]);

      JDialog dialog = pane.createDialog(editor, null);
      dialog.setVisible(true);

      result = pane.getValue();
    }
    for (int i = 0; i < options.length; i++) {
      if (result != null && result.equals(options[i])) return i;
    }
    return -1;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static public void log(Object from, String message) {
    if (Base.DEBUG) {
      System.out.println(from.getClass().getName() + ": " + message);
    }
  }


  static public void log(String message) {
    if (Base.DEBUG) {
      System.out.println(message);
    }
  }


  static public void logf(String message, Object... args) {
    if (Base.DEBUG) {
      System.out.printf(message, args);
    }
  }


  static public void err(String message) {
    err(message, null);
  }


  static public void err(String message, Throwable e) {
    if (Base.DEBUG) {
      if (message != null) {
        System.err.println(message);
      }
      if (e != null) {
        e.printStackTrace();
      }
    }
  }
}
