/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2014-20 The Processing Foundation

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

import java.io.*;
import java.net.*;
import java.util.*;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;


/**
 * Ant Task for downloading the latest JRE, JDK, or OpenJFX release.
 */
public class Downloader extends Task {
  private String platform; // macos
  private int train;  // Java 11 (was 1 through Java 8)
  private int version;  // 0 (was 8 prior to Java 9)
  private int update;  // Update 131
  private int build;  // Build 11

  private String component;  // "jdk" or "jfx"
  private String flavor;  // Like "zip"
  private String path;  // target path


  public Downloader() { }


  /**
   * Set the platform being used.
   *
   * @param platform The platfom for which files are being downloaded like macosx.
   */
  public void setPlatform(String platform) {
    this.platform = platform;
  }


  /**
   * Specify the build train being used.
   *
   * @param train The build train like 1 (Java 8 and before) or 11 (Java 11).
   */
  public void setTrain(int train) {
    this.train = train;
  }


  /**
   * Set the version to download within the given build train.
   *
   * @param version The version within the train to use like 0 for "11.0.1_13".
   */
  public void setVersion(int version) {
    this.version = version;
  }


  /**
   * Set the update number to download within the given build train.
   *
   * @param update The update within the version to use like 1 for "11.0.1_13".
   */
  public void setUpdate(int update) {
    this.update = update;
  }


  /**
   * Set the build number to download.
   *
   * @param build The build number to use within the build train like 13 for "11.0.1_13".
   */
  public void setBuild(int build) {
    this.build = build;
  }


  /**
   * Indicate what component or release type of Java is being downloaded.
   *
   * @param component The component to download like "jdk" or "jfx"
   */
  public void setComponent(String component) {
    this.component = component.toLowerCase();
  }


  /**
   * Indicate the file flavor to be downloaded.
   *
   * @param flavor The flavor of file (dependent on platform) to be downloaded. Like "-x64.tar.gz".
   */
  public void setFlavor(String flavor) {
    this.flavor = flavor;
  }


  /**
   * Set the path to which the file should be downloaded.
   *
   * @param path The path to which the file should be downloaded.
   */
  public void setPath(String path) {
    this.path = path;
  }


  /**
   * Entry point called by Ant
   */
  public void execute() throws BuildException {
    if (train == 0) {
      // 1 if prior to Java 9
      throw new BuildException("Train (i.e. 1 or 11) must be set");
    }

    if (train != 11 && version == 0) {
      throw new BuildException("Version (i.e. 7 or 8) must be set");
    }

    if (build == 0) {
      throw new BuildException("Build number must be set");
    }

    if (flavor == null) {
      throw new BuildException("You've gotta choose a flavor (macosx-x64.dmg, windows-x64.exe...)");
    }

    try {
      download();
    } catch (IOException e) {
      throw new BuildException(e);
    }
  }


  /**
   * Download the package from AdoptOpenJDK or Oracle.
   */
  void download() throws IOException {
    DownloadUrlGenerator downloadUrlGenerator;

    // Determine url generator
    if (component.equals("jdk")) {
      downloadUrlGenerator = new AdoptOpenJdkDownloadUrlGenerator();
    } else if (component.equals("jfx")) {
      downloadUrlGenerator = new GluonHqDownloadUrlGenerator();
    } else {
      throw new RuntimeException("Do not know how to download: " + component);
    }

    if (path == null) {
      path = downloadUrlGenerator.getLocalFilename(
        platform,
        component,
        train,
        version,
        update,
        build,
        flavor
      );
    }

    String url = downloadUrlGenerator.buildUrl(
        platform,
        component,
        train,
        version,
        update,
        build,
        flavor
    );

    System.out.println("Attempting download at " + url);

    HttpURLConnection conn =
      (HttpURLConnection) new URL(url).openConnection();

    /*
    Optional<String> cookieMaybe = downloadItem.getCookie();

    if (cookieMaybe.isPresent()) {
      conn.setRequestProperty("Cookie", cookieMaybe.get());
    }
    */

    while (conn.getResponseCode() == 302 || conn.getResponseCode() == 301) {
      Map<String, List<String>> headers = conn.getHeaderFields();
      List<String> location = headers.get("Location");
      if (location.size() == 1) {
        url = location.get(0);
        System.out.println("Redirecting to " + url);
      } else {
        // TODO should
        throw new BuildException("Received multiple redirect locations");
      }
      /*
      List<String> cookies = headers.get("Set-Cookie");
      conn = (HttpURLConnection) new URL(url).openConnection();
      if (cookies != null) {
        for (String cookie : cookies) {
          conn.setRequestProperty("Cookie", cookie);
        }
      }

      if (cookieMaybe.isPresent()) {
        conn.setRequestProperty("Cookie", cookieMaybe.get());
      }
      */
      conn.connect();
    }

    if (conn.getResponseCode() == 200) {
      InputStream input = conn.getInputStream();
      BufferedInputStream bis = new BufferedInputStream(input);
      File outputFile = new File(path); //folder, filename);

      System.out.format("Downloading %s from %s%n",
                        outputFile.getAbsolutePath(), url);

      // Write to a temp file so that we don't have an incomplete download
      // masquerading as a good archive.
      File tempFile = File.createTempFile("download", "", outputFile.getParentFile());
      BufferedOutputStream output =
        new BufferedOutputStream(new FileOutputStream(tempFile));
      int c = bis.read();
      while (c != -1) {
        output.write(c);
        c = bis.read();
      }
      bis.close();
      output.flush();
      output.close();

      if (outputFile.exists()) {
        if (!outputFile.delete()) {
          throw new BuildException("Could not delete old download: " +
                                   outputFile.getAbsolutePath());
        }
      }
      if (!tempFile.renameTo(outputFile)) {
        throw new BuildException(String.format("Could not rename %s to %s",
                                               tempFile.getAbsolutePath(),
                                               outputFile.getAbsolutePath()));
      }
    } else {
      printHeaders(conn);
      System.exit(1);
    }
  }


  static private void printHeaders(URLConnection conn) {
    Map<String, List<String>> headers = conn.getHeaderFields();
    Set<Map.Entry<String, List<String>>> entrySet = headers.entrySet();
    for (Map.Entry<String, List<String>> entry : entrySet) {
      String headerName = entry.getKey();
      System.err.println("Header Name:" + headerName);
      List<String> headerValues = entry.getValue();
      for (String value : headerValues) {
        System.err.println("Header Value:" + value);
      }
      System.err.println();
    }
  }
}
