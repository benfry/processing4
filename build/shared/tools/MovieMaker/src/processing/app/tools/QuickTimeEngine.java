package processing.app.tools;

import processing.app.Language;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.WritableRaster;
import java.io.*;
import java.util.Arrays;

import ch.randelshofer.media.mp3.MP3AudioInputStream;
import ch.randelshofer.media.quicktime.QuickTimeWriter;


/**
 * Code that's specific to the original QuickTime writer, which no longer
 * works on macOS 10.15 and later, because Apple has removed support for
 * most compression types (Animation, etc) that are not MPEG.
 * <p>
 * Originally hacked from Werner Randelshofer's QuickTimeWriter demo.
 * That source code can be found <a href="http://www.randelshofer.ch/blog/2010/10/writing-quicktime-movies-in-pure-java/">here</a>.
 * <p>
 * A more up-to-date version of the project is
 * <a href="http://www.randelshofer.ch/monte/">here</a>.
 * Problem is, it's too big, so we don't want to merge it into our code.
 * <p>
 * Broken out as a separate project because the license (CC) probably isn't
 * compatible with the rest of Processing and we don't want any confusion.
 * <p>
 * Added JAI ImageIO to support lots of other image file formats [131008].
 * Also copied the Processing TGA implementation.
 * <p>
 * Added support for the gamma ('gama') atom [131008].
 * <p>
 * A few more notes on the implementation:
 * <ul>
 * <li> The dialog box is super ugly. It's a hacked up version of the previous
 *      interface, but I'm too scared to pull that GUI layout code apart.
 * <li> The 'None' compressor seems to have bugs, so just disabled it instead.
 * <li> The 'pass through' option seems to be broken, so it's been removed.
 *      In its place is an option to use the same width/height as the originals.
 * <li> When this new 'pass through' is set, there's some nastiness with how
 *      the 'final' width/height variables are passed to the movie maker.
 *      This is an easy fix but needs a couple minutes.
 * </ul>
 * Ben Fry 2011-09-06, updated 2013-10-09, and again on 2021-06-27
 */
class QuickTimeEngine {
  Component parent;


  QuickTimeEngine(Component parent) {
    this.parent = parent;
  }


  String[] getFormats() {
    return new String[] {
      "Animation", "JPEG", "PNG"
    };
  }


  void write(File movieFile, File[] imgFiles, File soundFile,
             int width, int height, double fps, String formatName) throws IOException {
    QuickTimeWriter.VideoFormat videoFormat;
    switch (formatName) {
      case "JPEG":
        videoFormat = QuickTimeWriter.VideoFormat.JPG;
        break;
      case "PNG":
        videoFormat = QuickTimeWriter.VideoFormat.PNG;
        break;
      case "Animation":
      default:
        videoFormat = QuickTimeWriter.VideoFormat.RLE;
        break;
    }

    // preserving size, check the images for their size
    if (width == 0 || height == 0) {
      Dimension d = findSize(imgFiles);
      if (d == null) {
        // No images at all? No video then.
        throw new RuntimeException(Language.text("movie_maker.error.no_images_found"));
      }
      width = d.width;
      height = d.height;
    }

    if (soundFile != null) {
      writeVideoAndAudio(movieFile, imgFiles, soundFile, width, height, fps, videoFormat);
    } else {
      writeVideoOnlyVFR(movieFile, imgFiles, width, height, fps, videoFormat);
    }
  }


