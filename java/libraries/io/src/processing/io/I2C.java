/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Copyright (c) The Processing Foundation 2015
  Hardware I/O library developed by Gottfried Haider as part of GSoC 2015

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

package processing.io;

import processing.io.NativeInterface;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;


/**
 * Opens an I2C interface as master.<br/>
 * <br/>
 * I2C is a serial bus, commonly used to attach peripheral ICs (Integrated
 * Circuits) to processors and microcontrollers. It uses two pins, SDA (for
 * data) and SDL (for the clock signal). Multiple "slave" devices can be
 * connected to the same bus, as long as they are responding to different
 * addresses (see below).<br/>
 * <br/>
 * The I2C "master" device initiates a transmission, which includes sending the
 * address of the desired "slave" device. I2C addresses consist of 7 bits plus
 * one bit that indicates whether the device is being read from or written to.
 * Some datasheets list the address in an 8 bit form (7 address bits + R/W bit),
 * while others provide the address in a 7 bit form, with the address in the
 * lower 7 bits.<br/>
 * <br/>
 * This library expects addresses in their 7 bit form, similar to Arduino's Wire
 * library, and what is being output by the i2cdetect utility on Linux. If the
 * address provided in a datasheet is greater than 127 (hex 0x7f) or there are
 * separate addresses for read and write operations listed, which vary exactly
 * by one, then you want to shift the this number by one bit to the right before
 * passing it as an argument to
 * <a href="I2C_beginTransmission_.html">beginTransmission()</a>.
 * 
 * @webref I2C
 * @webBrief Opens an I2C interface as master
 */
public class I2C {

  protected String dev;
  protected int handle;
  protected int slave;
  protected byte[] out;
  protected boolean transmitting;


  /**
   *  Opens an I2C interface as master
   *  @param dev interface name
   *  @see list
   *  @webref I2C
   */
  public I2C(String dev) {
    NativeInterface.loadLibrary();
    this.dev = dev;

    if (NativeInterface.isSimulated()) {
      return;
    }

    handle = NativeInterface.openDevice("/dev/" + dev);
    if (handle < 0) {
      throw new RuntimeException(NativeInterface.getError(handle));
    }
  }


  /**
   * Begins a transmission to an attached device.<br/>
   * <br/>
   * This function expects the address in the lower 7 bits, the same way as in
   * Arduino's Wire library, and as shown in the output of the i2cdetect tool. If
   * the address provided in a datasheet is greater than 127 (hex 0x7f) or there
   * are separate addresses for read and write operations listed, which vary
   * exactly by one, then you want to shift the this number by one bit to the
   * right before passing it as an argument to this function.
   * 
   * @see write
   * @see read
   * @see endTransmission
   * @webref I2C
   * @webBrief Begins a transmission to an attached device
   */
  public void beginTransmission(int slave) {
    // addresses 120 (0x78) to 127 are additionally reserved
    if (0x78 <= slave) {
      System.err.println("beginTransmission expects a 7 bit address, try shifting one bit to the right");
      throw new IllegalArgumentException("Illegal address");
    }
    this.slave = slave;
    transmitting = true;
    out = null;
  }


  /**
   * Closes the I2C device<br/>
   * </br>
   * It is normally not necessary to explicitly close I2C interfaces, as they are
   * closed automatically by the operating system when the sketch exits.</br>
   * </br>
   * Note: It is possible to have two or more object using the same interface at a
   * time.
   * 
   * @webref I2C
   * @webBrief Closes the I2C device
   */
  public void close() {
    if (NativeInterface.isSimulated()) {
      return;
    }

    NativeInterface.closeDevice(handle);
    handle = 0;
  }


  protected void finalize() throws Throwable {
    try {
      close();
    } finally {
      super.finalize();
    }
  }


  /**
   * Ends the current transmissions<br/>
   * <br/>
   * This executes any queued writes. <a href="I2C_read_.html">Read()</a>
   * implicitly ends the current transmission as well, hence calling
   * <b>endTransmission()</b> afterwards is not necessary.
   * 
   * @see beginTransmission
   * @see write
   * @webref I2C
   * @webBrief Ends the current transmissions
   */
  public void endTransmission() {
    if (!transmitting) {
      // silently ignore this case
      return;
    }

    if (NativeInterface.isSimulated()) {
      return;
    }

    // implement these flags if needed: https://github.com/raspberrypi/linux/blob/rpi-patches/Documentation/i2c/i2c-protocol
    int ret = NativeInterface.transferI2c(handle, slave, out, null);
    transmitting = false;
    out = null;
    if (ret < 0) {
      if (ret == -5 | ret == -121) {    // EIO | EREMOTEIO
        System.err.println("The device did not respond. Check the cabling and whether you are using the correct address.");
      }
      throw new RuntimeException(NativeInterface.getError(ret));
    }
  }


