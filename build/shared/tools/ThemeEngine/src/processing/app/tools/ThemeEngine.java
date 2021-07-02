package processing.app.tools;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Arrays;
import java.util.prefs.Preferences;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;

import processing.app.Base;
import processing.app.Language;

import processing.app.Platform;


public class ThemeEngine implements Tool {
  Base base;

  public String getMenuTitle() {
    //return Language.text("theme_engine");
    return "Theme Engine";
  }


  public void init(Base base) {
    this.base = base;
  }


  public void run() {
    //setVisible(true);
  }
}
