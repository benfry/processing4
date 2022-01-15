package processing.app.tools;

import processing.app.Base;
import processing.app.ui.Editor;
import processing.app.ui.Theme;

import java.io.File;


public class ThemeEngine implements Tool {
  Base base;

  public String getMenuTitle() {
    return "Update Theme";
  }


  public void init(Base base) {
    this.base = base;
    //WebFrame.init();
  }


  public void run() {
    Editor activeEditor = base.getActiveEditor();

    File sketchbookFile = Theme.getSketchbookFile();
    if (!sketchbookFile.exists()) {
      // When first called, just create the theme.txt file
      Theme.save();

      if (activeEditor != null) {
        activeEditor.statusNotice("Saved theme.txt to " + sketchbookFile);
        System.out.println("After you make changes to theme.txt, " +
                           "select \u201C" + getMenuTitle() + "\u201D " +
                           "again to use the new colors.");
      }

    } else {
      // Normally, just reset the theme by loading theme.txt
      //setVisible(true);
      //Preferences.init();
      Theme.load();

      for (Editor editor : base.getEditors()) {
        //System.out.println("Updating theme for " + editor.getSketch().getName());
        editor.updateTheme();
      }

      if (activeEditor != null) {
        activeEditor.statusNotice("Finished updating theme.");
      }
    }
  }
}
