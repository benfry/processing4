/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-15 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU General Public License
  version 2, as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.app;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.*;

import processing.core.PApplet;
import processing.data.StringDict;
import processing.data.StringList;


public class Util {

  /**
   * Get the number of lines in a file by counting the number of newline
   * characters inside a String (and adding 1).
   */
  static public int countLines(String what) {
    int count = 1;
    for (char c : what.toCharArray()) {
      if (c == '\n') count++;
    }
    return count;
  }


  /**
   * Same as PApplet.loadBytes(), however never does gzip decoding.
   */
  static public byte[] loadBytesRaw(File file) throws IOException {
    int size = (int) file.length();
    FileInputStream input = new FileInputStream(file);
    byte[] buffer = new byte[size];
    int offset = 0;
    int bytesRead;
    while ((bytesRead = input.read(buffer, offset, size-offset)) != -1) {
      offset += bytesRead;
      if (bytesRead == 0) break;
    }
    input.close();  // weren't properly being closed
    return buffer;
  }


  static public InputStream createInput(String path) throws IOException {
    URL url = new URL(path);
    URLConnection conn = url.openConnection();

    if (conn instanceof HttpURLConnection httpConn) {
      // Will not handle a protocol change (see below)
      httpConn.setInstanceFollowRedirects(true);
      int response = httpConn.getResponseCode();
      // Default won't follow HTTP -> HTTPS redirects for security reasons
      // http://stackoverflow.com/a/1884427
      if (response >= 300 && response < 400) {
        String newLocation = httpConn.getHeaderField("Location");
        return createInput(newLocation);
      }
      return conn.getInputStream();

    } else if (conn instanceof JarURLConnection) {
      return url.openStream();
    }
    return null;
  }


  /**
   * Read from a file with a bunch of attribute/value pairs
   * that are separated by = and ignore comments with #.
   * As of 4.0.2, set allowHex to true if hex colors are used in the file.
   * This will disable support for comments that begin later in the line.
   * If allowHex is false, then comments can appear later on a line, which is
   * necessary for the contribution .properties files. Blank lines are ignored.
   */
  static public StringDict readSettings(File inputFile, boolean allowHex) {
    if (!inputFile.exists()) {
      Messages.err(inputFile + " does not exist inside readSettings()");
      return null;
    }
    String[] lines = PApplet.loadStrings(inputFile);
    if (lines == null) {
      System.err.println("Could not read " + inputFile);
      return null;
    }
    return readSettings(inputFile.toString(), lines, allowHex);
  }


  /**
   * Parse a String array that contains attribute/value pairs separated
   * by = (the equals sign). The # (hash) symbol is used to denote comments.
   * As of 4.0.2, set allowHex to true if hex colors are used in the file.
   * This will disable support for comments that begin later in the line.
   * Blank lines are always ignored.
   *
   * @param filename Name of the input file; only used for error messages.
   * @param lines Lines already parsed from the input file.
   * @param allowHex If false, # indicates comment to the end of line,
   *                 which is necessary for the .properties files
   *                 used by Contributions. If true, # is unharmed,
   *                 allowing for hex characters and colors (used by
   *                 themes and preferences).
   */
  static public StringDict readSettings(String filename, String[] lines, boolean allowHex) {
    StringDict settings = new StringDict();
    for (String line : lines) {
      // Remove extra whitespace (including the x00A0 and xFEFF)
      line = PApplet.trim(line);

      if (!allowHex && line.contains("#")) {
        line = line.substring(0, line.indexOf('#')).trim();
      }

      if (line.length() != 0 && line.charAt(0) != '#') {
        int equals = line.indexOf('=');
        if (equals == -1) {
          if (filename != null) {
            System.err.println("Ignoring illegal line in " + filename + ":");
            System.err.println("  " + line);
          }
        } else {
          String attr = line.substring(0, equals).trim();
          String valu = line.substring(equals + 1).trim();
          settings.set(attr, valu);
        }
      }
    }
    return settings;
  }


  static public void copyFile(File sourceFile,
                              File targetFile) throws IOException {
    BufferedInputStream from =
      new BufferedInputStream(new FileInputStream(sourceFile));
    BufferedOutputStream to =
      new BufferedOutputStream(new FileOutputStream(targetFile));
    byte[] buffer = new byte[16 * 1024];
    int bytesRead;
    while ((bytesRead = from.read(buffer)) != -1) {
      to.write(buffer, 0, bytesRead);
    }
    from.close();

    to.flush();
    to.close();

    //noinspection ResultOfMethodCallIgnored
    targetFile.setLastModified(sourceFile.lastModified());
    //noinspection ResultOfMethodCallIgnored
    targetFile.setExecutable(sourceFile.canExecute());
  }


