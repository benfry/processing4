package processing.core.io;

import processing.core.PImage;

/**
 * Handler for saving implementations of PImage
 * 
 * @see PImage#save(String)
 */
public interface ImageWriter {
  
  /**
   * @param filename String representing the location to save the image file
   * @param image the PImage object being saved
   * @return true if the image was saved successfully, false on error
   */
  boolean save(String filename, PImage image);
}
