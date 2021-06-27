package processing.app.tools;

import processing.app.Language;
import processing.app.Platform;

import javax.swing.*;
import java.awt.Component;
import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


class FFmpegEngine {
  Pattern framePattern = Pattern.compile("\\sframe=(\\d+)\\s");

  Component parent;
  String ffmpegPath;


  FFmpegEngine(Component parent) {
    this.parent = parent;

    // Use the location of this jar to find the "tool" folder
    String jarPath =
      getClass().getProtectionDomain().getCodeSource().getLocation().getFile();
    File toolFolder = new File(jarPath).getParentFile();
    // Use that path to get the full path to our copy of the ffmpeg binary
    String ffmpegName = Platform.isWindows() ? "ffmpeg.exe" : "ffmpeg";
    ffmpegPath = new File(toolFolder, ffmpegName).getAbsolutePath();
  }


  String[] getFormats() {
    return new String[] {
      "MPEG-4", "MPEG-4 (lossless)", "Animated GIF", "Animated GIF (looping)"
    };
  }


  void write(File movieFile, File[] imgFiles, File soundFile,
             int width, int height, double fps, String formatName) throws IOException {
    // Write a temporary file with the names of all the images.
    // This removes the requirement for images using %04d format (and having
    // to detect the exact variant). Also, the number of images is likely to
    // be too long for command line arguments on some platforms.
    File listingFile = File.createTempFile("listing", ".txt");
    PrintWriter writer = new PrintWriter(new FileWriter(listingFile));
    for (File file : imgFiles) {
      writer.println("file '" + file.getAbsolutePath() + "'");
    }
    writer.flush();
    writer.close();

    // path specified by the user (may be changed later to append file type)
    String outputPath = movieFile.getAbsolutePath();

    List<String> cmd = new ArrayList<>();
    cmd.add(ffmpegPath);

    // delete the file if it already exists
    cmd.add("-y");

    // set frame rate
    cmd.add("-r");
    cmd.add(String.valueOf(fps));

    // input format is image files
    cmd.add("-f");
    cmd.add("image2");

    // allow absolute paths in the file list
    cmd.add("-safe");
    cmd.add("0");

    // use the concatenation filter to read our entries from a file
    // https://trac.ffmpeg.org/wiki/Concatenate
    // (not enough to simply specify -i with a .txt file)
    cmd.add("-f");
    cmd.add("concat");

    // temporary file with the list of all images
    cmd.add("-i");
    cmd.add(listingFile.getAbsolutePath());

    // pipe:1 sends progress to stdout, pipe:2 to stderr
    cmd.add("-progress");
    cmd.add("pipe:2");

    String formatArgs = "fps=" + fps;
    if (width != 0 && height != 0) {
      formatArgs += ",scale=" + width + ":" + height + ":flags=lanczos";
    }

    if (formatName.startsWith("MPEG-4")) {
      // slideshow: http://trac.ffmpeg.org/wiki/Slideshow
      // options for compatibility: https://superuser.com/a/424024

      if (soundFile != null) {
        cmd.add("-i");
        cmd.add(soundFile.getAbsolutePath());
      }

      // use the h.264 video codec
      cmd.add("-vcodec");
      cmd.add("libx264");

      if (formatName.contains("lossless")) {
        // https://trac.ffmpeg.org/wiki/Encode/H.264
        cmd.add("-preset");
        // can also use "veryslow" for better compression
        cmd.add("ultrafast");
        cmd.add("-crf");
        cmd.add("0");
      }
      // high quality images
      cmd.add("-crf");
      cmd.add("21");  // 18 to 25, with 18 the lowest

      // make compatible with QuickTime and others
      cmd.add("-pix_fmt");
      cmd.add("yuv420p");

      // if there's a resize, specify it and the type of scaling
      if (width != 0 && height != 0) {
        cmd.add("-vf");
        cmd.add(formatArgs);
      }

      if (soundFile != null) {
        cmd.add("-acodec");
        cmd.add("aac");
      }

      // move container metadata to the beginning of the file
      cmd.add("-movflags");
      cmd.add("faststart");

      if (!outputPath.toLowerCase().endsWith(".mp4")) {
        outputPath += ".mp4";
      }

    } else if (formatName.startsWith("Animated GIF")) {
      // ffmpeg -r 30 -f image2 -safe 0 -f concat -i listing.txt -vf "fps=30,scale=320:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse" -loop 0 ~/Desktop/output.gif
      // sets the gif palette nicely, based on https://superuser.com/a/556031
      formatArgs += ",split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse";
      cmd.add("-vf");
      cmd.add(formatArgs);

      cmd.add("-loop");
      cmd.add(formatName.contains("loop") ? "1" : "0");

      if (!outputPath.toLowerCase().endsWith(".gif")) {
        outputPath += ".gif";
      }
    }

    cmd.add(outputPath);
    //final String outputName = new File(outputPath).getName();

    // pass cmd to Runtime exec
    // read the output and set the progress bar
    // show a message (success/failure) when complete

    final ProgressMonitor progress = new ProgressMonitor(parent,
      Language.interpolate("movie_maker.progress.creating_file_name", movieFile.getName()),
      Language.text("movie_maker.progress.creating_output_file"),
      0, imgFiles.length);

    // https://stackoverflow.com/a/33386692
    Process p = new ProcessBuilder(cmd).start();
    StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream(), System.out::println);
    StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream(), line -> {
      // TODO handle process.isCanceled()
      //System.out.println("msg: " + msg);
      Matcher m = framePattern.matcher(line);
      if (m.find()) {
        int frameCount = Integer.parseInt(m.group(1));
        // this was used to show which (input) image file was being handled
        //progress.setNote(Language.interpolate("movie_maker.progress.processing", inputName));
        System.out.println(frameCount + " of " + imgFiles.length);
        progress.setProgress(frameCount);
      }
    });

    new Thread(outputGobbler).start();
    new Thread(errorGobbler).start();
    try {
      int result = p.waitFor();
      System.out.println(result);
      progress.close();

    } catch (InterruptedException ignored) {  }
  }


  static class StreamGobbler implements Runnable {
    final private InputStream inputStream;
    final private Consumer<String> consumeInputLine;

    public StreamGobbler(InputStream inputStream, Consumer<String> consumeInputLine) {
      this.inputStream = inputStream;
      this.consumeInputLine = consumeInputLine;
    }

    public void run() {
      new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(consumeInputLine);
    }
  }
}