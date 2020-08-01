package processing.core.io;

import processing.core.PImage;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class TiffImageWriter implements ImageWriter {

  private static final byte[] TIFF_HEADER = {
    77, 77, 0, 42, 0, 0, 0, 8, 0, 9, 0, -2, 0, 4, 0, 0, 0, 1, 0, 0,
    0, 0, 1, 0, 0, 3, 0, 0, 0, 1, 0, 0, 0, 0, 1, 1, 0, 3, 0, 0, 0, 1,
    0, 0, 0, 0, 1, 2, 0, 3, 0, 0, 0, 3, 0, 0, 0, 122, 1, 6, 0, 3, 0,
    0, 0, 1, 0, 2, 0, 0, 1, 17, 0, 4, 0, 0, 0, 1, 0, 0, 3, 0, 1, 21,
    0, 3, 0, 0, 0, 1, 0, 3, 0, 0, 1, 22, 0, 3, 0, 0, 0, 1, 0, 0, 0, 0,
    1, 23, 0, 4, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8, 0, 8, 0, 8
  };

  private static final String TIFF_ERROR = "Error: Processing can only read its own TIFF files.";

  @Override
  public boolean save(String filename, PImage image) {
    try (var os = new BufferedOutputStream(new FileOutputStream(filename),
                                           32768);) {
      return saveTiff(os, image);
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
  }

  private boolean saveTiff(OutputStream output, PImage image) throws IOException {
    byte[] tiff = new byte[768];
    System.arraycopy(TIFF_HEADER, 0, tiff, 0, TIFF_HEADER.length);

    tiff[30] = (byte) ((image.pixelWidth >> 8) & 0xff);
    tiff[31] = (byte) ((image.pixelWidth) & 0xff);
    tiff[42] = tiff[102] = (byte) ((image.pixelHeight >> 8) & 0xff);
    tiff[43] = tiff[103] = (byte) ((image.pixelHeight) & 0xff);

    int count = image.pixelWidth * image.pixelHeight * 3;
    tiff[114] = (byte) ((count >> 24) & 0xff);
    tiff[115] = (byte) ((count >> 16) & 0xff);
    tiff[116] = (byte) ((count >> 8) & 0xff);
    tiff[117] = (byte) ((count) & 0xff);

    // spew the header to the disk
    output.write(tiff);

    for (int i = 0; i < image.pixels.length; i++) {
      output.write((image.pixels[i] >> 16) & 0xff);
      output.write((image.pixels[i] >> 8) & 0xff);
      output.write(image.pixels[i] & 0xff);
    }
    output.flush();
    return true;
  }
}
