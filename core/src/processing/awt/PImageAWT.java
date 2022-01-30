/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2015 The Processing Foundation

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License version 2.1 as published by the Free Software Foundation.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.awt;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.PixelGrabber;
import java.awt.image.WritableRaster;

import processing.core.PImage;


public class PImageAWT extends PImage {

  /**
   * Construct a new PImage from a java.awt.Image. This constructor assumes
   * that you've done the work of making sure a MediaTracker has been used
   * to fully download the data and that the img is valid.
   *
   * @nowebref
   * @param img assumes a MediaTracker has been used to fully download
   * the data and the img is valid
   */
  public PImageAWT(Image img) {
    format = RGB;
    if (img instanceof BufferedImage) {
      BufferedImage bi = (BufferedImage) img;
      width = bi.getWidth();
      height = bi.getHeight();
      int type = bi.getType();
      if (type == BufferedImage.TYPE_3BYTE_BGR ||
          type == BufferedImage.TYPE_4BYTE_ABGR) {
        pixels = new int[width * height];
        bi.getRGB(0, 0, width, height, pixels, 0, width);
        if (type == BufferedImage.TYPE_4BYTE_ABGR) {
          format = ARGB;
        } else {
          opaque();
        }
      } else {
        DataBuffer db = bi.getRaster().getDataBuffer();
        if (db instanceof DataBufferInt) {
          pixels = ((DataBufferInt) db).getData();
          if (type == BufferedImage.TYPE_INT_ARGB) {
            format = ARGB;
          } else if (type == BufferedImage.TYPE_INT_RGB) {
            opaque();
          }
        }
      }
    }
    // Implements fall-through if not DataBufferInt above, or not a
    // known type, or not DataBufferInt for the data itself.
    if (pixels == null) {  // go the old school Java 1.0 route
      width = img.getWidth(null);
      height = img.getHeight(null);
      pixels = new int[width * height];
      PixelGrabber pg =
        new PixelGrabber(img, 0, 0, width, height, pixels, 0, width);
      try {
        pg.grabPixels();
      } catch (InterruptedException e) { }
    }
    pixelDensity = 1;
    pixelWidth = width;
    pixelHeight = height;
  }


  /** Set the high bits of all pixels to opaque. */
  protected void opaque() {
    for (int i = 0; i < pixels.length; i++) {
      pixels[i] = 0xFF000000 | pixels[i];
    }
  }


  /**
   * Use the getNative() method instead, which allows library interfaces to be
   * written in a cross-platform fashion for desktop, Android, and others.
   * This is still included for PGraphics objects, which may need the image.
   */
  @Override
  public Image getImage() {  // ignore
    return (Image) getNative();
  }


  /**
   * Returns a native BufferedImage from this PImage.
   */
  @Override
  public Object getNative() {  // ignore
    loadPixels();
    int type = (format == RGB) ?
      BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
    BufferedImage image = new BufferedImage(pixelWidth, pixelHeight, type);
    WritableRaster wr = image.getRaster();
    wr.setDataElements(0, 0, pixelWidth, pixelHeight, pixels);
    return image;
  }


  @Override
  public void resize(int w, int h) {  // ignore
    if (w <= 0 && h <= 0) {
      throw new IllegalArgumentException("width or height must be > 0 for resize");
    }

    if (w == 0) {  // Use height to determine relative size
      float diff = (float) h / (float) height;
      w = (int) (width * diff);
    } else if (h == 0) {  // Use the width to determine relative size
      float diff = (float) w / (float) width;
      h = (int) (height * diff);
    }

    BufferedImage img =
      shrinkImage((BufferedImage) getNative(), w*pixelDensity, h*pixelDensity);

    PImage temp = new PImageAWT(img);
    this.pixelWidth = temp.width;
    this.pixelHeight = temp.height;

    // Get the resized pixel array
    this.pixels = temp.pixels;

    this.width = pixelWidth / pixelDensity;
    this.height = pixelHeight / pixelDensity;

    // Mark the pixels array as altered
    updatePixels();
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
      } else if (targetWidth >= w) {
        w = targetWidth;
      }
      if (h > targetHeight) {
        h /= 2;
        if (h < targetHeight) {
          h = targetHeight;
        }
      } else if (targetHeight >= h) {
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

    if (g2 != null) {
      g2.dispose();
    }

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


  /*
  @Override
  protected boolean saveImpl(String path) {
    return ShimAWT.saveImage(this, path);
  }
  */
}