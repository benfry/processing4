package processing.core.io;

import processing.core.PImage;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Default handler for saving images in Processing
 *
 * Delegates to other implementations of ImageWriter
 * based on the extension in filename
 * @see PImage#save(String)
 */
public class ExtensionSwitchingImageWriter implements ImageWriter {
  private final Map<String, ImageWriter> extensionWriterMap;
  private final Map<String, String> canonicalExtensionsMap;

  public ExtensionSwitchingImageWriter() {
    this(Map.ofEntries(Map.entry(".tiff", new TiffImageWriter()),
                       Map.entry(".tga", new TGAImageWriter()),
                       Map.entry("awt", new AWTImageWriter())),
         Map.ofEntries(Map.entry(".tiff", ".tiff"),
                       Map.entry(".tif", ".tiff"),
                       Map.entry(".tga", ".tga")));
  }

  public ExtensionSwitchingImageWriter(
    Map<String, ImageWriter> extensionToWriterMap,
    Map<String, String> canonicalExtensionsMap) {
    this.extensionWriterMap = extensionToWriterMap;
    this.canonicalExtensionsMap = canonicalExtensionsMap;
  }

  @Override
  public boolean save(String filename, PImage image) {
    return getExtension(filename)
      .map(useWriterForExtension(filename, image))
      .orElseGet(useFallbackWriter(filename, image));
  }

  private Optional<String> getExtension(String filename) {
    int splitIndex = filename.lastIndexOf(".");
    return Optional.ofNullable(
      splitIndex < 0 ? null : filename.substring(splitIndex));
  }

  private Function<String, Boolean> useWriterForExtension(String filename,
                                                          PImage image) {
    return (ext) -> getWriterForExtension(ext).save(filename, image);
  }

  private ImageWriter getWriterForExtension(String extension) {
    return extensionWriterMap.get(getCanonicalExtension(extension));
  }

  /**
   *   Coalesce alternative spelling of extensions into a single 'canonical'
   *   extension matching a key in #extensionWriterMap
   *
   *   eg: .jpeg, .jpg -> .jpg
   */
  private String getCanonicalExtension(String extension) {
    // use 'awt' as the default handler for formats without an ImageWriter
    return canonicalExtensionsMap.getOrDefault(extension, "awt");
  }

  private Supplier<Boolean> useFallbackWriter(String filename, PImage image) {
    return () -> this.save(filename + ".tif", image);
  }

}