  /**
   * Grab the contents of a file as a string. Connects lines with \n,
   * even if the input file used \r\n.
   */
  static public String loadFile(File file) {
    if (file != null && file.exists()) {
      String[] contents = PApplet.loadStrings(file);
      if (contents != null) {
        return PApplet.join(contents, "\n");
      }
    }
    return null;
  }


  /**
   * Spew the contents of a String object out to a file. As of 3.0 beta 2,
   * this will replace and write \r\n for newlines on Windows.
   * https://github.com/processing/processing/issues/3455
   * As of 3.3.7, this puts a newline at the end of the file,
   * per good practice/POSIX: https://stackoverflow.com/a/729795
   */
  static public void saveFile(String text, File file) throws IOException {
    String[] lines = text.split("\\r?\\n");
    File temp = File.createTempFile(file.getName(), null, file.getParentFile());
    try {
      // fix from cjwant to prevent symlinks from being destroyed.
      file = file.getCanonicalFile();

    } catch (IOException e) {
      throw new IOException("Could not resolve canonical representation of " +
                            file.getAbsolutePath());
    }
    // Could use saveStrings(), but we wouldn't be able to checkError()
    PrintWriter writer = PApplet.createWriter(temp);
    for (String line : lines) {
      writer.println(line);
    }
    boolean error = writer.checkError();  // calls flush()
    writer.close();  // attempt to close regardless
    if (error) {
      throw new IOException("Error while trying to save " + file);
    }

    // remove the old file before renaming the temp file
    if (file.exists()) {
      boolean result = file.delete();
      if (!result) {
        throw new IOException("Could not remove old version of " +
                              file.getAbsolutePath());
      }
    }
    boolean result = temp.renameTo(file);
    if (!result) {
      throw new IOException("Could not replace " + file.getAbsolutePath() +
                            " with " + temp.getAbsolutePath());
    }
  }


  /**
   * Create a temporary folder by using the createTempFile() mechanism,
   * deleting the file it creates, and making a folder using the location
   * that was provided.
   * <p>
   * Unlike createTempFile(), there is no minimum size for prefix. If
   * prefix is less than 3 characters, the remaining characters will be
   * filled with underscores
   */
  static public File createTempFolder(String prefix, String suffix,
                                      File directory) throws IOException {
    int fillChars = 3 - prefix.length();
    if (fillChars > 0) {
      prefix += "_".repeat(fillChars);
    }
    if (directory == null) {
      directory = getProcessingTemp();
    }
    File folder = File.createTempFile(prefix, suffix, directory);
    // Now delete that file and create a folder in its place
    if (!folder.delete()) {
      throw new IOException("Could not remove " + folder +
        " to create a temporary folder");
    }
    if (!folder.mkdirs()) {
      throw new IOException("Unable to create " + folder +
        ", please check permissions for " + folder.getParentFile());
    }
    // And send the folder back to your friends
    return folder;
  }


  static public File getProcessingTemp() throws IOException {
    String tmpDir = System.getProperty("java.io.tmpdir");
    File directory = new File(tmpDir, "processing");
    if (!directory.exists()) {
      if (directory.mkdirs()) {
        // Set the parent directory writable for multi-user machines.
        // https://github.com/processing/processing4/issues/666
        if (Platform.isLinux()) {
          Path path = directory.toPath();
          Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxrwxrwx"));

        } else {
          if (!directory.setReadable(true, false)) {
            System.err.println("Could not set readable for all: " + directory);
          }
          if (!directory.setWritable(true, false)) {
            System.err.println("Could not set writable for all: " + directory);
          }
          if (!directory.setExecutable(true, false)) {
            System.err.println("Could not set writable for all: " + directory);
          }
        }
      } else {
        throw new IOException("Could not create temp directory. " +
          "Check that you have permissions to write to " + tmpDir);
      }
    }
    return directory;
  }