  /**
   *  Lists all available I2C interfaces
   *  @return String array
   *  @webref I2C
   *  @webBrief Lists all available I2C interfaces
   */
  public static String[] list() {
    if (NativeInterface.isSimulated()) {
      // as on the Raspberry Pi
      return new String[]{ "i2c-1" };
    }

    ArrayList<String> devs = new ArrayList<String>();
    File dir = new File("/dev");
    File[] files = dir.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.getName().startsWith("i2c-")) {
          devs.add(file.getName());
        }
      }
    }
    // listFiles() does not guarantee ordering
    String[] tmp = devs.toArray(new String[devs.size()]);
    Arrays.sort(tmp);
    return tmp;
  }


  /**
   * Read bytes from the attached device<br/>
   * <br/>
   * You must call <b>beginTransmission()</b> before calling this function. This function
   * also ends the current transmission and sends any data that was queued using
   * <b>write()</b> before. It is not necessary to call
   * <a href="I2C_endTransmission_.html">endTransmission()</a> after <b>read()</b>.
   * 
   * @param len number of bytes to read
   * @return bytes read from device
   * @see beginTransmission
   * @see write
   * @see endTransmission
   * @webref I2C
   * @webBrief Read bytes from the attached device
   */
  public byte[] read(int len) {
    if (!transmitting) {
      throw new RuntimeException("beginTransmisson has not been called");
    }

    byte[] in = new byte[len];

    if (NativeInterface.isSimulated()) {
      return in;
    }

    int ret = NativeInterface.transferI2c(handle, slave, out, in);
    transmitting = false;
    out = null;
    if (ret < 0) {
      if (ret == -5 | ret == -121) {    // EIO | EREMOTEIO
        System.err.println("The device did not respond. Check the cabling and whether you are using the correct address.");
      }
      throw new RuntimeException(NativeInterface.getError(ret));
    }

    return in;
  }


  /**
   * Add bytes to be written to the device<br/>
   * <br/>
   * You must call <b>beginTransmission()</b> before calling this function. The actual
   * writing takes part when <b>read()</b> or <b>endTransmission()</b> is being called.
   * 
   * @param out bytes to be written
   * @see beginTransmission
   * @see read
   * @see endTransmission
   * @webref I2C
   * @webBrief Add bytes to be written to the device
   */
  public void write(byte[] out) {
    if (!transmitting) {
      throw new RuntimeException("beginTransmisson has not been called");
    }

    if (this.out == null) {
      this.out = out;
    } else {
      byte[] tmp = new byte[this.out.length + out.length];
      System.arraycopy(this.out, 0, tmp, 0, this.out.length);
      System.arraycopy(out, 0, tmp, this.out.length, out.length);
      this.out = tmp;
    }
  }


  /**
   *  Adds bytes to be written to the attached device
   *  @param out string to be written
   *  @see beginTransmission
   *  @see read
   *  @see endTransmission
   */
  public void write(String out) {
    write(out.getBytes());
  }


  /**
   *  Adds a byte to be written to the attached device
   *  @param out single byte to be written, e.g. numeric literal (0 to 255, or -128 to 127)
   *  @see beginTransmission
   *  @see read
   *  @see endTransmission
   */
  public void write(int out) {
    if (out < -128 || 255 < out) {
      System.err.println("The write function can only operate on a single byte at a time. Call it with a value from 0 to 255, or -128 to 127.");
      throw new RuntimeException("Argument does not fit into a single byte");
    }
    byte[] tmp = new byte[1];
    tmp[0] = (byte)out;
    write(tmp);
  }

  /**
   *  Adds a byte to be written to the attached device
   *  @param out single byte to be written
   *  @see beginTransmission
   *  @see read
   *  @see endTransmission
   */
  public void write(byte out) {
    // cast to (unsigned) int
    write(out & 0xff);
  }
}
