/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2013-23 The Processing Foundation
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
// (As of 220311 it's also been merged together from two classes (one called
// ProgressMonitor, the other ProgressBar, which wrapped the JProgressBar),
// so it looks less like that now. But the point is still relevant: most of
// what's here should be done with Swing housekeeping classes. [fry 220312]
// TODO https://github.com/processing/processing4/issues/351

public class ContribProgress {
  static private final int UNKNOWN = -1;

  final private JProgressBar progressBar;

  private boolean canceled = false;
  private Exception exception;


  public ContribProgress(JProgressBar progressBar) {
    this.progressBar = progressBar;
  }


  public void startTask(String name) {
    startTask(name, UNKNOWN);
  }


  public void startTask(String name, int maxValue) {
    if (progressBar != null) {
      progressBar.setString(name);
      progressBar.setIndeterminate(maxValue == UNKNOWN);
      progressBar.setMaximum(maxValue);
    }
  }


  public void setProgress(int value) {
    if (progressBar != null) {
      progressBar.setValue(value);
    }
  }


  public final void finished() {
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


  public boolean notCanceled() {
    return !canceled;
  }


  public void setException(Exception e) {
    exception = e;
  }


  public Exception getException() {
    return exception;
  }


  public boolean isException() {
    return exception != null;
  }
}