  /**
   * Read an image from a file. ImageIcon doesn't don't do well with some
   * file types, so we use ImageIO. ImageIO doesn't handle TGA files
   * created by Processing, so this calls our own loadImageTGA().
   * <br> Prints errors itself.
   * @return null on error; image only if okay.
   */
  private BufferedImage readImage(File file) {
    try {
      Thread current = Thread.currentThread();
      ClassLoader origLoader = Thread.currentThread().getContextClassLoader();
      current.setContextClassLoader(getClass().getClassLoader());

      BufferedImage image;
      try {
        image = ImageIO.read(file);
      } catch (IOException e) {
        System.err.println(Language.interpolate("movie_maker.error.cannot_read",
          file.getAbsolutePath()));
        return null;
      }

      current.setContextClassLoader(origLoader);

      /*
      String[] loadImageFormats = ImageIO.getReaderFormatNames();
      if (loadImageFormats != null) {
        for (String format : loadImageFormats) {
          System.out.println(format);
        }
      }
      */

      if (image == null) {
        String path = file.getAbsolutePath();
        String pathLower = path.toLowerCase();
        // Might be an incompatible TGA or TIFF created by Processing
        if (pathLower.endsWith(".tga")) {
          try {
            return loadImageTGA(file);
          } catch (IOException e) {
            cannotRead(file);
            return null;
          }

        } else if (pathLower.endsWith(".tif") || pathLower.endsWith(".tiff")) {
          cannotRead(file);
          System.err.println(Language.text("movie_maker.error.avoid_tiff"));
          return null;

        } else {
          cannotRead(file);
          return null;
        }

      } else {
        if (image.getWidth() <= 0 || image.getHeight() <= 0) {
          System.err.println(Language.interpolate("movie_maker.error.cannot_read_maybe_bad", file.getAbsolutePath()));
          return null;
        }
      }
      return image;

      // Catch-all is sometimes needed.
    } catch (RuntimeException e) {
      cannotRead(file);
      return null;
    }
  }


  private Dimension findSize(File[] imgFiles) {
    for (int i = 0; i < imgFiles.length; i++) {
      BufferedImage temp = readImage(imgFiles[i]);
      if (temp != null) {
        return new Dimension(temp.getWidth(), temp.getHeight());
      } else {
        // Nullify bad Files so we don't get errors twice.
        imgFiles[i] = null;
      }
    }
    return null;
  }


  static private void cannotRead(File file) {
    String path = file.getAbsolutePath();
    String msg = Language.interpolate("movie_maker.error.cannot_read", path);
    System.err.println(msg);
  }


  /** variable frame rate. */
  private void writeVideoOnlyVFR(File movieFile, File[] imgFiles, int width, int height, double fps, QuickTimeWriter.VideoFormat videoFormat) throws IOException {
    File tmpFile = new File(movieFile.getPath() + ".tmp");
    ProgressMonitor p = new ProgressMonitor(parent,
      Language.interpolate("movie_maker.progress.creating_file_name", movieFile.getName()),
      Language.text("movie_maker.progress.creating_output_file"),
      0, imgFiles.length);
    Graphics2D g = null;
    BufferedImage img = null;
    BufferedImage prevImg;
    int[] data;
    int[] prevData;
    QuickTimeWriter qtOut = null;
    try {
      int timeScale = (int) (fps * 100.0);
      int duration = 100;

      qtOut = new QuickTimeWriter(videoFormat == QuickTimeWriter.VideoFormat.RAW ? movieFile : tmpFile);
      qtOut.addVideoTrack(videoFormat, timeScale, width, height);
      qtOut.setSyncInterval(0, 30);

      //if (!passThrough) {
      img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      data = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
      prevImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      prevData = ((DataBufferInt) prevImg.getRaster().getDataBuffer()).getData();
      g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      int prevImgDuration = 0;
      for (int i = 0; i < imgFiles.length && !p.isCanceled(); i++) {
        File f = imgFiles[i];
        if (f == null) continue;

        p.setNote(Language.interpolate("movie_maker.progress.processing", f.getName()));
        p.setProgress(i);

        //BufferedImage fImg = ImageIO.read(f);
        BufferedImage fImg = readImage(f);
        if (fImg == null) continue;

        g.drawImage(fImg, 0, 0, width, height, null);
        if (i != 0 && Arrays.equals(data, prevData)) {
          prevImgDuration += duration;
        } else {
          if (prevImgDuration != 0) {
            qtOut.writeFrame(0, prevImg, prevImgDuration);
          }
          prevImgDuration = duration;
          System.arraycopy(data, 0, prevData, 0, data.length);
        }
      }
      if (prevImgDuration != 0) {
        qtOut.writeFrame(0, prevImg, prevImgDuration);
      }
      qtOut.toWebOptimizedMovie(movieFile, true);
      tmpFile.delete();
      qtOut.close();
      qtOut = null;
    } finally {
      p.close();
      if (g != null) {
        g.dispose();
      }
      if (img != null) {
        img.flush();
      }
      if (qtOut != null) {
        qtOut.close();
      }
    }
  }


