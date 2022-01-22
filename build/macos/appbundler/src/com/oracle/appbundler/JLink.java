/*
 * Copyright 2019, ATTO Technology, Inc. All rights reserved.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  ATTO Technology designates this
 * particular file as subject to the "Classpath" exception as provided
 * by ATTO Technology in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package com.oracle.appbundler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.ExecTask;

/**
 * Class representing a module that will be passed to jlink to build the bundled
 * JVM.
 */
public class JLink {
    private String runtime = null;
    private ArrayList<String> jmods = new ArrayList<>();
    private ArrayList<String> arguments = new ArrayList<>();
    private ExecTask exec = new ExecTask();

    public JLink() {
        exec.init();
    }

    public String getRuntime() {
        return runtime;
    }

    public void setRuntime(String runtime) {
        this.runtime = runtime;
    }

    public void setTask(AppBundlerTask task) {
        exec.bindToOwner(task);
    }

    /* Provide canonical path so that runtime can be specified via a
     * version-agnostic path (relative link, e.g. `current-jre`) while
     * still preserving the original runtime directory name, e.g.
     * `jre1.8.0_45.jre`.
     */
    public File getDir() {
        File dir = new File(runtime);
        try {
            return dir.getCanonicalFile();
        } catch (IOException e) {
            return dir;
        }
    }

    public void addConfiguredJMod(JMod jmod) throws BuildException {
        String name = jmod.getName();

        if (name == null) {
            throw new BuildException("Name is required.");
        }

        jmods.add(name);
    }

    public void addConfiguredArgument(Argument argument) throws BuildException {
        String value = argument.getValue();

        if (value == null) {
            throw new BuildException("Value is required.");
        }

        arguments.add(value);
    }

    public void copyTo(File targetDir) throws IOException {
        File runtimeHomeDirectory = getDir();
        File runtimeContentsDirectory = runtimeHomeDirectory.getParentFile();
        File runtimeDirectory = runtimeContentsDirectory.getParentFile();

        // Create root plug-in directory
        File pluginDirectory = new File(targetDir, runtimeDirectory.getName());
        pluginDirectory.mkdir();

        // Create Contents directory
        File pluginContentsDirectory = new File(pluginDirectory, runtimeContentsDirectory.getName());
        pluginContentsDirectory.mkdir();

        // Copy MacOS directory
        File runtimeMacOSDirectory = new File(runtimeContentsDirectory, "MacOS");
        AppBundlerTask.copy(runtimeMacOSDirectory, new File(pluginContentsDirectory, runtimeMacOSDirectory.getName()));


        // Copy Info.plist file
        File runtimeInfoPlistFile = new File(runtimeContentsDirectory, "Info.plist");
        AppBundlerTask.copy(runtimeInfoPlistFile, new File(pluginContentsDirectory, runtimeInfoPlistFile.getName()));

        // Copy included contents of Home directory
        File pluginHomeDirectory = new File(pluginContentsDirectory, runtimeHomeDirectory.getName());

        exec.setExecutable(runtimeHomeDirectory.getAbsolutePath() + "/bin/jlink");
        exec.setFailIfExecutionFails(true);
        exec.setFailonerror(true);
        for(String s : this.arguments) {
            exec.createArg().setValue(s);
        }

        exec.createArg().setValue("--no-man-pages");
        exec.createArg().setValue("--no-header-files");
        exec.createArg().setValue("--strip-native-commands");    /* no bin directory */
        exec.createArg().setValue("--add-modules");
        exec.createArg().setValue(String.join(",", jmods));
        exec.createArg().setValue("--output");
        exec.createArg().setValue(pluginHomeDirectory.getAbsolutePath());

        exec.execute();
    }

    @Override
    public String toString() {
        return runtime;
    }
}
