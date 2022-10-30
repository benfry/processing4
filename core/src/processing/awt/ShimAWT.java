package processing.awt;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.awt.geom.AffineTransform;
import java.util.Map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.UIManager;

// used by desktopFile() method
import javax.swing.filechooser.FileSystemView;

import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PImage;


/**
 * This class exists as an abstraction layer to remove AWT from PApplet.
 * It is a staging area for AWT-specific code that's shared by the Java2D,
 * JavaFX, and JOGL renderers. Once PSurfaceFX and PSurfaceJOGL have
 * their own implementations, these methods will move to PSurfaceAWT.
 */
public class ShimAWT implements PConstants {
  /*
  PGraphics graphics;
  PApplet sketch;


  public ShimAWT(PApplet sketch) {
    this.graphics = graphics;
    this.sketch = sketch;
  }
  */
  static private ShimAWT instance;

  final private GraphicsDevice[] displayDevices;

  final private int displayWidth;
  final private int displayHeight;


  /** Only needed for display functions */
  static private ShimAWT getInstance() {
    if (instance == null) {
      instance = new ShimAWT();
    }
    return instance;
  }


  private ShimAWT() {
    // Need the list of display devices to be queried already for usage below.
    // https://github.com/processing/processing/issues/3295
    // https://github.com/processing/processing/issues/3296
    // Not doing this from a static initializer because it may cause
    // PApplet to cache and the values to stick through subsequent runs.
    // Instead make it a runtime thing and a local variable.
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice device = ge.getDefaultScreenDevice();
    displayDevices = ge.getScreenDevices();

//    // Default or unparsed will be -1, spanning will be 0, actual displays will
//    // be numbered from 1 because it's too weird to say "display 0" in prefs.
//    if (display > 0 && display <= displayDevices.length) {
//      device = displayDevices[display-1];
//    }
    // When this was called, display will always be unset (even in 3.x),
    // since this happens before settings() is called.

    // Set displayWidth and displayHeight for people still using those.
    DisplayMode displayMode = device.getDisplayMode();
    displayWidth = displayMode.getWidth();
    displayHeight = displayMode.getHeight();
  }


  static public int getDisplayWidth() {
    return getInstance().displayWidth;
  }


  static public int getDisplayHeight() {
    return getInstance().displayHeight;
  }


  static public int getDisplayCount() {
    return getInstance().displayDevices.length;
  }


  static public int getDisplayDensity(int num) {
    return getInstance().displayDensityImpl(num);
  }


  /*
  private int displayDensityImpl() {
    if (display != SPAN && (fullScreen || present)) {
      return displayDensity(display);
    }
    // walk through all displays, use 2 if any display is 2
    for (int i = 0; i < displayDevices.length; i++) {
      if (displayDensity(i+1) == 2) {
        return 2;
      }
    }
    // If nobody's density is 2 then everyone is 1
    return 1;
  }
  */