  /**
   * Copy a folder from one place to another. This ignores all dot files and
   * folders found in the source directory, to avoid copying silly .DS_Store
   * files and potentially troublesome .svn folders.
   */
  static public void copyDir(File sourceDir,
                             File targetDir) throws IOException {
    if (sourceDir.equals(targetDir)) {
      final String urDum = "source and target directories are identical";
      throw new IllegalArgumentException(urDum);
    }
    if (!targetDir.exists() && !targetDir.mkdirs()) {
      throw new IOException("Could not create " + targetDir);
    }
    String[] filenames = sourceDir.list();
    if (filenames != null) {
      for (String filename : filenames) {
        // Ignore dot files (.DS_Store), dot folders (.svn) while copying
        if (filename.charAt(0) != '.') {
          File source = new File(sourceDir, filename);
          File target = new File(targetDir, filename);
          if (source.isDirectory()) {
            //target.mkdirs();
            copyDir(source, target);
            //noinspection ResultOfMethodCallIgnored
            target.setLastModified(source.lastModified());
          } else {
            copyFile(source, target);
          }
        }
      }
    } else {
      throw new IOException("Could not read " + sourceDir);
    }
  }


  static public void copyDirNative(File sourceDir,
                                   File targetDir) throws IOException {
    Process process;
    if (Platform.isMacOS() || Platform.isLinux()) {
      process = Runtime.getRuntime().exec(new String[] {
        "cp", "-a", sourceDir.getAbsolutePath(), targetDir.getAbsolutePath()
      });
    } else {
      // TODO implement version that uses XCOPY here on Windows
      throw new RuntimeException("Not yet implemented on Windows");
    }
    try {
      int result = process.waitFor();
      if (result != 0) {
        throw new IOException("Error while copying (result " + result + ")");
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }


  /**
   * Remove all files in a directory and the directory itself.
   * Prints error messages with failed filenames. Does not follow symlinks.
   * Use Platform.deleteFile() instead, which first attempts to use
   * the Trash or Recycle Bin, out of an abundance of caution.
   */
  static public boolean removeDir(File dir) {
    return removeDir(dir, true);
  }


  /**
   * Remove all files in a directory and the directory itself.
   * Optionally, prints error messages with failed filenames.
   * Does not follow symlinks.
   */
  static public boolean removeDir(File dir, boolean printErrorMessages) {
    if (!dir.exists()) return true;

    boolean result = true;
    if (!Files.isSymbolicLink(dir.toPath())) {
      File[] files = dir.listFiles();
      if (files != null) {
        for (File child : files) {
          if (child.isFile()) {
            boolean deleted = child.delete();
            if (!deleted && printErrorMessages) {
              System.err.println("Could not delete " + child.getAbsolutePath());
            }
            result &= deleted;
          } else if (child.isDirectory()) {
            result &= removeDir(child, printErrorMessages);
          }
        }
      }
    }
    boolean deleted = dir.delete();
    if (!deleted && printErrorMessages) {
      System.err.println("Could not delete " + dir.getAbsolutePath());
    }
    result &= deleted;
    return result;
  }


  /**
   * Function to return the length of the file, or entire directory, including
   * the component files and sub-folders if passed.
   * @param file The file or folder to calculate
   */
  static public long calcSize(File file) {
    return file.isFile() ? file.length() : Util.calcFolderSize(file);
  }


  /**
   * Calculate the size of the contents of a folder.
   * Used to determine whether sketches are empty or not.
   * Note that the function calls itself recursively.
   */
  static public long calcFolderSize(File folder) {
    long size = 0;

    String[] filenames = folder.list();
    // null if folder doesn't exist, happens when deleting sketch
    if (filenames == null) return -1;

    for (String file : filenames) {
      if (!file.equals(".") && !file.equals("..") && !file.equals(".DS_Store")) {
        File fella = new File(folder, file);
        if (fella.isDirectory()) {
          long subfolderSize = calcFolderSize(fella);
          if (subfolderSize == -1) {
            return -1;
          } else {
            size += subfolderSize;
          }
        } else {
          size += fella.length();
        }
      }
    }
    return size;
  }


  /**
   * Recursively creates a list of all files within the specified folder,
   * and returns a list of their relative paths.
   * Ignores any files/folders prefixed with a dot.
   * @param relative true return relative paths instead of absolute paths
   */
  static public String[] listFiles(File folder, boolean relative) {
    return listFiles(folder, relative, null);
  }


  static public String[] listFiles(File folder, boolean relative,
                                   String extension) {
    if (extension != null) {
      if (!extension.startsWith(".")) {
        extension = "." + extension;
      }
    }

    StringList list = new StringList();
    listFilesImpl(folder, relative, extension, list);

    if (relative) {
      String[] outgoing = new String[list.size()];
      // remove the slash (or backslash) as well
      int prefixLength = folder.getAbsolutePath().length() + 1;
      for (int i = 0; i < outgoing.length; i++) {
        outgoing[i] = list.get(i).substring(prefixLength);
      }
      return outgoing;
    }
    return list.toArray();
  }


  static void listFilesImpl(File folder, boolean relative,
                            String extension, StringList list) {
    File[] items = folder.listFiles();
    if (items != null) {
      for (File item : items) {
        String name = item.getName();
        if (name.charAt(0) != '.') {
          if (item.isDirectory()) {
            listFilesImpl(item, relative, extension, list);

          } else {  // a file
            if (extension == null || name.endsWith(extension)) {
              list.append(item.getAbsolutePath());
            }
          }
        }
      }
    }
  }


  /**
   * @param folder source folder to search
   * @return an array of .jar and .zip files in that folder
   */
  static public File[] listJarFiles(File folder) {
    return folder.listFiles((dir, name) ->
      (!name.startsWith(".") &&
       (name.toLowerCase().endsWith(".jar") ||
        name.toLowerCase().endsWith(".zip"))));
  }


  /////////////////////////////////////////////////////////////////////////////


  /**
   * Given a folder, return a list of absolute paths to all jar or zip files
   * inside that folder, separated by pathSeparatorChar.
   * <p>
   * This will prepend a colon (or whatever the path separator is)
   * so that it can be directly appended to another path string.
   * <p>
   * As of 0136, this will no longer add the root folder as well.
   * <p>
   * This function doesn't bother checking to see if there are any .class
   * files in the folder or within a subfolder.
   */
  static public String contentsToClassPath(File folder) {
    if (folder == null) return "";

    StringBuilder sb = new StringBuilder();
    String sep = System.getProperty("path.separator");

    try {
      String path = folder.getCanonicalPath();

      // When getting the name of this folder, make sure it has a slash
      // after it, so that the names of sub-items can be added.
      if (!path.endsWith(File.separator)) {
        path += File.separator;
      }

      String[] list = folder.list();
      if (list != null) {
        for (String item : list) {
          // Skip . and ._ files. Prior to 0125p3, .jar files that had
          // OS X AppleDouble files associated would cause trouble.
          if (!item.startsWith(".")) {
            if (item.toLowerCase().endsWith(".jar") ||
              item.toLowerCase().endsWith(".zip")) {
              sb.append(sep);
              sb.append(path);
              sb.append(item);
            }
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();  // this would be odd
    }
    return sb.toString();
  }


  /**
   * A classpath, separated by the path separator, will contain
   * a series of .jar/.zip files or directories containing .class
   * files, or containing subdirectories that have .class files.
   *
   * @param path the input classpath
   * @return array of possible package names
   */
  static public StringList packageListFromClassPath(String path) {
//    Map<String, Object> map = new HashMap<String, Object>();
    StringList list = new StringList();
    String[] pieces =
      PApplet.split(path, File.pathSeparatorChar);

    for (String piece : pieces) {
      //System.out.println("checking piece '" + pieces[i] + "'");
      if (piece.length() != 0) {
        if (piece.toLowerCase().endsWith(".jar") ||
          piece.toLowerCase().endsWith(".zip")) {
          //System.out.println("checking " + pieces[i]);
          packageListFromZip(piece, list);

        } else {  // it's another type of file or directory
          File dir = new File(piece);
          if (dir.exists() && dir.isDirectory()) {
            packageListFromFolder(dir, null, list);
            //importCount = magicImportsRecursive(dir, null,
            //                                  map);
            //imports, importCount);
          }
        }
      }
    }
//    int mapCount = map.size();
//    String output[] = new String[mapCount];
//    int index = 0;
//    Set<String> set = map.keySet();
//    for (String s : set) {
//      output[index++] = s.replace('/', '.');
//    }
//    return output;
    StringList outgoing = new StringList(list.size());
    for (String item : list) {
      outgoing.append(item.replace('/', '.'));
    }
    return outgoing;
  }


  static private void packageListFromZip(String filename, StringList list) {
    try {
      ZipFile file = new ZipFile(filename);
      Enumeration<?> entries = file.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = (ZipEntry) entries.nextElement();

        if (!entry.isDirectory()) {
          String name = entry.getName();

          // Avoid META-INF because some jokers put .class files in there
          // https://github.com/processing/processing/issues/5778
          if (name.endsWith(".class") && !name.contains("META-INF/")) {
            int slash = name.lastIndexOf('/');
            if (slash != -1) {
              String packageName = name.substring(0, slash);
              list.appendUnique(packageName);
            }
          }
        }
      }
      file.close();
    } catch (IOException e) {
      System.err.println("Ignoring " + filename + " (" + e.getMessage() + ")");
      //e.printStackTrace();
    }
  }


  /**
   * Make list of package names by traversing a directory hierarchy.
   * Each time a class is found in a folder, add its containing set
   * of folders to the package list. If another folder is found,
   * walk down into that folder and continue.
   */
  static private void packageListFromFolder(File dir, String sofar,
                                            StringList list) {
    boolean foundClass = false;
    String[] filenames = dir.list();
    if (filenames != null) {
      for (String filename : filenames) {
        if (filename.equals(".") || filename.equals("..")) continue;

        File sub = new File(dir, filename);
        if (sub.isDirectory()) {
          String nowfar =
            (sofar == null) ? filename : (sofar + "." + filename);
          packageListFromFolder(sub, nowfar, list);
        } else if (!foundClass) {  // if no classes found in this folder yet
          if (filename.endsWith(".class")) {
            list.appendUnique(sofar);
            foundClass = true;
          }
        }
      }
    } else {
      System.err.println("Could not read " + dir);
    }
  }


  /**
   * Extract the contents of a .zip archive into a folder.
   * Ignores (does not extract) any __MACOSX files from macOS archives.
   */
  static public void unzip(File zipFile, File dest) throws IOException {
    FileInputStream fis = new FileInputStream(zipFile);
    CheckedInputStream checksum = new CheckedInputStream(fis, new Adler32());
    ZipInputStream zis = new ZipInputStream(new BufferedInputStream(checksum));
    ZipEntry entry;
    while ((entry = zis.getNextEntry()) != null) {
      final String name = entry.getName();
      if (!name.startsWith(("__MACOSX"))) {
        File currentFile = new File(dest, name);
        if (entry.isDirectory()) {
          currentFile.mkdirs();
        } else {
          File parentDir = currentFile.getParentFile();
          // Sometimes the directory entries aren't already created
          if (!parentDir.exists()) {
            parentDir.mkdirs();
          }
          currentFile.createNewFile();
          unzipEntry(zis, currentFile);
        }
      }
    }
  }


  static protected void unzipEntry(ZipInputStream zin, File f) throws IOException {
    FileOutputStream out = new FileOutputStream(f);
    byte[] b = new byte[512];
    int len;
    while ((len = zin.read(b)) != -1) {
      out.write(b, 0, len);
    }
    out.flush();
    out.close();
  }


  static public byte[] gzipEncode(byte[] what) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GZIPOutputStream output = new GZIPOutputStream(baos);
    PApplet.saveStream(output, new ByteArrayInputStream(what));
    output.close();
    return baos.toByteArray();
  }


  static public boolean containsNonASCII(String what) {
    for (char c : what.toCharArray()) {
      if (c < 32 || c > 127) return true;
    }
    return false;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static public String sanitizeHtmlTags(String str) {
    return str.replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;");
  }


  /**
   * This has a [link](http://example.com/) in [it](http://example.org/).
   * <p>
   * Becomes...
   * <p>
   * This has a <a href="http://example.com/">link</a> in <a
   * href="http://example.org/">it</a>.
   */
  static public String markDownLinksToHtml(String str) {
    Pattern p = Pattern.compile("\\[(.*?)]\\((.*?)\\)");
    Matcher m = p.matcher(str);

    StringBuilder sb = new StringBuilder();

    int start = 0;
    while (m.find(start)) {
      sb.append(str, start, m.start());

      String text = m.group(1);
      String url = m.group(2);

      sb.append("<a href=\"");
      sb.append(url);
      sb.append("\">");
      sb.append(text);
      sb.append("</a>");

      start = m.end();
    }
    sb.append(str.substring(start));
    return sb.toString();
  }


  static public String removeMarkDownLinks(String str) {
    StringBuilder name = new StringBuilder();
    if (str != null) {
      int parentheses = 0;
      for (char c : str.toCharArray()) {
        if (c == '[' || c == ']') {
          // pass
        } else if (c == '(') {
          parentheses++;
        } else if (c == ')') {
          parentheses--;
        } else if (parentheses == 0) {
          name.append(c);
        }
      }
    }
    return name.toString();
  }
}