  private void writeVideoAndAudio(File movieFile, File[] imgFiles, File audioFile,
                                  int width, int height, double fps, QuickTimeWriter.VideoFormat videoFormat) throws IOException {

    File tmpFile = new File(movieFile.getPath() + ".tmp");
    ProgressMonitor p = new ProgressMonitor(parent,
      Language.interpolate("movie_maker.progress.creating_file_name", movieFile.getName()),
      Language.text("movie_maker.progress.creating_output_file"),
      0, imgFiles.length);
    AudioInputStream audioIn = null;
    QuickTimeWriter qtOut = null;
    BufferedImage imgBuffer = null;
    Graphics2D g = null;

    try {
      // Determine audio format
      if (audioFile.getName().toLowerCase().endsWith(".mp3")) {
        audioIn = new MP3AudioInputStream(audioFile);
      } else {
        audioIn = AudioSystem.getAudioInputStream(audioFile);
      }
      AudioFormat audioFormat = audioIn.getFormat();
      boolean isVBR = audioFormat.getProperty("vbr") != null && (Boolean) audioFormat.getProperty("vbr");

      // Determine duration of a single sample
      int asDuration = (int) (audioFormat.getSampleRate() / audioFormat.getFrameRate());
      int vsDuration = 100;
      // Create writer
      qtOut = new QuickTimeWriter(videoFormat == QuickTimeWriter.VideoFormat.RAW ? movieFile : tmpFile);
      qtOut.addAudioTrack(audioFormat); // audio in track 0
      qtOut.addVideoTrack(videoFormat, (int) (fps * vsDuration), width, height);  // video in track 1

      // Create audio buffer
      int asSize;
      byte[] audioBuffer;
      if (isVBR) {
        // => variable bit rate: create audio buffer for a single frame
        asSize = audioFormat.getFrameSize();
        audioBuffer = new byte[asSize];
      } else {
        // => fixed bit rate: create audio buffer for half a second
        asSize = audioFormat.getChannels() * audioFormat.getSampleSizeInBits() / 8;
        audioBuffer = new byte[(int) (qtOut.getMediaTimeScale(0) / 2 * asSize)];
      }

      // Create video buffer
      imgBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      g = imgBuffer.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      int movieTime = 0;
      int imgIndex = 0;
      boolean isAudioDone = false;
      while ((imgIndex < imgFiles.length || !isAudioDone) && !p.isCanceled()) {
        // Advance movie time by half a second (we interleave twice per second)
        movieTime += qtOut.getMovieTimeScale() / 2;

        // Advance audio to movie time + 1 second (audio must be ahead of video by 1 second)
        while (!isAudioDone && qtOut.getTrackDuration(0) < movieTime + qtOut.getMovieTimeScale()) {
          int len = audioIn.read(audioBuffer);
          if (len == -1) {
            isAudioDone = true;
          } else {
            qtOut.writeSamples(0, len / asSize, audioBuffer, 0, len, asDuration);
          }
          if (isVBR) {
            // => variable bit rate: format can change at any time
            audioFormat = audioIn.getFormat();
            if (audioFormat == null) {
              break;
            }
            asSize = audioFormat.getFrameSize();
            asDuration = (int) (audioFormat.getSampleRate() / audioFormat.getFrameRate());
            if (audioBuffer.length < asSize) {
              audioBuffer = new byte[asSize];
            }
          }
        }

        // Advance video to movie time
        for (; imgIndex < imgFiles.length && qtOut.getTrackDuration(1) < movieTime; ++imgIndex) {
          // catch up with video time
          p.setProgress(imgIndex);
          File f = imgFiles[imgIndex];
          if (f == null) continue;

          p.setNote(Language.interpolate("movie_maker.progress.processing", f.getName()));
          BufferedImage fImg = readImage(f);
          if (fImg == null) continue;

          g.drawImage(fImg, 0, 0, width, height, null);
          fImg.flush();
          qtOut.writeFrame(1, imgBuffer, vsDuration);
        }
      }
//      if (streaming.equals("fastStart")) {
//        qtOut.toWebOptimizedMovie(movieFile, false);
//        tmpFile.delete();
//      } else if (streaming.equals("fastStartCompressed")) {
      qtOut.toWebOptimizedMovie(movieFile, true);
      tmpFile.delete();
//      }
      qtOut.close();
      qtOut = null;
    } catch (UnsupportedAudioFileException e) {
      throw new IOException(e.getMessage(), e);
    } finally {
      p.close();
      if (qtOut != null) {
        qtOut.close();
      }
      if (audioIn != null) {
        audioIn.close();
      }
      if (g != null) {
        g.dispose();
      }
      if (imgBuffer != null) {
        imgBuffer.flush();
      }
    }
  }


