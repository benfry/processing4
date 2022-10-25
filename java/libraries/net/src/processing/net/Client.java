/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Client - basic network client implementation
  Part of the Processing project - http://processing.org

  Copyright (c) 2004-2007 Ben Fry and Casey Reas
  The previous version of this code was developed by Hernando Barragan

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation; either
  version 2.1 of the License, or (at your option) any later version.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.net;
import processing.core.*;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
   *
   * A client connects to a server and sends data back and forth. If anything
   * goes wrong with the connection, for example the host is not there or is
   * listening on a different port, an exception is thrown.
   *
 * @webref client
 * @webBrief The client class is used to create client Objects which connect to a server to exchange data
 * @instanceName client any variable of type Client
 * @usage Application
 * @see_external LIB_net/clientEvent
 */
@SuppressWarnings("unused")
public class Client implements Runnable {

  protected static final int MAX_BUFFER_SIZE = 1 << 27; // 128 MB

  PApplet parent;
  Method clientEventMethod;
  Method disconnectEventMethod;

  volatile Thread thread;
  Socket socket;
  int port;
  String host;

  public InputStream input;
  public OutputStream output;

  final Object bufferLock = new Object[0];

  byte[] buffer = new byte[32768];
  int bufferIndex;
  int bufferLast;

  boolean disposeRegistered = false;


  /**
   * @param parent typically use "this"
   * @param host address of the server
   * @param port port to read/write from on the server
   */
  public Client(PApplet parent, String host, int port) {
    this.parent = parent;
    this.host = host;
    this.port = port;

    try {
      socket = new Socket(this.host, this.port);
      input = socket.getInputStream();
      output = socket.getOutputStream();

      thread = new Thread(this);
      thread.start();

      parent.registerMethod("dispose", this);
      disposeRegistered = true;

      // reflection to check whether host sketch has a call for
      // public void clientEvent(processing.net.Client)
      // which would be called each time an event comes in
      try {
        clientEventMethod =
          parent.getClass().getMethod("clientEvent", Client.class);
      } catch (Exception e) {
        // no such method, or an error... which is fine, just ignore
      }
      // do the same for disconnectEvent(Client c);
      try {
        disconnectEventMethod =
          parent.getClass().getMethod("disconnectEvent", Client.class);
      } catch (Exception e) {
        // no such method, or an error... which is fine, just ignore
      }

    } catch (IOException e) {
      e.printStackTrace();
      dispose();
    }
  }


  /**
   * @param socket any object of type Socket
   */
  public Client(PApplet parent, Socket socket) throws IOException {
    this.parent = parent;
    this.socket = socket;

    input = socket.getInputStream();
    output = socket.getOutputStream();

    thread = new Thread(this);
    thread.start();

    // reflection to check whether host sketch has a call for
    // public void clientEvent(processing.net.Client)
    // which would be called each time an event comes in
    try {
      clientEventMethod =
          parent.getClass().getMethod("clientEvent", Client.class);
    } catch (Exception e) {
      // no such method, or an error... which is fine, just ignore
    }
    // do the same for disconnectEvent(Client c);
    try {
      disconnectEventMethod =
        parent.getClass().getMethod("disconnectEvent", Client.class);
    } catch (Exception e) {
      // no such method, or an error... which is fine, just ignore
    }
  }


  /**
   *
   * Disconnects from the server. Use to shut the connection when you're
   * finished with the Client.
   *
   * @webref client
   * @webBrief Disconnects from the server
   * @usage application
   */
  public void stop() {
    if (disconnectEventMethod != null && thread != null){
      try {
        disconnectEventMethod.invoke(parent, this);
      } catch (Exception e) {
        Throwable cause = e;
        // unwrap the exception if it came from the user code
        if (e instanceof InvocationTargetException && e.getCause() != null) {
          cause = e.getCause();
        }
        cause.printStackTrace();
        disconnectEventMethod = null;
      }
    }
    if (disposeRegistered) {
      parent.unregisterMethod("dispose", this);
      disposeRegistered = false;
    }
    dispose();
  }


