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
  static final int HTTP_OK = 200;
  static final int HTTP_NOT_FOUND = 404;
  static final int HTTP_BAD_METHOD = 405;

  /** where worker threads stand idle */
  private final Vector<Worker> threads = new Vector<>();

  /** max # worker threads */
  static final int WORKERS = 5;

  private final int port;
  private final ZipFile zip;
  private final Map<String, ZipEntry> entries;

  static final int BUFFER_SIZE = 8192;

  static final byte[] EOL = { (byte) '\r', (byte) '\n' };


  public WebServer(File zipFile, int port) throws IOException {
    this.zip = new ZipFile(zipFile);
    this.port = port;

    entries = new HashMap<>();
    Enumeration<? extends ZipEntry> en = zip.entries();
    while (en.hasMoreElements()) {
      ZipEntry entry = en.nextElement();
      entries.put(entry.getName(), entry);
    }

    // start worker threads
    for (int i = 0; i < WORKERS; ++i) {
      Worker w = new Worker();
      Thread t = new Thread(w, "Web Server Worker #" + i);
      t.start();
      threads.addElement(w);
    }

    new Thread(() -> {
      try {
        ServerSocket ss = new ServerSocket(port);
        while (true) {
          Socket s = ss.accept();
          synchronized (threads) {
            if (threads.isEmpty()) {
              Worker ws = new Worker();
              ws.setSocket(s);
              (new Thread(ws, "additional worker")).start();
            } else {
              Worker w = threads.elementAt(0);
              threads.removeElementAt(0);
              w.setSocket(s);
            }
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }).start();
  }


  public String getPrefix() {
    return "http://localhost:" + port + "/";
  }


  class Worker implements Runnable {
    // buffer to use for requests
    byte[] buffer;
    private Socket socket;

    Worker() {
      buffer = new byte[BUFFER_SIZE];
      socket = null;
    }

    synchronized void setSocket(Socket s) {
      this.socket = s;
      notify();
    }

    public synchronized void run() {
      while (true) {
        if (socket == null) {
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
        socket = null;
        synchronized (threads) {
          if (threads.size() >= WebServer.WORKERS) {
            // too many threads, exit this one
            return;
          } else {
            threads.addElement(this);
          }
        }
      }
    }

    void handleClient() throws IOException {
      InputStream is = new BufferedInputStream(socket.getInputStream());
      PrintStream ps = new PrintStream(socket.getOutputStream());
      // we will only block in read for this many milliseconds
      // before we fail with java.io.InterruptedIOException,
      // at which point we will abandon the connection.
      socket.setSoTimeout(10000);
      socket.setTcpNoDelay(true);
      // zero out the buffer from last time
      for (int i = 0; i < BUFFER_SIZE; i++) {
        buffer[i] = 0;
      }
      try {
        // We only support HTTP GET/HEAD, and don't support any fancy HTTP
        // options, so we're only interested really in the first line.
        int length = 0;

        outerLoop:
        while (length < BUFFER_SIZE) {
          int r = is.read(buffer, length, BUFFER_SIZE - length);
          if (r == -1) {
            return;  // EOF
          }
          int i = length;
          length += r;
          for (; i < length; i++) {
            if (buffer[i] == (byte) '\n' || buffer[i] == (byte) '\r') {
              break outerLoop;  // read one line
            }
          }
        }

        // are we doing a GET or just a HEAD
        boolean doingGet;
        // beginning of file name
        int index;
        if (buffer[0] == (byte) 'G' &&
          buffer[1] == (byte) 'E' &&
          buffer[2] == (byte) 'T' &&
          buffer[3] == (byte) ' ') {
          doingGet = true;
          index = 4;
        } else if (buffer[0] == (byte) 'H' &&
          buffer[1] == (byte) 'E' &&
          buffer[2] == (byte) 'A' &&
          buffer[3] == (byte) 'D' &&
          buffer[4] == (byte) ' ') {
          doingGet = false;
          index = 5;
        } else {
          // we don't support this method
          ps.print("HTTP/1.0 " + HTTP_BAD_METHOD + " unsupported method type: ");
          ps.write(buffer, 0, 5);
          ps.write(EOL);
          ps.flush();
          socket.close();
          return;
        }

        int i;
        // find the file name, from:
        // GET /foo/bar.html HTTP/1.0
        // extract "/foo/bar.html"
        for (i = index; i < length; i++) {
          if (buffer[i] == (byte) ' ') {
            break;
          }
        }

        String path = new String(buffer, index, i - index);
        // get the zip entry, remove the front slash
        ZipEntry entry = entries.get(path.substring(1));
        boolean ok = printHeaders(ps, path, entry);
        if (entry != null) {
          InputStream stream = zip.getInputStream(entry);
          if (doingGet && ok) {
            sendFile(stream, ps);
          }
        } else {
          send404(ps);
        }
      } finally {
        socket.close();
      }
    }

    boolean printHeaders(PrintStream ps, String path, ZipEntry entry) throws IOException {
      int status;
      if (entry == null) {
        status = HTTP_NOT_FOUND;
        ps.print("HTTP/1.0 " + HTTP_NOT_FOUND + " Not Found");
      } else {
        status = HTTP_OK;
        ps.print("HTTP/1.0 " + HTTP_OK + " OK");
      }
      ps.write(EOL);
      Messages.log("From " + socket.getInetAddress().getHostAddress() + ": GET " + path + " --> " + status);

      ps.print("Server: Processing Reference Server");
      ps.write(EOL);
      ps.print("Date: " + new Date());
      ps.write(EOL);

      if (entry != null) {
        if (!entry.isDirectory()) {
          ps.print("Content-length: " + entry.getSize());
          ps.write(EOL);
          ps.print("Last Modified: " + new Date(entry.getTime()));
          ps.write(EOL);
          String name = entry.getName();
          int ind = name.lastIndexOf('.');
          String contentType = "application/x-unknown-content-type";
          if (ind > 0) {
            contentType = contentTypes.getOrDefault(name.substring(ind), contentType);
          }
          ps.print("Content-type: " + contentType);
        } else {
          ps.print("Content-type: text/html");
        }
        ps.write(EOL);
      }
      ps.write(EOL);  // adding another newline here [fry]

      // indicates whether to send a file on return
      return status == HTTP_OK;
    }

    void send404(PrintStream ps) throws IOException {
      ps.write(EOL);
      ps.write(EOL);
      ps.print("<html><body><h1>404 Not Found</h1>");
      ps.print("The requested resource was not found.</body></html>");
      ps.write(EOL);
      ps.write(EOL);
    }

    void sendFile(InputStream is, PrintStream ps) throws IOException {
      try (is) {
        int n;
        while ((n = is.read(buffer)) > 0) {
          ps.write(buffer, 0, n);
        }
      }
    }
  }


  /** mapping of file extensions to content-types */
  static final Map<String, String> contentTypes = new ConcurrentHashMap<>();

  // get list of extensions to support (https://superuser.com/a/232101)
  // find . -type f | sed -En 's|.*/[^/]+\.([^/.]+)$|\1|p' | sort -u
  // -E is for macOS, use -r on Linux
  static {
    contentTypes.put("", "content/unknown");

    contentTypes.put(".css", "text/css");
    contentTypes.put(".csv", "text/csv");
    contentTypes.put(".eot", "application/vnd.ms-fontobject");  // only in 3.x
    contentTypes.put(".gif", "image/gif");
    contentTypes.put(".html", "text/html");
    contentTypes.put(".ico", "image/x-icon");  // only in 3.x?
    contentTypes.put(".jpeg", "image/jpeg");
    contentTypes.put(".jpg", "image/jpeg");
    contentTypes.put(".js", "text/javascript");
    contentTypes.put(".json", "application/json");
    contentTypes.put(".md", "text/markdown");
    contentTypes.put(".mdx", "text/mdx");
    contentTypes.put(".mtl", "text/plain");  // https://stackoverflow.com/a/19304383
    contentTypes.put(".obj", "text/plain");  // https://stackoverflow.com/a/19304383
    contentTypes.put(".otf", "font/otf");
    contentTypes.put(".pde", "text/plain");
    contentTypes.put(".png", "image/png");
    contentTypes.put(".svg", "image/svg+xml");
    contentTypes.put(".tsv", "text/tab-separated-values");
    contentTypes.put(".ttf", "font/ttf");
    contentTypes.put(".txt", "text/plain");
    contentTypes.put(".vlw", "application/octet-stream");  // or maybe font/x-vlw
    contentTypes.put(".woff", "font/woff");
    contentTypes.put(".woff2", "font/woff2");
    contentTypes.put(".xml", "application/xml");  // https://datatracker.ietf.org/doc/html/rfc3023
    contentTypes.put(".yml", "text/yaml");
    contentTypes.put(".zip", "application/zip");
  }


  /**
   * A main() method for testing.
   *
   * <pre>
   * cd app
   * ant
   * open http://localhost:8053/reference/index.html
   * java -cp pde.jar processing.app.WebServer ../java/reference.zip
   * </pre>
   */
  static public void main(String[] args) {
    try {
      new WebServer(new File(args[0]), 8053);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}