  /**
   * Targa image loader for RLE-compressed TGA files.
   * Code taken from PApplet, any changes here should lead to updates there.
   */
  static private BufferedImage loadImageTGA(File file) throws IOException {

    try (InputStream is = new FileInputStream(file)) {
      byte[] header = new byte[18];
      int offset = 0;
      do {
        int count = is.read(header, offset, header.length - offset);
        if (count == -1) return null;
        offset += count;
      } while (offset < 18);

    /*
      header[2] image type code
      2  (0x02) - Uncompressed, RGB images.
      3  (0x03) - Uncompressed, black and white images.
      10 (0x0A) - Run-length encoded RGB images.
      11 (0x0B) - Compressed, black and white images. (grayscale?)

      header[16] is the bit depth (8, 24, 32)

      header[17] image descriptor (packed bits)
      0x20 is 32 = origin upper-left
      0x28 is 32 + 8 = origin upper-left + 32 bits

        7  6  5  4  3  2  1  0
      128 64 32 16  8  4  2  1
    */

      int format = 0;
      final int RGB = 1;
      final int ARGB = 2;
      final int ALPHA = 4;

      if (((header[2] == 3) || (header[2] == 11)) &&  // B&W, plus RLE or not
        (header[16] == 8) &&  // 8 bits
        ((header[17] == 0x8) || (header[17] == 0x28))) {  // origin, 32 bit
        format = ALPHA;

      } else if (((header[2] == 2) || (header[2] == 10)) &&  // RGB, RLE or not
        (header[16] == 24) &&  // 24 bits
        ((header[17] == 0x20) || (header[17] == 0))) {  // origin
        format = RGB;

      } else if (((header[2] == 2) || (header[2] == 10)) &&
        (header[16] == 32) &&
        ((header[17] == 0x8) || (header[17] == 0x28))) {  // origin, 32
        format = ARGB;
      }

      if (format == 0) {
        throw new IOException(Language.interpolate("movie_maker.error.unknown_tga_format", file.getName()));
      }

      int w = ((header[13] & 0xff) << 8) + (header[12] & 0xff);
      int h = ((header[15] & 0xff) << 8) + (header[14] & 0xff);
      //PImage outgoing = createImage(w, h, format);
      int[] pixels = new int[w * h];

      // where "reversed" means upper-left corner (normal for most of
      // the modernized world, but "reversed" for the tga spec)
      //boolean reversed = (header[17] & 0x20) != 0;
      // https://github.com/processing/processing/issues/1682
      boolean reversed = (header[17] & 0x20) == 0;

      if ((header[2] == 2) || (header[2] == 3)) {  // not RLE encoded
        if (reversed) {
          int index = (h - 1) * w;
          switch (format) {
            case ALPHA:
              for (int y = h - 1; y >= 0; y--) {
                for (int x = 0; x < w; x++) {
                  pixels[index + x] = is.read();
                }
                index -= w;
              }
              break;
            case RGB:
              for (int y = h - 1; y >= 0; y--) {
                for (int x = 0; x < w; x++) {
                  pixels[index + x] =
                    is.read() | (is.read() << 8) | (is.read() << 16) |
                      0xff000000;
                }
                index -= w;
              }
              break;
            case ARGB:
              for (int y = h - 1; y >= 0; y--) {
                for (int x = 0; x < w; x++) {
                  pixels[index + x] =
                    is.read() | (is.read() << 8) | (is.read() << 16) |
                      (is.read() << 24);
                }
                index -= w;
              }
          }
        } else {  // not reversed
          int count = w * h;
          switch (format) {
            case ALPHA:
              for (int i = 0; i < count; i++) {
                pixels[i] = is.read();
              }
              break;
            case RGB:
              for (int i = 0; i < count; i++) {
                pixels[i] =
                  is.read() | (is.read() << 8) | (is.read() << 16) |
                    0xff000000;
              }
              break;
            case ARGB:
              for (int i = 0; i < count; i++) {
                pixels[i] =
                  is.read() | (is.read() << 8) | (is.read() << 16) |
                    (is.read() << 24);
              }
              break;
          }
        }

      } else {  // header[2] is 10 or 11
        int index = 0;

        while (index < pixels.length) {
          int num = is.read();
          boolean isRLE = (num & 0x80) != 0;
          if (isRLE) {
            num -= 127;  // (num & 0x7F) + 1
            int pixel = 0;
            switch (format) {
              case ALPHA:
                pixel = is.read();
                break;
              case RGB:
                pixel = 0xFF000000 |
                  is.read() | (is.read() << 8) | (is.read() << 16);
                break;
              case ARGB:
                pixel = is.read() |
                  (is.read() << 8) | (is.read() << 16) | (is.read() << 24);
                break;
            }
            for (int i = 0; i < num; i++) {
              pixels[index++] = pixel;
              if (index == pixels.length) break;
            }
          } else {  // write up to 127 bytes as uncompressed
            num += 1;
            switch (format) {
              case ALPHA:
                for (int i = 0; i < num; i++) {
                  pixels[index++] = is.read();
                }
                break;
              case RGB:
                for (int i = 0; i < num; i++) {
                  pixels[index++] = 0xFF000000 |
                    is.read() | (is.read() << 8) | (is.read() << 16);
                }
                break;
              case ARGB:
                for (int i = 0; i < num; i++) {
                  pixels[index++] = is.read() |
                    (is.read() << 8) | (is.read() << 16) | (is.read() << 24);
                }
                break;
            }
          }
        }

        if (!reversed) {
          int[] temp = new int[w];
          for (int y = 0; y < h / 2; y++) {
            int z = (h - 1) - y;
            System.arraycopy(pixels, y * w, temp, 0, w);
            System.arraycopy(pixels, z * w, pixels, y * w, w);
            System.arraycopy(temp, 0, pixels, z * w, w);
          }
        }
      }
      //is.close();
      int type = (format == RGB) ?
        BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
      BufferedImage image = new BufferedImage(w, h, type);
      WritableRaster wr = image.getRaster();
      wr.setDataElements(0, 0, w, h, pixels);
      return image;

    }
  }
}