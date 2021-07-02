package processing.app.tools;

import processing.app.Base;


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