  private int displayDensityImpl(int display) {
    GraphicsConfiguration graphicsConfig = null;

    if (display == -1) {  // the default display
      GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      graphicsConfig = ge.getDefaultScreenDevice().getDefaultConfiguration();

    } else if (display == SPAN) {
      // walk through all displays, go with lowest common denominator
      for (int i = 0; i < displayDevices.length; i++) {
        if (displayDensityImpl(i) == 1) {
          return 1;
        }
      }
      return 2;  // everyone is density 2

    } else if (display <= displayDevices.length) {
      graphicsConfig = displayDevices[display - 1].getDefaultConfiguration();
    }

    if (graphicsConfig == null) {
      System.err.println("Display " + display + " does not exist, " +
        "returning 1 for displayDensity(" + display + ")");
      return 1;  // not the end of the world, so don't throw a RuntimeException

    } else {
      AffineTransform tx = graphicsConfig.getDefaultTransform();
      return (int) Math.round(tx.getScaleX());
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static public void fromNativeImage(Image img, PImage out) {
    out.format = RGB;
    out.pixels = null;

    if (img instanceof BufferedImage) {
      BufferedImage bi = (BufferedImage) img;
      out.width = bi.getWidth();
      out.height = bi.getHeight();

      int type = bi.getType();
      if (type == BufferedImage.TYPE_3BYTE_BGR ||
        type == BufferedImage.TYPE_4BYTE_ABGR) {
        out.pixels = new int[out.width * out.height];
        bi.getRGB(0, 0, out.width, out.height, out.pixels, 0, out.width);
        if (type == BufferedImage.TYPE_4BYTE_ABGR) {
          out.format = ARGB;
//        } else {
//          opaque();
        }
      } else {
        DataBuffer db = bi.getRaster().getDataBuffer();
        if (db instanceof DataBufferInt) {
          out.pixels = ((DataBufferInt) db).getData();
          if (type == BufferedImage.TYPE_INT_ARGB) {
            out.format = ARGB;
//          } else if (type == BufferedImage.TYPE_INT_RGB) {
//            opaque();
          }
        }
      }
    }
    if ((out.pixels != null) && (out.format == RGB)) {
      for (int i = 0; i < out.pixels.length; i++) {
        out.pixels[i] |= 0xFF000000;
      }
    }
    // Implements fall-through if not DataBufferInt above, or not a
    // known type, or not DataBufferInt for the data itself.
    if (out.pixels == null) {  // go the old school Java 1.0 route
      out.width = img.getWidth(null);
      out.height = img.getHeight(null);
      out.pixels = new int[out.width * out.height];
      PixelGrabber pg =
        new PixelGrabber(img, 0, 0, out.width, out.height, out.pixels, 0, out.width);
      try {
        pg.grabPixels();
      } catch (InterruptedException ignored) { }
    }
    out.pixelDensity = 1;
    out.pixelWidth = out.width;
    out.pixelHeight = out.height;
  }


//  /** Set the high bits of all pixels to opaque. */
//  protected void opaque() {
//    for (int i = 0; i < pixels.length; i++) {
//      pixels[i] = 0xFF000000 | pixels[i];
//    }
//  }


  static public Object getNativeImage(PImage img) {
    img.loadPixels();
    int type = (img.format == RGB) ?
      BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
    BufferedImage image =
      new BufferedImage(img.pixelWidth, img.pixelHeight, type);
    WritableRaster wr = image.getRaster();
    wr.setDataElements(0, 0, img.pixelWidth, img.pixelHeight, img.pixels);
    return image;
  }


  static public void resizeImage(PImage img, int w, int h) {  // ignore
    if (w <= 0 && h <= 0) {
      throw new IllegalArgumentException("width or height must be > 0 for resize");
    }

    if (w == 0) {  // Use height to determine relative size
      float diff = (float) h / (float) img.height;
      w = (int) (img.width * diff);
    } else if (h == 0) {  // Use the width to determine relative size
      float diff = (float) w / (float) img.width;
      h = (int) (img.height * diff);
    }

    BufferedImage bimg =
      shrinkImage((BufferedImage) img.getNative(), w*img.pixelDensity, h*img.pixelDensity);

    PImage temp = new PImageAWT(bimg);
    img.pixelWidth = temp.width;
    img.pixelHeight = temp.height;

    // Get the resized pixel array
    img.pixels = temp.pixels;

    img.width = img.pixelWidth / img.pixelDensity;
    img.height = img.pixelHeight / img.pixelDensity;

    // Mark the pixels array as altered
    img.updatePixels();
  }


  // Adapted from getFasterScaledInstance() method from page 111 of
  // "Filthy Rich Clients" by Chet Haase and Romain Guy
  // Additional modifications and simplifications have been added,
  // plus a fix to deal with an infinite loop if images are expanded.
  // https://github.com/processing/processing/issues/1501
  static private BufferedImage shrinkImage(BufferedImage img,
                                           int targetWidth, int targetHeight) {
    int type = (img.getTransparency() == Transparency.OPAQUE) ?
      BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
    BufferedImage outgoing = img;
    BufferedImage scratchImage = null;
    Graphics2D g2 = null;
    int prevW = outgoing.getWidth();
    int prevH = outgoing.getHeight();
    boolean isTranslucent = img.getTransparency() != Transparency.OPAQUE;

    // Use multi-step technique: start with original size, then scale down in
    // multiple passes with drawImage() until the target size is reached
    int w = img.getWidth();
    int h = img.getHeight();

    do {
      if (w > targetWidth) {
        w /= 2;
        // if this is the last step, do the exact size
        if (w < targetWidth) {
          w = targetWidth;
        }
      } else {  //if (targetWidth >= w) {
        w = targetWidth;
      }
      if (h > targetHeight) {
        h /= 2;
        if (h < targetHeight) {
          h = targetHeight;
        }
      } else {  //if (targetHeight >= h) {
        h = targetHeight;
      }
      if (scratchImage == null || isTranslucent) {
        // Use a single scratch buffer for all iterations and then copy
        // to the final, correctly-sized image before returning
        scratchImage = new BufferedImage(w, h, type);
        g2 = scratchImage.createGraphics();
      }
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2.drawImage(outgoing, 0, 0, w, h, 0, 0, prevW, prevH, null);
      prevW = w;
      prevH = h;
      outgoing = scratchImage;
    } while (w != targetWidth || h != targetHeight);

    //if (g2 != null) {
    g2.dispose();
    //}

    // If we used a scratch buffer that is larger than our target size,
    // create an image of the right size and copy the results into it
    if (targetWidth != outgoing.getWidth() ||
      targetHeight != outgoing.getHeight()) {
      scratchImage = new BufferedImage(targetWidth, targetHeight, type);
      g2 = scratchImage.createGraphics();
      g2.drawImage(outgoing, 0, 0, null);
      g2.dispose();
      outgoing = scratchImage;
    }
    return outgoing;
  }


  static protected String[] loadImageExtensions;  // list of ImageIO formats


  static public PImage loadImage(PApplet sketch, String filename, Object... args) {
    String extension = null;
    if (args != null && args.length > 0) {
      // the only one that's supported for now
      extension = (String) args[0];
    }

    if (extension == null) {
      String lower = filename.toLowerCase();
      int dot = filename.lastIndexOf('.');
      if (dot == -1) {
        extension = "unknown";  // no extension found

      } else {
        extension = lower.substring(dot + 1);

        // check for, and strip any parameters on the url, i.e.
        // filename.jpg?blah=blah&something=that
        int question = extension.indexOf('?');
        if (question != -1) {
          extension = extension.substring(0, question);
        }
      }
    }

    // just in case. them users will try anything!
    extension = extension.toLowerCase();

    if (extension.equals("tga")) {
      try {
        InputStream input = sketch.createInput(filename);
        if (input == null) return null;

        PImage image = PImage.loadTGA(input);
        if (image != null) {
          image.parent = sketch;
        }
        return image;

      } catch (IOException e) {
        e.printStackTrace();
        return null;
      }
    }

    // Disabling for 4.0 beta 5, we're now using ImageIO for TIFF
    /*
    if (extension.equals("tif") || extension.equals("tiff")) {
      InputStream input = sketch.createInput(filename);
      PImage image =  (input == null) ? null : PImage.loadTIFF(input);
      return image;
    }
    */

    // For jpeg, gif, and png, load them using createImage(),
    // because the javax.imageio code was found to be much slower.
    // https://download.processing.org/bugzilla/392.html
    try {
      if (extension.equals("jpg") || extension.equals("jpeg") ||
          extension.equals("gif") || extension.equals("png") ||
          extension.equals("unknown")) {
        byte[] bytes = sketch.loadBytes(filename);
        if (bytes == null) {
          return null;
        } else {
          //Image awtImage = Toolkit.getDefaultToolkit().createImage(bytes);
          Image awtImage = new ImageIcon(bytes).getImage();

          if (awtImage instanceof BufferedImage) {
            BufferedImage buffImage = (BufferedImage) awtImage;
            int space = buffImage.getColorModel().getColorSpace().getType();
            if (space == ColorSpace.TYPE_CMYK) {
              System.err.println(filename + " is a CMYK image, " +
                                 "only RGB images are supported.");
              return null;
              /*
              // wishful thinking, appears to not be supported
              // https://community.oracle.com/thread/1272045?start=0&tstart=0
              BufferedImage destImage =
                new BufferedImage(buffImage.getWidth(),
                                  buffImage.getHeight(),
                                  BufferedImage.TYPE_3BYTE_BGR);
              ColorConvertOp op = new ColorConvertOp(null);
              op.filter(buffImage, destImage);
              image = new PImage(destImage);
              */
            }
          }

          PImage image = new PImageAWT(awtImage);
          if (image.width == -1) {
            System.err.println("The file " + filename +
                               " contains bad image data, or may not be an image.");
          }

          // if it's a .gif image, test to see if it has transparency
          if (extension.equals("gif") || extension.equals("png") ||
              extension.equals("unknown")) {
            image.checkAlpha();
          }

          image.parent = sketch;
          return image;
        }
      }
    } catch (Exception e) {
      // show error, but move on to the stuff below, see if it'll work
      e.printStackTrace();
    }

    if (loadImageExtensions == null) {
      loadImageExtensions = ImageIO.getReaderFormatNames();
    }
    if (loadImageExtensions != null) {
      for (String loadImageExtension : loadImageExtensions) {
        if (extension.equals(loadImageExtension)) {
          return loadImageIO(sketch, filename);
        }
      }

      // failed, could not load image after all those attempts
      System.err.println("Could not load " + filename + ", " +
        "make sure it ends with a supported extension " +
        "(" + PApplet.join(loadImageExtensions, ", ") + ")");
    } else {
      System.err.println("Could not load " + filename);
    }
    return null;
  }


  /**
   * Use Java 1.4 ImageIO methods to load an image.
   */
  static protected PImage loadImageIO(PApplet sketch, String filename) {
    InputStream stream = sketch.createInput(filename);
    if (stream == null) {
      System.err.println("The image " + filename + " could not be found.");
      return null;
    }

    try {
      BufferedImage bi = ImageIO.read(stream);
      //PImage outgoing = new PImage(bi.getWidth(), bi.getHeight());
      PImage outgoing = new PImageAWT(bi);
      outgoing.parent = sketch;

      //bi.getRGB(0, 0, outgoing.width, outgoing.height,
      //          outgoing.pixels, 0, outgoing.width);

      // check the alpha for this image
      // was gonna call getType() on the image to see if RGB or ARGB,
      // but it's not actually useful, since gif images will come through
      // as TYPE_BYTE_INDEXED, which means it'll still have to check for
      // the transparency. also, would have to iterate through all the other
      // types and guess whether alpha was in there, so.. just gonna stick
      // with the old method.
      outgoing.checkAlpha();

      stream.close();
      // return the image
      return outgoing;

    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static public boolean saveImage(PImage image, String path, String... args) {
    if (saveImageExtensions == null) {
      saveImageExtensions = javax.imageio.ImageIO.getWriterFormatNames();
    }
    try {
      if (saveImageExtensions != null) {
        for (String saveImageFormat : saveImageExtensions) {
          if (path.endsWith("." + saveImageFormat)) {
            if (!saveImageIO(image, path, args)) {
              System.err.println("Error while saving image.");
              return false;
            }
            return true;
          }
        }
        System.err.println("Could not save " + path + ", " +
                           "make sure it ends with a supported extension " +
                           "(" + PApplet.join(saveImageExtensions, ", ") + ")");
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return false;
  }


  static protected String[] saveImageExtensions;


  /**
   * Use ImageIO functions from Java 1.4 and later to handle image save.
   * Various formats are supported, typically jpeg, png, bmp, and wbmp.
   * To get a list of the supported formats for writing, use: <BR>
   * <TT>println(javax.imageio.ImageIO.getReaderFormatNames())</TT>
   */
  static protected boolean saveImageIO(PImage image, String path, String... args) throws IOException {
    try {
      int outputFormat = (image.format == ARGB) ?
        BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;

      Map<String, Number> params = new HashMap<>();
      params.put("quality", 0.9f);  // default JPEG quality
      params.put("dpi", 100.0);  // default DPI for PNG

      if (args != null) {
        for (String arg : args) {
          if (arg.startsWith("quality=")) {
            params.put("quality", Float.parseFloat(arg.substring(8)));
          } else if (arg.startsWith("dpi=")) {
            params.put("dpi", Double.parseDouble(arg.substring(4)));
          }
        }
      }

      String extension =
        path.substring(path.lastIndexOf('.') + 1).toLowerCase();

      // JPEG and BMP images that have an alpha channel set get pretty unhappy.
      // BMP just doesn't write, and JPEG writes it as a CMYK image.
      // https://github.com/processing/processing/issues/454
      if (extension.equals("bmp") || extension.equals("jpg") || extension.equals("jpeg")) {
        outputFormat = BufferedImage.TYPE_INT_RGB;
      }

      BufferedImage bimage = new BufferedImage(image.pixelWidth, image.pixelHeight, outputFormat);
      bimage.setRGB(0, 0, image.pixelWidth, image.pixelHeight, image.pixels, 0, image.pixelWidth);

      File file = new File(path);

      ImageWriter writer = null;
      ImageWriteParam param = null;
      IIOMetadata metadata = null;

      if (extension.equals("jpg") || extension.equals("jpeg")) {
        if ((writer = imageioWriter("jpeg")) != null) {
          // Set JPEG quality to 90% with baseline optimization. Setting this
          // to 1 was a huge jump (about triple the size), so this seems good.
          // Oddly, a smaller file size than Photoshop at 90%, but I suppose
          // it's a completely different algorithm.
          param = writer.getDefaultWriteParam();
          param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
          //param.setCompressionQuality(0.9f);
          param.setCompressionQuality((Float) params.get("quality"));
        }
      }

      if (extension.equals("png")) {
        if ((writer = imageioWriter("png")) != null) {
          param = writer.getDefaultWriteParam();
          metadata = imageioDPI(writer, param, (Double) params.get("dpi"));
        }
      }

      if (writer != null) {
        OutputStream output = PApplet.createOutput(file);
        if (output == null) {
          return false;
        }
        writer.setOutput(ImageIO.createImageOutputStream(output));
        writer.write(metadata, new IIOImage(bimage, null, metadata), param);
        writer.dispose();

        output.flush();
        output.close();
        return true;
      }
      // If iter.hasNext() somehow fails up top, it falls through to here
      return javax.imageio.ImageIO.write(bimage, extension, file);

    } catch (Exception e) {
      e.printStackTrace();
      throw new IOException("image save failed.");
    }
  }


  static private ImageWriter imageioWriter(String extension) {
    Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName(extension);
    if (iter.hasNext()) {
      return iter.next();
    }
    return null;
  }


  @SuppressWarnings("SameParameterValue")
  static private IIOMetadata imageioDPI(ImageWriter writer, ImageWriteParam param, double dpi) {
    // http://stackoverflow.com/questions/321736/how-to-set-dpi-information-in-an-image
    ImageTypeSpecifier typeSpecifier =
      ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB);
    IIOMetadata metadata =
      writer.getDefaultImageMetadata(typeSpecifier, param);

    if (!metadata.isReadOnly() && metadata.isStandardMetadataFormatSupported()) {
      // for PNG, it's dots per millimeter
      double dotsPerMilli = dpi / 25.4;

      IIOMetadataNode horiz = new IIOMetadataNode("HorizontalPixelSize");
      horiz.setAttribute("value", Double.toString(dotsPerMilli));

      IIOMetadataNode vert = new IIOMetadataNode("VerticalPixelSize");
      vert.setAttribute("value", Double.toString(dotsPerMilli));

      IIOMetadataNode dim = new IIOMetadataNode("Dimension");
      dim.appendChild(horiz);
      dim.appendChild(vert);

      IIOMetadataNode root = new IIOMetadataNode("javax_imageio_1.0");
      root.appendChild(dim);

      try {
        metadata.mergeTree("javax_imageio_1.0", root);
        return metadata;

      } catch (IIOInvalidTreeException e) {
        System.err.println("Could not set the DPI of the output image");
        e.printStackTrace();
      }
    }
    return null;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static public void initRun() {
    // Supposed to help with flicker, but no effect on OS X.
    // TODO IIRC this helped on Windows, but need to double check.
    System.setProperty("sun.awt.noerasebackground", "true");

    // Remove 60fps limit on the JavaFX "pulse" timer
    System.setProperty("javafx.animation.fullspeed", "true");

    // Catch any HeadlessException to provide more useful feedback
    try {
      // Call validate() while resize events are in progress
      Toolkit.getDefaultToolkit().setDynamicLayout(true);
    } catch (HeadlessException e) {
      System.err.println("Cannot run sketch without a display. Read this for possible solutions:");
      System.err.println("https://github.com/processing/processing/wiki/Running-without-a-Display");
      System.exit(1);
    }
  }


  /*
  public int displayDensity() {
    if (sketch.display != PConstants.SPAN && (sketch.fullScreen || sketch.present)) {
      return displayDensity(sketch.display);
    }
    // walk through all displays, use 2 if any display is 2
    for (int i = 0; i < displayDevices.length; i++) {
      if (displayDensity(i+1) == 2) {
        return 2;
      }
    }
    // If nobody's density is 2 then everyone is 1
    return 1;
  }
  */


 /**
  * display - the display number to check
  * (1-indexed to match the Preferences dialog box)
  */
  /*
  public int displayDensity(int display) {
    if (display > 0 && display <= displayDevices.length) {
      GraphicsConfiguration graphicsConfig =
        displayDevices[display - 1].getDefaultConfiguration();
      AffineTransform tx = graphicsConfig.getDefaultTransform();
      return (int) Math.round(tx.getScaleX());
    }

    System.err.println("Display " + display + " does not exist, returning ");
    return 1;  // not the end of the world, so don't throw a RuntimeException
  }
  */


  static public void selectInput(String prompt, String callbackMethod,
                                 File file, Object callbackObject) {
    EventQueue.invokeLater(() -> selectImpl(prompt, callbackMethod, file,
      callbackObject, null, FileDialog.LOAD));
  }


  /*
  static public void selectOutput(String prompt, String callbackMethod,
                                  File file, Object callbackObject, Frame parent) {
    selectImpl(prompt, callbackMethod, file, callbackObject, parent, FileDialog.SAVE, null);
  }


  static public void selectOutput(String prompt, String callbackMethod,
                                  File file, Object callbackObject, Frame parent,
                                  PApplet sketch) {
    selectImpl(prompt, callbackMethod, file, callbackObject, parent, FileDialog.SAVE, sketch);
  }
  */


  static public void selectOutput(String prompt, String callbackMethod,
                                  File file, Object callbackObject) {
    EventQueue.invokeLater(() -> selectImpl(prompt, callbackMethod, file,
      callbackObject, null, FileDialog.SAVE));
  }


  /*
  // Will remove the 'sketch' parameter once we get an upstream JOGL fix
  // https://github.com/processing/processing/issues/3831
  static protected void selectEvent(final String prompt,
                                   final String callbackMethod,
                                   final File defaultSelection,
                                   final Object callbackObject,
                                   final Frame parentFrame,
                                   final int mode,
                                   final PApplet sketch) {
    EventQueue.invokeLater(new Runnable() {
      public void run() {
        boolean hide = (sketch != null) &&
          (sketch.g instanceof PGraphicsOpenGL) &&
          (PApplet.platform == PConstants.WINDOWS);
        if (hide) sketch.getSurface().setVisible(false);

        selectImpl(prompt, callbackMethod, defaultSelection, callbackObject,
                   parentFrame, mode, sketch);

        if (hide) sketch.getSurface().setVisible(true);
      }
    });
  }
  */


  static public void selectImpl(final String prompt,
                                final String callbackMethod,
                                final File defaultSelection,
                                final Object callbackObject,
                                final Frame parentFrame,
                                final int mode) {
    File selectedFile = null;

    if (PApplet.useNativeSelect) {
      FileDialog dialog = new FileDialog(parentFrame, prompt, mode);
      if (defaultSelection != null) {
        dialog.setDirectory(defaultSelection.getParent());
        dialog.setFile(defaultSelection.getName());
      }

      dialog.setVisible(true);
      String directory = dialog.getDirectory();
      String filename = dialog.getFile();
      if (filename != null) {
        selectedFile = new File(directory, filename);
      }

    } else {
      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle(prompt);
      if (defaultSelection != null) {
        chooser.setSelectedFile(defaultSelection);
      }

      int result = -1;
      if (mode == FileDialog.SAVE) {
        result = chooser.showSaveDialog(parentFrame);
      } else if (mode == FileDialog.LOAD) {
        result = chooser.showOpenDialog(parentFrame);
      }
      if (result == JFileChooser.APPROVE_OPTION) {
        selectedFile = chooser.getSelectedFile();
      }
    }
    PApplet.selectCallback(selectedFile, callbackMethod, callbackObject);
  }


  static public void selectFolder(final String prompt,
                                  final String callbackMethod,
                                  final File defaultSelection,
                                  final Object callbackObject) {
    EventQueue.invokeLater(() -> selectFolderImpl(prompt, callbackMethod,
      defaultSelection, callbackObject, null));
  }


  /*
  static public void selectFolder(final String prompt,
                                  final String callbackMethod,
                                  final File defaultSelection,
                                  final Object callbackObject,
                                  final Frame parentFrame) {
    selectFolderEvent(prompt, callbackMethod, defaultSelection, callbackObject, parentFrame, null);
  }


  // Will remove the 'sketch' parameter once we get an upstream JOGL fix
  // https://github.com/processing/processing/issues/3831
  static public void selectFolderEvent(final String prompt,
                                       final String callbackMethod,
                                       final File defaultSelection,
                                       final Object callbackObject,
                                       final Frame parentFrame,
                                       final PApplet sketch) {
    EventQueue.invokeLater(() -> {
      selectFolderImpl(prompt, callbackMethod, defaultSelection,
                       callbackObject, parentFrame, sketch);
    });
  }
  */


  static public void selectFolderImpl(final String prompt,
                                      final String callbackMethod,
                                      final File defaultSelection,
                                      final Object callbackObject,
                                      final Frame parentFrame) {
    File selectedFile = null;
    if (PApplet.platform == PConstants.MACOS && PApplet.useNativeSelect) {
      FileDialog fileDialog =
        new FileDialog(parentFrame, prompt, FileDialog.LOAD);
      if (defaultSelection != null) {
        fileDialog.setDirectory(defaultSelection.getAbsolutePath());
      }
      System.setProperty("apple.awt.fileDialogForDirectories", "true");
      fileDialog.setVisible(true);
      System.setProperty("apple.awt.fileDialogForDirectories", "false");
      String filename = fileDialog.getFile();
      if (filename != null) {
        selectedFile = new File(fileDialog.getDirectory(), fileDialog.getFile());
      }
    } else {
      checkLookAndFeel();
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setDialogTitle(prompt);
      fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      if (defaultSelection != null) {
        fileChooser.setCurrentDirectory(defaultSelection);
      }

      int result = fileChooser.showOpenDialog(parentFrame);
      if (result == JFileChooser.APPROVE_OPTION) {
        selectedFile = fileChooser.getSelectedFile();
      }
    }
    PApplet.selectCallback(selectedFile, callbackMethod, callbackObject);
  }


  static private boolean lookAndFeelCheck;

  /**
   * Initialize the Look & Feel if it hasn't been already.
   * Call this before using any Swing-related code in PApplet methods.
   */
  static private void checkLookAndFeel() {
    if (!lookAndFeelCheck) {
      if (PApplet.platform == PConstants.WINDOWS) {
        // Windows is defaulting to Metal or something else awful.
        // Which also is not scaled properly with HiDPI interfaces.
        try {
          UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { }
      }
      lookAndFeelCheck = true;
    }
  }


  // TODO maybe call this with reflection from inside PApplet?
  //      longer term, develop a more general method for other platforms
  static public File getWindowsDesktop() {
    return FileSystemView.getFileSystemView().getHomeDirectory();
  }


  static public boolean openLink(String url) {
    try {
      if (Desktop.isDesktopSupported()) {
        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.BROWSE)) {
          desktop.browse(new URI(url));
          return true;
        }
      }
    } catch (IOException | URISyntaxException e) {
      e.printStackTrace();
    }
    return false;
  }
}