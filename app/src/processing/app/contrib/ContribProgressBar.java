/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2013-22 The Processing Foundation
  Copyright (c) 2011-12 Ben Fry and Casey Reas

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License version 2
  as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License along
  with this program; if not, write to the Free Software Foundation, Inc.
  59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package processing.app.contrib;

import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JProgressBar;


// I suspect this code can mostly be replaced with built-in Swing functions.
// This code seems like it's adapted from old example code found on the web.
// https://github.com/processing/processing4/issues/351

public class ContribProgressBar {
  static private final int UNKNOWN = -1;

  JProgressBar progressBar;

  int progress = 0;
  int max;

  boolean finished = false;
  boolean canceled = false;
  boolean error = false;
  Exception exception;


  public ContribProgressBar(JProgressBar progressBar) {
    this.progressBar = progressBar;
  }


  public void startTask(String name) {
    startTask(name, UNKNOWN);
  }


  public void startTask(String name, int maxValue) {
    finished = false;

    if (progressBar != null) {
      progressBar.setString(name);
      progressBar.setIndeterminate(maxValue == UNKNOWN);
      progressBar.setMaximum(maxValue);
    }
  }


  public void setProgress(int value) {
    progress = value;
    if (progressBar != null) {
      progressBar.setValue(value);
    }
  }


  public final void finished() {
    finished = true;
    try {
      EventQueue.invokeAndWait(this::finishedAction);
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      } else {
        cause.printStackTrace();
      }
    }
  }


  public void finishedAction() { }


  public boolean isCanceled() {
    return canceled;
  }


  public final void cancel() {
    canceled = true;
    try {
      EventQueue.invokeAndWait(this::cancelAction);
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      } else {
        cause.printStackTrace();
      }
    }
  }


  public void cancelAction() { }


  public boolean isError() {
    return error;
  }


  public void error(Exception e) {
    error = true;
    exception = e;
  }
}
