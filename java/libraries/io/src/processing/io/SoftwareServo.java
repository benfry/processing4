/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Copyright (c) The Processing Foundation 2015
  Hardware I/O library developed by Gottfried Haider

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

import processing.core.*;


/**
 * Opens an RC servo motor connected to a GPIO pin<br/>
 * </br>
 * This library uses timers to control RC servo motors by means of pulse width
 * modulation (PWM). While not as accurate as dedicated PWM hardware, it has
 * shown to be sufficient for many applications.<br/>
 * <br/>
 * Connect the signal wire (typically colored yellow) to any available GPIO pin
 * and control the servo's angle as shown in the example sketch.
 * 
 * @webref software_servo
 * @webBrief Opens an RC servo motor connected to a GPIO pin
 */
public class SoftwareServo {

  public static final int DEFAULT_MIN_PULSE = 544;
  public static final int DEFAULT_MAX_PULSE = 2400;

  protected int pin = -1;           // gpio number (-1 .. not attached)
  protected long handle = -1;       // native thread id (<0 .. not started)
  protected int period = 20000;     // 20 ms (50 Hz)
  protected int minPulse = 0;       // minimum pulse width in microseconds
  protected int maxPulse = 0;       // maximum pulse width in microseconds
  protected int pulse = 0;          // current pulse in microseconds


  /**
   *  Opens a servo motor
   *  @param parent typically use "this"
   *  @webref software_servo
   *  @webBrief Opens a servo motor
   */
  public SoftwareServo(PApplet parent) {
    NativeInterface.loadLibrary();
  }


  /**
   *  Closes a servo motor
   *  
   */
  public void close() {
    detach();
  }


  protected void finalize() throws Throwable {
    try {
      close();
    } finally {
      super.finalize();
    }
  }


  /**
   * Attaches a servo motor to a GPIO pin<br/>
   * <br/>
   * You must call this function before calling <b>write()</b>. Note that the servo motor
   * will only be instructed to move after the first time <b>write()</b> is called.<br/>
   * <br/>
   * The optional parameters <b>minPulse</b> and <b>maxPulse</b> control the minimum and maximum
   * pulse width durations. The default values, identical to those of Arduino's
   * Servo class, should be compatible with most servo motors.
   * 
   * @param pin GPIO pin
   * @webref software_servo
   * @webBrief Attaches a servo motor to a GPIO pin
   */
  public void attach(int pin) {
    detach();
    this.pin = pin;
    this.minPulse = DEFAULT_MIN_PULSE;
    this.maxPulse = DEFAULT_MAX_PULSE;
  }


  /**
   * Attaches a servo motor to a GPIO pin<br/>
   * <br/>
   * You must call this function before calling <b>write()</b>. Note that the servo motor
   * will only be instructed to move after the first time <b>write()</b> is called.<br/>
   * <br/>
   * The optional parameters minPulse and maxPulse control the minimum and maximum
   * pulse width durations. The default values, identical to those of Arduino's
   * Servo class, should be compatible with most servo motors.
   * 
   *  @param minPulse minimum pulse width in microseconds (default: 544, same as on Arduino)
   *  @param maxPulse maximum pulse width in microseconds (default: 2400, same as on Arduino)
   *  @webref software_servo
   */
  public void attach(int pin, int minPulse, int maxPulse) {
    detach();
    this.pin = pin;
    this.minPulse = minPulse;
    this.maxPulse = maxPulse;
  }


  /**
   * Moves a servo motor to a given orientation<br/>
   * <br/>
   * If you are using this class in combination with a continuous rotation servo,
   * different angles will result in the servo rotating forward or backward at
   * different speeds. For regular servo motors, this will instruct the servo to
   * rotate to and hold a specific angle.
   * 
   * @param angle angle in degrees (controls speed and direction on
   *              continuous-rotation servos)
   * @webref software_servo
   * @webBrief Moves a servo motor to a given orientation
   */
  public void write(float angle) {
    if (attached() == false) {
      System.err.println("You need to call attach(pin) before write(angle).");
      throw new RuntimeException("Servo is not attached");
    }

    if (angle < 0 || 180 < angle) {
      System.err.println("Only degree values between 0 and 180 can be used.");
      throw new IllegalArgumentException("Illegal value");
    }
    pulse = (int)(minPulse + (angle/180.0) * (maxPulse-minPulse));

    if (handle < 0) {
      // start a new thread
      GPIO.pinMode(pin, GPIO.OUTPUT);
      if (NativeInterface.isSimulated()) {
        return;
      }
      handle = NativeInterface.servoStartThread(pin, pulse, period);
      if (handle < 0) {
        throw new RuntimeException(NativeInterface.getError((int)handle));
      }
    } else {
      // thread already running
      int ret = NativeInterface.servoUpdateThread(handle, pulse, period);
      if (ret < 0) {
        throw new RuntimeException(NativeInterface.getError(ret));
      }
    }
  }


  /**
   *  Returns whether a servo motor is attached to a pin
   *  @return true if attached, false is not
   *  @webref software_servo
   *  @webBrief Returns whether a servo motor is attached to a pin
   */
  public boolean attached() {
    return (pin != -1);
  }


  /**
   * Detatches a servo motor from a GPIO pin<br/>
   * <br/>
   * Calling this method will stop the servo from moving or trying to hold the
   * current orientation.
   * 
   * @webref software_servo
   * @webBrief Detatches a servo motor from a GPIO pin
   */
  public void detach() {
    if (0 <= handle) {
      // stop thread
      int ret = NativeInterface.servoStopThread(handle);
      GPIO.pinMode(pin, GPIO.INPUT);
      handle = -1;
      pin = -1;
      if (ret < 0) {
        throw new RuntimeException(NativeInterface.getError(ret));
      }
    }
  }
}