  /**
   * Disconnect from the server: internal use only.
   * <P>
   * This should only be called by the internal functions in PApplet,
   * use stop() instead from within your own sketches.
   */
  public void dispose() {
    thread = null;
    try {
      if (input != null) {
        input.close();
        input = null;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      if (output != null) {
        output.close();
        output = null;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      if (socket != null) {
        socket.close();
        socket = null;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  @Override
  public void run() {
    byte[] readBuffer;
    { // make the read buffer same size as socket receive buffer so that
      // we don't waste cycles calling listeners when there is more data waiting
      int readBufferSize = 1 << 16; // 64 KB (default socket receive buffer size)
      try {
        readBufferSize = socket.getReceiveBufferSize();
      } catch (SocketException ignore) { }
      readBuffer = new byte[readBufferSize];
    }
    while (Thread.currentThread() == thread) {
      try {
        while (input != null) {
          int readCount;

          // try to read a byte using a blocking read.
          // An exception will occur when the sketch is exits.
          try {
            readCount = input.read(readBuffer, 0, readBuffer.length);
          } catch (SocketException e) {
             System.err.println("Client SocketException: " + e.getMessage());
             // the socket had a problem reading so don't try to read from it again.
             stop();
             return;
          }

          // read returns -1 if end-of-stream occurs (for example if the host disappears)
          if (readCount == -1) {
            System.err.println("Client got end-of-stream.");
            stop();
            return;
          }

          synchronized (bufferLock) {
            int freeBack = buffer.length - bufferLast;
            if (readCount > freeBack) {
              // not enough space at the back
              int bufferLength = bufferLast - bufferIndex;
              byte[] targetBuffer = buffer;
              if (bufferLength + readCount > buffer.length) {
                // can't fit even after compacting, resize the buffer
                // find the next power of two which can fit everything in
                int newSize = Integer.highestOneBit(bufferLength + readCount - 1) << 1;
                if (newSize > MAX_BUFFER_SIZE) {
                  // buffer is full because client is not reading (fast enough)
                  System.err.println("Client: can't receive more data, buffer is full. " +
                                         "Make sure you read the data from the client.");
                  stop();
                  return;
                }
                targetBuffer = new byte[newSize];
              }
              // compact the buffer (either in-place or into the new bigger buffer)
              System.arraycopy(buffer, bufferIndex, targetBuffer, 0, bufferLength);
              bufferLast -= bufferIndex;
              bufferIndex = 0;
              buffer = targetBuffer;
            }
            // copy all newly read bytes into the buffer
            System.arraycopy(readBuffer, 0, buffer, bufferLast, readCount);
            bufferLast += readCount;
          }

          // now post an event
          if (clientEventMethod != null) {
            try {
              clientEventMethod.invoke(parent, this);
            } catch (Exception e) {
              System.err.println("error, disabling clientEvent() for " + host);
              Throwable cause = e;
              // unwrap the exception if it came from the user code
              if (e instanceof InvocationTargetException && e.getCause() != null) {
                cause = e.getCause();
              }
              cause.printStackTrace();
              clientEventMethod = null;
            }
          }
        }
      } catch (IOException e) {
        //errorMessage("run", e);
        e.printStackTrace();
      }
    }
  }


  /**
   *
   * Returns <b>true</b> if this client is still active and hasn't run
   * into any trouble.
   *
   * @webref client
   * @webBrief Returns <b>true</b> if this client is still active
   * @usage application
   */
  public boolean active() {
    return (thread != null);
  }


  /**
   *
   * Returns the IP address of the computer to which the Client is attached.
   *
   * @webref client
   * @usage application
   * @webBrief Returns the IP address of the machine as a <b>String</b>
   */
  public String ip() {
    if (socket != null){
      return socket.getInetAddress().getHostAddress();
    }
    return null;
  }


  /**
   *
   * Returns the number of bytes available. When any client has bytes
   * available from the server, it returns the number of bytes.
   *
   * @webref client
   * @usage application
   * @webBrief Returns the number of bytes in the buffer waiting to be read
   */
  public int available() {
    synchronized (bufferLock) {
      return (bufferLast - bufferIndex);
    }
  }


  /**
   *
   * Empty the buffer, removes all the data stored there.
   *
   * @webref client
   * @usage application
   * @webBrief Clears the buffer
   */
  public void clear() {
    synchronized (bufferLock) {
      bufferLast = 0;
      bufferIndex = 0;
    }
  }


  /**
   *
   * Returns a number between 0 and 255 for the next byte that's waiting in
   * the buffer. Returns -1 if there is no byte, although this should be
   * avoided by first checking <b>available()</b> to see if any data is available.
   *
   * @webref client
   * @usage application
   * @webBrief Returns a value from the buffer
   */
  public int read() {
    synchronized (bufferLock) {
      if (bufferIndex == bufferLast) return -1;

      int outgoing = buffer[bufferIndex++] & 0xff;
      if (bufferIndex == bufferLast) {  // rewind
        bufferIndex = 0;
        bufferLast = 0;
      }
      return outgoing;
    }
  }


  /**
   *
   * Returns the next byte in the buffer as a char. Returns <b>-1</b> or
   * <b>0xffff</b> if nothing is there.
   *
   * @webref client
   * @usage application
   * @webBrief Returns the next byte in the buffer as a char
   */
  public char readChar() {
    synchronized (bufferLock) {
      if (bufferIndex == bufferLast) return (char) (-1);
      return (char) read();
    }
  }


  /**
   *
   * Reads a group of bytes from the buffer. The version with no parameters
   * returns a byte array of all data in the buffer. This is not efficient,
   * but is easy to use. The version with the <b>byteBuffer</b> parameter is
   * more memory and time efficient. It grabs the data in the buffer and puts
   * it into the byte array passed in and returns an int value for the number
   * of bytes read. If more bytes are available than can fit into the
   * <b>byteBuffer</b>, only those that fit are read.
   *
   * <h3>Advanced</h3>
   * Return a byte array of anything that's in the serial buffer.
   * Not particularly memory/speed efficient, because it creates
   * a byte array on each read, but it's easier to use than
   * readBytes(byte b[]) (see below).
   *
   * @webref client
   * @usage application
   * @webBrief Reads a group of bytes from the buffer
   */
  public byte[] readBytes() {
    synchronized (bufferLock) {
      if (bufferIndex == bufferLast) return null;

      int length = bufferLast - bufferIndex;
      byte[] outgoing = new byte[length];
      System.arraycopy(buffer, bufferIndex, outgoing, 0, length);

      bufferIndex = 0;  // rewind
      bufferLast = 0;
      return outgoing;
    }
  }


  /**
   * <h3>Advanced</h3>
   * Return a byte array of anything that's in the serial buffer
   * up to the specified maximum number of bytes.
   * Not particularly memory/speed efficient, because it creates
   * a byte array on each read, but it's easier to use than
   * readBytes(byte b[]) (see below).
   *
   * @param max the maximum number of bytes to read
   */
  public byte[] readBytes(int max) {
    synchronized (bufferLock) {
      if (bufferIndex == bufferLast) return null;

      int length = bufferLast - bufferIndex;
      if (length > max) length = max;
      byte[] outgoing = new byte[length];
      System.arraycopy(buffer, bufferIndex, outgoing, 0, length);

      bufferIndex += length;
      if (bufferIndex == bufferLast) {
        bufferIndex = 0;  // rewind
        bufferLast = 0;
      }

      return outgoing;
    }
  }


  /**
   * <h3>Advanced</h3>
   * Grab whatever is in the serial buffer, and stuff it into a
   * byte buffer passed in by the user. This is more memory/time
   * efficient than readBytes() returning a byte[] array.
   *
   * Returns an int for how many bytes were read. If more bytes
   * are available than can fit into the byte array, only those
   * that will fit are read.
   *
   * @param bytebuffer passed in byte array to be altered
   */
  public int readBytes(byte[] bytebuffer) {
    synchronized (bufferLock) {
      if (bufferIndex == bufferLast) return 0;

      int length = bufferLast - bufferIndex;
      if (length > bytebuffer.length) length = bytebuffer.length;
      System.arraycopy(buffer, bufferIndex, bytebuffer, 0, length);

      bufferIndex += length;
      if (bufferIndex == bufferLast) {
        bufferIndex = 0;  // rewind
        bufferLast = 0;
      }
      return length;
    }
  }


  /**
   *
   * Reads from the port into a buffer of bytes up to and including a
   * particular character. If the character isn't in the buffer, 'null' is
   * returned. The version with no <b>byteBuffer</b> parameter returns a byte
   * array of all data up to and including the <b>interesting</b> byte. This
   * is not efficient, but is easy to use. The version with the
   * <b>byteBuffer</b> parameter is more memory and time efficient. It grabs
   * the data in the buffer and puts it into the byte array passed in and
   * returns an int value for the number of bytes read. If the byte buffer is
   * not large enough, -1 is returned and an error is printed to the message
   * area. If nothing is in the buffer, 0 is returned.
   *
   * @webref client
   * @usage application
   * @webBrief Reads from the buffer of bytes up to and including a particular character
   * @param interesting character designated to mark the end of the data
   */
  public byte[] readBytesUntil(int interesting) {
    byte what = (byte)interesting;

    synchronized (bufferLock) {
      if (bufferIndex == bufferLast) return null;

      int found = -1;
      for (int k = bufferIndex; k < bufferLast; k++) {
        if (buffer[k] == what) {
          found = k;
          break;
        }
      }
      if (found == -1) return null;

      int length = found - bufferIndex + 1;
      byte[] outgoing = new byte[length];
      System.arraycopy(buffer, bufferIndex, outgoing, 0, length);

      bufferIndex += length;
      if (bufferIndex == bufferLast) {
        bufferIndex = 0; // rewind
        bufferLast = 0;
      }
      return outgoing;
    }
  }


  /**
   * <h3>Advanced</h3>
   * Reads from the serial port into a buffer of bytes until a
   * particular character. If the character isn't in the serial
   * buffer, then 'null' is returned.
   *
   * If outgoing[] is not big enough, then -1 is returned,
   *   and an error message is printed on the console.
   * If nothing is in the buffer, zero is returned.
   * If 'interesting' byte is not in the buffer, then 0 is returned.
   *
   * @param byteBuffer passed in byte array to be altered
   */
  public int readBytesUntil(int interesting, byte[] byteBuffer) {
    byte what = (byte)interesting;

    synchronized (bufferLock) {
      if (bufferIndex == bufferLast) return 0;

      int found = -1;
      for (int k = bufferIndex; k < bufferLast; k++) {
        if (buffer[k] == what) {
          found = k;
          break;
        }
      }
      if (found == -1) return 0;

      int length = found - bufferIndex + 1;
      if (length > byteBuffer.length) {
        System.err.println("readBytesUntil() byte buffer is" +
                           " too small for the " + length +
                           " bytes up to and including char " + interesting);
        return -1;
      }
      //byte outgoing[] = new byte[length];
      System.arraycopy(buffer, bufferIndex, byteBuffer, 0, length);

      bufferIndex += length;
      if (bufferIndex == bufferLast) {
        bufferIndex = 0;  // rewind
        bufferLast = 0;
      }
      return length;
    }
  }


  /**
   *
   * Returns the all the data from the buffer as a <b>String</b>.
   *
   * In 4.0 beta 3, changed to using UTF-8 as the encoding,
   * otherwise the behavior is platform-dependent.
   *
   * @webref client
   * @usage application
   * @webBrief Returns the buffer as a <b>String</b>
   */
  public String readString() {
    byte[] b = readBytes();
    if (b == null) {
      return null;
    }
    return new String(b, StandardCharsets.UTF_8);
  }


  /**
   *
   * Combination of <b>readBytesUntil()</b> and <b>readString()</b>. Returns
   * <b>null</b> if it doesn't find what you're looking for.
   *
   * <h3>Advanced</h3>
   * In 4.0 beta 3, changed to using UTF-8 as the encoding,
   * otherwise the behavior is platform-dependent.
   *
   * @webref client
   * @usage application
   * @webBrief Returns the buffer as a <b>String</b> up to and including a particular character
   * @param interesting character designated to mark the end of the data
   */
  public String readStringUntil(int interesting) {
    byte[] b = readBytesUntil(interesting);
    if (b == null) {
      return null;
    }
    return new String(b, StandardCharsets.UTF_8);
  }


  /**
   *
   * Writes data to a server specified when constructing the client, or writes
   * data to the specific client obtained from the Server <b>available()</b>
   * method.
   *
   * @webref client
   * @usage application
   * @webBrief  Writes <b>bytes</b>, <b>chars</b>, <b>ints</b>, <b>bytes[]</b>, <b>Strings</b>
   * @param data data to write
   */
  public void write(int data) {  // will also cover char
    try {
      output.write(data & 0xff);  // for good measure do the &
      output.flush();   // hmm, not sure if a good idea

    } catch (Exception e) {  // null pointer or serial port dead
      e.printStackTrace();
      stop();
    }
  }


  public void write(byte[] data) {
    try {
      output.write(data);
      output.flush();   // hmm, not sure if a good idea

    } catch (Exception e) {  // null pointer or serial port dead
      e.printStackTrace();
      stop();
    }
  }


  /**
   * In 4.0 beta 3, changed to using UTF-8 as the encoding,
   * otherwise the behavior is platform-dependent.
   */
  public void write(String data) {
    write(data.getBytes(StandardCharsets.UTF_8));
  }
}
