/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-14 The Processing Foundation
  Copyright (c) 2011-12 Ben Fry and Casey Reas

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License version 2
  as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package processing.app;

import java.awt.EventQueue;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;
import java.util.*;

import processing.app.ui.Toolkit;
import processing.core.*;


/**
 * Class that handles a small server that prevents multiple instances of
 * Processing from running simultaneously. If there's already an instance
 * running, it'll handle opening a new empty sketch, or any files that had
 * been passed in on the command line.
 */
public class SingleInstance {
  static final String SERVER_PORT = "instance_server.port";
  static final String SERVER_KEY = "instance_server.key";


  /**
   * Returns true if there's an instance of Processing already running.
   * Will not return true unless this code was able to successfully
   * contact the already running instance to have it launch sketches.
   * @return true if successfully launched on the other instance
   */
  static boolean alreadyRunning(String[] args) {
    return Preferences.get(SERVER_PORT) != null && sendArguments(args);
  }


  /**
   * Disable briefly for Processing to restart itself.
   */
  static public void clearRunning() {
    Preferences.unset(SERVER_PORT);
    Preferences.save();
  }


  static void startServer(final Base base) {
    try {
      Messages.log("Opening SingleInstance socket");
      final ServerSocket ss =
        new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
      Preferences.set(SERVER_PORT, "" + ss.getLocalPort());
      final String key = "" + Math.random();
      Preferences.set(SERVER_KEY, key);
      Preferences.save();

      Messages.log("Starting SingleInstance thread");
      new Thread(new Runnable() {
        public void run() {
          while (true) {
            try {
              Socket s = ss.accept();  // blocks (sleeps) until connection
              final BufferedReader reader = PApplet.createReader(s.getInputStream());
              String receivedKey = reader.readLine();
              Messages.log(this, "key is " + key + ", received is " + receivedKey);

              if (key.equals(receivedKey)) {
                EventQueue.invokeLater(new Runnable() {
                  public void run() {
                    try {
                      Messages.log(this, "about to read line");
                      String path = reader.readLine();
                      if (path == null) {
                        // Because an attempt was made to launch the PDE again,
                        // throw the user a bone by at least opening a new
                        // Untitled window for them.
                        Messages.log(this, "opening new empty sketch");
//                        platform.base.handleNew();
                        base.handleNew();

                      } else {
                        // loop through the sketches that were passed in
                        do {
                          Messages.log(this, "calling open with " + path);
//                        platform.base.handleOpen(filename);
                          base.handleOpen(path);
                          path = reader.readLine();
                        } while (path != null);
                      }
                    } catch (IOException e) {
                      e.printStackTrace();
                    }
                  }
                });
              } else {
                Messages.log(this, "keys do not match");
              }
//              }
            } catch (IOException e) {
              Messages.err("SingleInstance error while listening", e);
            }
          }
        }
      }, "SingleInstance Server").start();

    } catch (IOException e) {
      Messages.err("Could not create single instance server.", e);
    }
  }


  static boolean sendArguments(String[] args) {  //, long timeout) {
    boolean toRet;
    Messages.log("Proxy settings will now be temporarily erased");
    System.clearProperty("http.proxyHost");
    System.clearProperty("https.proxyHost");
    System.clearProperty("socksProxyHost");
    System.clearProperty("java.net.useSystemProxies"); // bye bye settings for now
    Messages.log("Proxy settings have been erased.");
    try {
      Messages.log("Checking to see if Processing is already running");
      int port = Preferences.getInteger(SERVER_PORT);
      String key = Preferences.get(SERVER_KEY);

      Socket socket = null;
      try {
        socket = new Socket(InetAddress.getLoopbackAddress(), port);
      } catch (Exception ignored) { }

      if (socket != null) {
        Messages.log("Processing is already running, sending command line");
        PrintWriter writer = PApplet.createWriter(socket.getOutputStream());
        writer.println(key);
        for (String arg : args) {
          writer.println(arg);
        }
        writer.flush();
        writer.close();
        toRet = true; // Critical that proxy is reset
      }
    } catch (IOException e) {
      Messages.err("Error sending commands to other instance", e);
    }
    Messages.log("Processing is not already running (or could not connect)");
    toRet = false; // Critical that proxy is reset
    Messages.log("Proxy settings are returning");
    if (Preferences.get("proxy.system").equals("true")) {
      // Use the system proxy settings by default
      // https://github.com/processing/processing/issues/2643
      System.setProperty("java.net.useSystemProxies", "true");
    }
    Preferences.handleProxy("http", "http.proxyHost", "http.proxyPort");
    Preferences.handleProxy("https", "https.proxyHost", "https.proxyPort");
    Preferences.handleProxy("socks", "socksProxyHost", "socksProxyPort");
    Messages.log("Proxy settings are back.");
    return toRet;
  }
}
