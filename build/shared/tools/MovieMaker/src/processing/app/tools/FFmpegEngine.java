package processing.app.tools;

import java.awt.*;
import java.io.File;
import java.io.IOException;

class FFmpegEngine {
  Component parent;


  FFmpegEngine(Component parent) {
    this.parent = parent;
  }


  String[] getFormats() {
    return new String[] {
      "MPEG-4", "Animated GIF"
    };
  }


  void write(File movieFile, File[] imgFiles, File soundFile,
             int width, int height, double fps, String formatName) throws IOException {
  }
}