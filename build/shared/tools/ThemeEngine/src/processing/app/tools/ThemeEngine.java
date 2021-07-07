package processing.app.tools;

import processing.app.Base;
import processing.app.ui.Editor;


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
    //Preferences.init();

    for (Editor editor : base.getEditors()) {
      System.out.println("Updating theme for " + editor.getSketch().getName());
      //editor.applyPreferences();
      editor.updateTheme();
    }
  }
}
