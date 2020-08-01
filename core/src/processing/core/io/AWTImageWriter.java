package processing.core.io;

import processing.core.PApplet;
import processing.core.PImage;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.metadata.IIOMetadata;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static processing.core.PConstants.ARGB;

public class AWTImageWriter implements ImageWriter {
  private String[] saveImageFormats;

  @Override
  public boolean save(String filename, PImage image) {
    try {
      if (canHandleFormat(filename))
        if (saveImageIO(filename, image)) {
          return true;
        }

      System.err.println("Error while saving image.");
    } catch (IOException e) {
      e.printStackTrace();
    }
    return false;
  }

  private boolean canHandleFormat(String filename){
    return Arrays.stream(getSaveImageFormats())
      .anyMatch(ext -> filename.endsWith("." + ext));
  }

  private String[] getSaveImageFormats() {
    if(saveImageFormats == null)
      saveImageFormats = javax.imageio.ImageIO.getWriterFormatNames();

    return saveImageFormats;
  }

  /**
   * Use ImageIO functions from Java 1.4 and later to handle image save.
   * Various formats are supported, typically jpeg, png, bmp, and wbmp.
   * To get a list of the supported formats for writing, use: <BR>
   * <TT>println(javax.imageio.ImageIO.getReaderFormatNames())</TT>
   */
  private boolean saveImageIO(String path, PImage image) throws IOException {
    try {
      String extension =
        path.substring(path.lastIndexOf('.') + 1).toLowerCase();

      int outputFormat = formatSupportsAlpha(extension)
        ? getOutputFormat(image) : BufferedImage.TYPE_INT_RGB;

      File file = new File(path);

      javax.imageio.ImageWriter writer = null;
      ImageWriteParam param = null;
      IIOMetadata metadata = null;

      if (extension.equals("jpg") || extension.equals("jpeg")) {
        param = setupJpegImageWriter(writer = imageioWriter("jpeg"));
      }

      if (extension.equals("png")) {
        param = setupPngImageWriter(writer = imageioWriter("png"));
      }

      BufferedImage bimage = new BufferedImage(image.pixelWidth, image.pixelHeight, outputFormat);
      bimage.setRGB(0, 0, image.pixelWidth, image.pixelHeight, image.pixels, 0, image.pixelWidth);

      return writeImage(extension, file, writer, param, metadata, bimage);

    } catch (Exception e) {
      e.printStackTrace();
      throw new IOException("image save failed.");
    }
  }

  private boolean writeImage(String extension,
                             File file,
                             javax.imageio.ImageWriter writer,
                             ImageWriteParam param, IIOMetadata metadata,
                             BufferedImage bimage) throws IOException {
    if (writer != null) {
      var output = new BufferedOutputStream(PApplet.createOutput(file));
      writer.setOutput(ImageIO.createImageOutputStream(output));
      writer.write(metadata, new IIOImage(bimage, null, metadata), param);
      writer.dispose();

      output.flush();
      output.close();
      return true;
    }
    // If iter.hasNext() somehow fails up top, it falls through to here
    return ImageIO.write(bimage, extension, file);
  }

  private int getOutputFormat(PImage image){
    return image.format == ARGB
      ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
  }

  private boolean formatSupportsAlpha(String extension){
    return !(extension.equals("bmp") ||
             extension.equals("jpg") ||
             extension.equals("jpeg"));
  }

  private javax.imageio.ImageWriter imageioWriter(String extension) {
    var iter = ImageIO.getImageWritersByFormatName(extension);
    if (iter.hasNext()) {
      return iter.next();
    }
    return null;
  }

  private ImageWriteParam setupJpegImageWriter(
    javax.imageio.ImageWriter writer){
    if(writer != null) {
      // Set JPEG quality to 90% with baseline optimization. Setting this
      // to 1 was a huge jump (about triple the size), so this seems good.
      // Oddly, a smaller file size than Photoshop at 90%, but I suppose
      // it's a completely different algorithm.
      var param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(0.9f);
      return param;
    }

    return null;
  }

  private ImageWriteParam setupPngImageWriter(
    javax.imageio.ImageWriter writer
  ){
    return writer == null ? null : writer.getDefaultWriteParam();
  }
}
