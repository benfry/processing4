package processing.app;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.*;


/**
 * This code is placed here in anticipation of running the reference from an
 * internal web server that reads the docs from a zip file, instead of using
 * thousands of .html files on the disk, which is really inefficient.
 * <p/>
 * This is a very simple, multi-threaded HTTP server, originally based on
 * <a href="http://j.mp/6BQwpI">this</a> article on java.sun.com.
 */
public class WebServer {

  /** where worker threads stand idle */
  static final Vector<WebServerWorker> threads = new Vector<>();

  /** max # worker threads */
  static final int WORKERS = 5;

  /** port to use, if there are complaints, move to preferences.txt */
  static final int PORT = 8053;


  static public int launch(String zipPath) throws IOException {
    final ZipFile zip = new ZipFile(zipPath);
    final Map<String, ZipEntry> entries = new HashMap<>();
    Enumeration<? extends ZipEntry> en = zip.entries();
    while (en.hasMoreElements()) {
      ZipEntry entry = en.nextElement();
      entries.put(entry.getName(), entry);
    }

    // start worker threads
    for (int i = 0; i < WORKERS; ++i) {
      WebServerWorker w = new WebServerWorker(zip, entries);
      Thread t = new Thread(w, "Web Server Worker #" + i);
      t.start();
      threads.addElement(w);
    }

    new Thread(() -> {
      try {
        ServerSocket ss = new ServerSocket(PORT);
        while (true) {
          Socket s = ss.accept();
          synchronized (threads) {
            if (threads.isEmpty()) {
              WebServerWorker ws = new WebServerWorker(zip, entries);
              ws.setSocket(s);
              (new Thread(ws, "additional worker")).start();
            } else {
              WebServerWorker w = threads.elementAt(0);
              threads.removeElementAt(0);
              w.setSocket(s);
            }
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }).start();
    return PORT;
  }


  // main method for testing
  static public void main(String[] args) {
    try {
      launch(args[0]);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}


class WebServerWorker implements Runnable {
  static final int HTTP_OK = 200;
  static final int HTTP_NOT_FOUND = 404;
  static final int HTTP_BAD_METHOD = 405;

  private final ZipFile zip;
  private final Map<String, ZipEntry> entries;

  final static int BUF_SIZE = 2048;

  static final byte[] EOL = { (byte)'\r', (byte)'\n' };

  /* buffer to use for requests */
  byte[] buf;
  private Socket s;

  WebServerWorker(ZipFile zip, Map<String, ZipEntry> entries) {
    this.entries = entries;
    this.zip = zip;

    buf = new byte[BUF_SIZE];
    s = null;
  }

  synchronized void setSocket(Socket s) {
    this.s = s;
    notify();
  }

  public synchronized void run() {
    while (true) {
      if (s == null) {
        try {
          wait();
        } catch (InterruptedException e) {
          continue;
        }
      }
      try {
        handleClient();
      } catch (Exception e) {
        e.printStackTrace();
      }
      // go back in wait queue if there's fewer
      // than numHandler connections.
      s = null;
      synchronized (WebServer.threads) {
        if (WebServer.threads.size() >= WebServer.WORKERS) {
          // too many threads, exit this one
          return;
        } else {
          WebServer.threads.addElement(this);
        }
      }
    }
  }


  void handleClient() throws IOException {
    InputStream is = new BufferedInputStream(s.getInputStream());
    PrintStream ps = new PrintStream(s.getOutputStream());
    // we will only block in read for this many milliseconds
    // before we fail with java.io.InterruptedIOException,
    // at which point we will abandon the connection.
    s.setSoTimeout(10000);
    s.setTcpNoDelay(true);
    // zero out the buffer from last time
    for (int i = 0; i < BUF_SIZE; i++) {
      buf[i] = 0;
    }
    try {
      // We only support HTTP GET/HEAD, and don't support any fancy HTTP
      // options, so we're only interested really in the first line.
      int length = 0;

      outerLoop:
      while (length < BUF_SIZE) {
        int r = is.read(buf, length, BUF_SIZE - length);
        if (r == -1) {
          return;  // EOF
        }
        int i = length;
        length += r;
        for (; i < length; i++) {
          if (buf[i] == (byte) '\n' || buf[i] == (byte) '\r') {
            break outerLoop;  // read one line
          }
        }
      }

      // are we doing a GET or just a HEAD
      boolean doingGet;
      // beginning of file name
      int index;
      if (buf[0] == (byte) 'G' &&
        buf[1] == (byte) 'E' &&
        buf[2] == (byte) 'T' &&
        buf[3] == (byte) ' ') {
        doingGet = true;
        index = 4;
      } else if (buf[0] == (byte) 'H' &&
        buf[1] == (byte) 'E' &&
        buf[2] == (byte) 'A' &&
        buf[3] == (byte) 'D' &&
        buf[4] == (byte) ' ') {
        doingGet = false;
        index = 5;
      } else {
        // we don't support this method
        ps.print("HTTP/1.0 " + HTTP_BAD_METHOD + " unsupported method type: ");
        ps.write(buf, 0, 5);
        ps.write(EOL);
        ps.flush();
        s.close();
        return;
      }

      int i;
      // find the file name, from:
      // GET /foo/bar.html HTTP/1.0
      // extract "/foo/bar.html"
      for (i = index; i < length; i++) {
        if (buf[i] == (byte) ' ') {
          break;
        }
      }

      String filename = new String(buf, index, i - index);
      // get the zip entry, remove the front slash
      ZipEntry entry = entries.get(filename.substring(1));
      boolean ok = printHeaders(entry, ps);
      if (entry != null) {
        InputStream stream = zip.getInputStream(entry);
        if (doingGet && ok) {
          sendFile(stream, ps);
        }
      } else {
        send404(ps);
      }
    } finally {
      s.close();
    }
  }


  boolean printHeaders(ZipEntry entry, PrintStream ps) throws IOException {
    boolean ret;
    int rCode;
    if (entry == null) {
      rCode = HTTP_NOT_FOUND;
      ps.print("HTTP/1.0 " + HTTP_NOT_FOUND + " Not Found");
      ps.write(EOL);
      ret = false;
    } else {
      rCode = HTTP_OK;
      ps.print("HTTP/1.0 " + HTTP_OK + " OK");
      ps.write(EOL);
      ret = true;
    }
    if (entry != null) {
      Messages.log("From " + s.getInetAddress().getHostAddress() + ": GET " + entry.getName() + " --> " + rCode);
    }
    ps.print("Server: Processing Documentation Server");
    ps.write(EOL);
    ps.print("Date: " + new Date());
    ps.write(EOL);
    if (ret) {
      if (!entry.isDirectory()) {
        ps.print("Content-length: " + entry.getSize());
        ps.write(EOL);
        ps.print("Last Modified: " + new Date(entry.getTime()));
        ps.write(EOL);
        String name = entry.getName();
        int ind = name.lastIndexOf('.');
        String ct = null;
        if (ind > 0) {
          ct = map.get(name.substring(ind));
        }
        if (ct == null) {
          //System.err.println("unknown content type " + name.substring(ind));
          ct = "application/x-unknown-content-type";
        }
        ps.print("Content-type: " + ct);
      } else {
        ps.print("Content-type: text/html");
      }
      ps.write(EOL);
    }
    ps.write(EOL);  // adding another newline here [fry]
    return ret;
  }


  void send404(PrintStream ps) throws IOException {
    ps.write(EOL);
    ps.write(EOL);
    ps.print("<html><body><h1>404 Not Found</h1>" +
      "The requested resource was not found.</body></html>");
    ps.write(EOL);
    ps.write(EOL);
  }


  void sendFile(InputStream is, PrintStream ps) throws IOException {
    try (is) {
      int n;
      while ((n = is.read(buf)) > 0) {
        ps.write(buf, 0, n);
      }
    }
  }


  /** mapping of file extensions to content-types */
  static Map<String, String> map = new ConcurrentHashMap<>();

  static {
    map.put("", "content/unknown");

    map.put(".uu", "application/octet-stream");
    map.put(".exe", "application/octet-stream");
    map.put(".ps", "application/postscript");
    map.put(".zip", "application/zip");
    map.put(".sh", "application/x-shar");
    map.put(".tar", "application/x-tar");
    map.put(".snd", "audio/basic");
    map.put(".au", "audio/basic");
    map.put(".wav", "audio/x-wav");

    map.put(".gif", "image/gif");
    map.put(".jpg", "image/jpeg");
    map.put(".jpeg", "image/jpeg");

    map.put(".htm", "text/html");
    map.put(".html", "text/html");
    map.put(".css", "text/css");
    map.put(".js", "text/javascript");

    map.put(".txt", "text/plain");
    map.put(".java", "text/plain");

    map.put(".c", "text/plain");
    map.put(".cc", "text/plain");
    map.put(".c++", "text/plain");
    map.put(".h", "text/plain");
    map.put(".pl", "text/plain");
  }
}


