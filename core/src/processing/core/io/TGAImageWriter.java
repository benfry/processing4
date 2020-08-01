package processing.core.io;

import processing.core.PImage;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static processing.core.PConstants.*;

public class TGAImageWriter implements ImageWriter {

  @Override
  public boolean save(String filename, PImage image) {
    try(var os = new BufferedOutputStream(new FileOutputStream(filename), 32768)){
    return saveTGA(os, image);
    }catch(IOException e){
      e.printStackTrace();
      return false;
    }
  }

  private boolean saveTGA(BufferedOutputStream output, PImage image)
    throws IOException {

    byte[] header = new byte[18];

    if (image.format == ALPHA) {  // save ALPHA images as 8bit grayscale
      header[2] = 0x0B;
      header[16] = 0x08;
      header[17] = 0x28;

    } else if (image.format == RGB) {
      header[2] = 0x0A;
      header[16] = 24;
      header[17] = 0x20;

    } else if (image.format == ARGB) {
      header[2] = 0x0A;
      header[16] = 32;
      header[17] = 0x28;

    } else {
      throw new RuntimeException("Image format not recognized inside save()");
    }
    // set image dimensions lo-hi byte order
    header[12] = (byte) (image.pixelWidth & 0xff);
    header[13] = (byte) (image.pixelWidth >> 8);
    header[14] = (byte) (image.pixelHeight & 0xff);
    header[15] = (byte) (image.pixelHeight >> 8);

      output.write(header);

      int maxLen = image.pixelHeight * image.pixelWidth;
      int index = 0;
      int col; //, prevCol;
      int[] currChunk = new int[128];

      // 8bit image exporter is in separate loop
      // to avoid excessive conditionals...
      if (image.format == ALPHA) {
        while (index < maxLen) {
          boolean isRLE = false;
          int rle = 1;
          currChunk[0] = col = image.pixels[index] & 0xff;
          while (index + rle < maxLen) {
            if (col != (image.pixels[index + rle]&0xff) || rle == 128) {
              isRLE = (rle > 1);
              break;
            }
            rle++;
          }
          if (isRLE) {
            output.write(0x80 | (rle - 1));
            output.write(col);

          } else {
            rle = 1;
            while (index + rle < maxLen) {
              int cscan = image.pixels[index + rle] & 0xff;
              if ((col != cscan && rle < 128) || rle < 3) {
                currChunk[rle] = col = cscan;
              } else {
                if (col == cscan) rle -= 2;
                break;
              }
              rle++;
            }
            output.write(rle - 1);
            for (int i = 0; i < rle; i++) output.write(currChunk[i]);
          }
          index += rle;
        }
      } else {  // export 24/32 bit TARGA
        while (index < maxLen) {
          boolean isRLE = false;
          currChunk[0] = col = image.pixels[index];
          int rle = 1;
          // try to find repeating bytes (min. len = 2 pixels)
          // maximum chunk size is 128 pixels
          while (index + rle < maxLen) {
            if (col != image.pixels[index + rle] || rle == 128) {
              isRLE = (rle > 1); // set flag for RLE chunk
              break;
            }
            rle++;
          }
          if (isRLE) {
            output.write(128 | (rle - 1));
            output.write(col & 0xff);
            output.write(col >> 8 & 0xff);
            output.write(col >> 16 & 0xff);
            if (image.format == ARGB) output.write(col >>> 24 & 0xff);

          } else {  // not RLE
            rle = 1;
            while (index + rle < maxLen) {
              if ((col != image.pixels[index + rle] && rle < 128) || rle < 3) {
                currChunk[rle] = col = image.pixels[index + rle];
              } else {
                // check if the exit condition was the start of
                // a repeating colour
                if (col == image.pixels[index + rle]) rle -= 2;
                break;
              }
              rle++;
            }
            // write uncompressed chunk
            output.write(rle - 1);
            if (image.format == ARGB) {
              for (int i = 0; i < rle; i++) {
                col = currChunk[i];
                output.write(col & 0xff);
                output.write(col >> 8 & 0xff);
                output.write(col >> 16 & 0xff);
                output.write(col >>> 24 & 0xff);
              }
            } else {
              for (int i = 0; i < rle; i++) {
                col = currChunk[i];
                output.write(col & 0xff);
                output.write(col >> 8 & 0xff);
                output.write(col >> 16 & 0xff);
              }
            }
          }
          index += rle;
        }
      }
      output.flush();
      return true;
  }
}
