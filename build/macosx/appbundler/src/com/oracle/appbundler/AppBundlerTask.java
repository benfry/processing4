/*
 * Copyright 2012, Oracle and/or its affiliates. All rights reserved.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.appbundler;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Reference;
import org.apache.tools.ant.types.resources.FileResource;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * App bundler Ant task.
 */
public class AppBundlerTask extends Task {
    // Output folder for generated bundle
    private File outputDirectory = null;

    // General bundle properties
    private String name = null;
    private String displayName = null;
    private String identifier = null;
    private File icon = null;
    private String executableName = EXECUTABLE_NAME;

    private String shortVersion = "1.0";
    private String version = "1.0";
    private String signature = "????";
    private String copyright = "";
    private String privileged = null;
    private String workingDirectory = null;
    private String minimumSystemVersion = "10.7";

    private boolean requiresAquaAppearance = false;
    private String jvmRequired = null;
    private boolean jrePreferred = false;
    private boolean jdkPreferred = false;

    private String applicationCategory = null;

    private boolean highResolutionCapable = true;
    private boolean supportsAutomaticGraphicsSwitching = true;
    private boolean hideDockIcon = false;
    private boolean isDebug = false;
    private boolean ignorePSN = false;

    // JVM info properties
    private String mainClassName = null;
    private String jnlpLauncherName = null;
    private String jarLauncherName = null;
    private Runtime runtime = null;
    private JLink jlink = null;
    private ArrayList<FileSet> classPath = new ArrayList<>();
    private ArrayList<FileSet> libraryPath = new ArrayList<>();
    private ArrayList<Option> options = new ArrayList<>();
    private ArrayList<String> arguments = new ArrayList<>();
    private ArrayList<String> architectures = new ArrayList<>();
    private ArrayList<String> registeredProtocols = new ArrayList<>();
    private ArrayList<BundleDocument> bundleDocuments = new ArrayList<>();
    private ArrayList<TypeDeclaration> exportedTypeDeclarations = new ArrayList<>();
    private ArrayList<TypeDeclaration> importedTypeDeclarations = new ArrayList<>();
    private ArrayList<PlistEntry> plistEntries = new ArrayList<>();
    private ArrayList<Environment> environments = new ArrayList<>();

    private Reference classPathRef;
    private ArrayList<String> plistClassPaths = new ArrayList<>();

    private static final String EXECUTABLE_NAME = "JavaAppLauncher";
    private static final String DEFAULT_ICON_NAME = "GenericApp.icns";
    private static final String OS_TYPE_CODE = "APPL";

    private static final String PLIST_DTD = "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">";
    private static final String PLIST_TAG = "plist";
    private static final String PLIST_VERSION_ATTRIBUTE = "version";
    private static final String DICT_TAG = "dict";
    private static final String KEY_TAG = "key";
    private static final String ARRAY_TAG = "array";
    private static final String STRING_TAG = "string";

    private static final int BUFFER_SIZE = 2048;

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public void setIcon(File icon) {
        this.icon = icon;
    }

    public void setExecutableName(String executable) {
        this.executableName = executable;
    }

    public void setShortVersion(String shortVersion) {
        this.shortVersion = shortVersion;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public void setCopyright(String copyright) {
        this.copyright = copyright;
    }

    public void setPrivileged(String privileged) {
        this.privileged = privileged;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public void setJVMRequired(String v){
        this.jvmRequired = v;
    }

    public void setJREPreferred(boolean preferred){
        this.jrePreferred = preferred;
    }

    public void setJDKPreferred(boolean preferred){
        this.jdkPreferred = preferred;
    }

    public void setMinimumSystemVersion(String v){
        this.minimumSystemVersion = v;
    }

    public void setApplicationCategory(String applicationCategory) {
        this.applicationCategory = applicationCategory;
    }

    public void setHighResolutionCapable(boolean highResolutionCapable) {
        this.highResolutionCapable = highResolutionCapable;
    }

    public void setHideDockIcon(boolean hideDock) {
        this.hideDockIcon = hideDock;
    }

    public void setDebug(boolean enabled) {
        this.isDebug = enabled;
    }

    public void setSupportsAutomaticGraphicsSwitching(boolean supportsAutomaticGraphicsSwitching) {
        this.supportsAutomaticGraphicsSwitching = supportsAutomaticGraphicsSwitching;
    }

    public void setIgnorePSN(boolean ignorePSN) {
        this.ignorePSN = ignorePSN;
    }

    public void setMainClassName(String mainClassName) {
        this.mainClassName = mainClassName;
    }

    public void setJnlpLauncherName(String jnlpLauncherName) {
        this.jnlpLauncherName = jnlpLauncherName;
    }

    public void setJarLauncherName(String jarLauncherName) {
        this.jarLauncherName = jarLauncherName;
    }

    public void addConfiguredRuntime(Runtime runtime) throws BuildException {
        if (this.runtime != null) {
            throw new BuildException("Runtime already specified.");
        }

        if (this.jlink != null) {
            throw new BuildException("Cannot specify runtime and jlink together.");
        }

        this.runtime = runtime;
    }

    public void addConfiguredJLink(JLink jlink) throws BuildException {
        if (this.jlink != null) {
            throw new BuildException("JLink already specified.");
        }

        if (this.runtime != null) {
            throw new BuildException("Cannot specify runtime and jlink together.");
        }

        jlink.setTask(this);
        this.jlink = jlink;
    }

    public void setClasspathRef(Reference ref) {

        this.classPathRef = ref;
    }

    public void setPlistClassPaths(String plistClassPaths) {
        for (String tok : plistClassPaths.split("\\s*,\\s*")) {
            this.plistClassPaths.add(tok);
        }
    }

    public void addConfiguredClassPath(FileSet classPath) {
        this.classPath.add(classPath);
    }

    public void addConfiguredLibraryPath(FileSet libraryPath) {
        this.libraryPath.add(libraryPath);
    }

    public void addConfiguredBundleDocument(BundleDocument document) {
        if ((document.getContentTypes() == null) && (document.getExtensions() == null)) {
            throw new BuildException("Document content type or extension is required.");
        }
        this.bundleDocuments.add(document);
    }

    public void addConfiguredTypeDeclaration(TypeDeclaration typeDeclaration) {
        if (typeDeclaration.getIdentifier() == null) {
            throw new BuildException("Type declarations must have an identifier.");
        }
        if (typeDeclaration.isImported()) {
            this.importedTypeDeclarations.add(typeDeclaration);
        } else {
            this.exportedTypeDeclarations.add(typeDeclaration);
        }
    }

    public void addConfiguredPlistEntry(PlistEntry plistEntry) {
        if (plistEntry.getKey() == null) {
            throw new BuildException("Name is required.");
        }
        if (plistEntry.getValue() == null) {
            throw new BuildException("Value is required.");
        }
        if (plistEntry.getType() == null) {
            plistEntry.setType(STRING_TAG);
        }

        this.plistEntries.add(plistEntry);
    }

    public void addConfiguredEnvironment(Environment environment) {
        if (environment.getName() == null) {
            throw new BuildException("Name is required.");
        }
        if (environment.getValue() == null) {
            throw new BuildException("Value is required.");
        }

        this.environments.add(environment);
    }

    public void addConfiguredOption(Option option) throws BuildException {
        String value = option.getValue();

        if (value == null) {
            throw new BuildException("Value is required.");
        }

        options.add(option);
    }

    public void addConfiguredArgument(Argument argument) throws BuildException {
        String value = argument.getValue();

        if (value == null) {
            throw new BuildException("Value is required.");
        }

        arguments.add(value);
    }
    public void addConfiguredScheme(Argument argument) throws BuildException {
        String value = argument.getValue();

        if (value == null) {
            throw new BuildException("Value is required.");
        }

        this.registeredProtocols.add(value);
    }

    public void addConfiguredArch(Architecture architecture) throws BuildException {
        String name = architecture.getName();

        if (name == null) {
            throw new BuildException("Name is required.");
        }

        architectures.add(name);

    }

    @Override
    public void execute() throws BuildException {
        // Validate required properties
        if (outputDirectory == null) {
            throw new IllegalStateException("Output directory is required.");
        }

        if (!outputDirectory.exists()) {
            throw new IllegalStateException("Output directory does not exist.");
        }

        if (!outputDirectory.isDirectory()) {
            throw new IllegalStateException("Invalid output directory.");
        }

        if (name == null) {
            throw new IllegalStateException("Name is required.");
        }

        if (displayName == null) {
            throw new IllegalStateException("Display name is required.");
        }

        if (identifier == null) {
            throw new IllegalStateException("Identifier is required.");
        }

        if (icon != null) {
            if (!icon.exists()) {
                throw new IllegalStateException("Icon does not exist.");
            }

            if (icon.isDirectory()) {
                throw new IllegalStateException("Invalid icon.");
            }
        }

        if (shortVersion == null) {
            throw new IllegalStateException("Short version is required.");
        }

        if (signature == null || signature.trim().length() != 4) {
            throw new IllegalStateException("Invalid or missing signature.");
        }

        if (copyright == null) {
            throw new IllegalStateException("Copyright is required.");
        }

        if (jnlpLauncherName == null && mainClassName == null) {
            throw new IllegalStateException("Main class name or JNLP launcher name is required.");
        }

        // Create the app bundle
        try {
            System.out.println("Creating app bundle: " + name);

            // Create directory structure
            File rootDirectory = new File(outputDirectory, name + ".app");
            delete(rootDirectory);
            rootDirectory.mkdir();

            File contentsDirectory = new File(rootDirectory, "Contents");
            contentsDirectory.mkdir();

            File macOSDirectory = new File(contentsDirectory, "MacOS");
            macOSDirectory.mkdir();

            File javaDirectory = new File(contentsDirectory, "Java");
            javaDirectory.mkdir();

            File plugInsDirectory = new File(contentsDirectory, "PlugIns");
            plugInsDirectory.mkdir();

            File resourcesDirectory = new File(contentsDirectory, "Resources");
            resourcesDirectory.mkdir();

            // Generate Info.plist
            File infoPlistFile = new File(contentsDirectory, "Info.plist");
            infoPlistFile.createNewFile();
            writeInfoPlist(infoPlistFile);

            // Generate PkgInfo
            File pkgInfoFile = new File(contentsDirectory, "PkgInfo");
            pkgInfoFile.createNewFile();
            writePkgInfo(pkgInfoFile);

            // Copy executable to MacOS folder
            File executableFile = new File(macOSDirectory, executableName);
            copy(getClass().getResource(EXECUTABLE_NAME), executableFile);

            executableFile.setExecutable(true, false);

            // Copy localized resources to Resources folder
            copyResources(resourcesDirectory);

            // Copy runtime to PlugIns folder
            copyRuntime(plugInsDirectory);

            // Copy class path entries to Java folder
            copyClassPathEntries(javaDirectory);

            // Copy class path ref entries to Java folder
            copyClassPathRefEntries(javaDirectory);

            // Copy library path entries to MacOS folder
            copyLibraryPathEntries(macOSDirectory);

            // Copy app icon to Resources folder
            copyIcon(resourcesDirectory);

            // Copy app document icons to Resources folder
            copyDocumentIcons(bundleDocuments, resourcesDirectory);
            copyDocumentIcons(exportedTypeDeclarations, resourcesDirectory);
            copyDocumentIcons(importedTypeDeclarations, resourcesDirectory);

        } catch (IOException exception) {
            throw new BuildException(exception);
        }
    }

    private void copyResources(File resourcesDirectory) throws IOException {
        // Unzip res.zip into resources directory
        InputStream inputStream = getClass().getResourceAsStream("res.zip");
        ZipInputStream zipInputStream = new ZipInputStream(inputStream);

        try {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                File file = new File(resourcesDirectory, zipEntry.getName());

                if (zipEntry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file), BUFFER_SIZE);

                    try {
                        int b = zipInputStream.read();
                        while (b != -1) {
                            outputStream.write(b);
                            b = zipInputStream.read();
                        }

                        outputStream.flush();
                    } finally {
                        outputStream.close();
                    }

                }

                zipEntry = zipInputStream.getNextEntry();
            }
        } finally {
            zipInputStream.close();
        }
    }

    private void copyRuntime(File plugInsDirectory) throws IOException {
        if (runtime != null) {
            runtime.copyTo(plugInsDirectory);
        } else if (jlink != null) {
            jlink.copyTo(plugInsDirectory);
        }
    }

    private void copyClassPathRefEntries(File javaDirectory) throws IOException {
        if (classPathRef != null) {
          org.apache.tools.ant.types.Path classpath =
            (org.apache.tools.ant.types.Path) classPathRef.getReferencedObject(getProject());
            for (Object resource : classpath) {
                if (resource instanceof FileResource) {
                    FileResource fileResource = (FileResource) resource;
                    File source = fileResource.getFile();
                    File destination = new File(javaDirectory, source.getName());
                    copy(source, destination);
                }
            }
        }
    }

    private void copyClassPathEntries(File javaDirectory) throws IOException {
        for (FileSet fileSet : classPath) {
            File classPathDirectory = fileSet.getDir();
            DirectoryScanner directoryScanner = fileSet.getDirectoryScanner(getProject());
            String[] includedFiles = directoryScanner.getIncludedFiles();

            for (String includedFile : includedFiles) {
                File source = new File(classPathDirectory, includedFile);
                File destination = new File(javaDirectory, new File(includedFile).getName());
                copy(source, destination);
            }
        }
    }

    private void copyLibraryPathEntries(File macOSDirectory) throws IOException {
        for (FileSet fileSet : libraryPath) {
            File libraryPathDirectory = fileSet.getDir();
            DirectoryScanner directoryScanner = fileSet.getDirectoryScanner(getProject());
            String[] includedFiles = directoryScanner.getIncludedFiles();

            for (int i = 0; i < includedFiles.length; i++) {
                String includedFile = includedFiles[i];
                File source = new File(libraryPathDirectory, includedFile);
                File destination = new File(macOSDirectory, new File(includedFile).getName());
                copy(source, destination);
            }
        }
    }

    private void copyIcon(File resourcesDirectory) throws IOException {
        if (icon == null) {
            copy(getClass().getResource(DEFAULT_ICON_NAME), new File(resourcesDirectory, DEFAULT_ICON_NAME));
        } else {
            copy(icon, new File(resourcesDirectory, icon.getName()));
        }
    }

    public void copyDocumentIcons(final ArrayList<? extends IconContainer> iconContainers,
            File resourcesDirectory) throws IOException {
        for(IconContainer iconContainer: iconContainers) {
            if(iconContainer.hasIcon()) {
                File ifile = iconContainer.getIconFile();
                if (ifile != null) {
                    copyDocumentIcon(ifile,resourcesDirectory);
                }
            }
        }
    }

    private void copyDocumentIcon(File ifile, File resourcesDirectory) throws IOException {
        if (ifile == null) {
            return;
        } else {
            copy(ifile, new File(resourcesDirectory, ifile.getName()));
        }
    }

    private void writeInfoPlist(File file) throws IOException {
        Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), UTF_8));
        XMLOutputFactory output = XMLOutputFactory.newInstance();

        try {
            XMLStreamWriter xout = output.createXMLStreamWriter(out);

            // Write XML declaration
            xout.writeStartDocument();
            xout.writeCharacters("\n");

            // Write plist DTD declaration
            xout.writeDTD(PLIST_DTD);
            xout.writeCharacters("\n");

            // Begin root element
            xout.writeStartElement(PLIST_TAG);
            xout.writeAttribute(PLIST_VERSION_ATTRIBUTE, "1.0");
            xout.writeCharacters("\n");

            // Begin root dictionary
            writeIndentation(xout, 1);
            xout.writeStartElement(DICT_TAG);
            xout.writeCharacters("\n");

            // Write bundle properties
            writeProperty(xout, "CFBundleDevelopmentRegion", "English", 2);
            writeProperty(xout, "CFBundleExecutable", executableName, 2);
            writeProperty(xout, "CFBundleIconFile", (icon == null) ? DEFAULT_ICON_NAME : icon.getName(), 2);
            writeProperty(xout, "CFBundleIdentifier", identifier, 2);
            writeProperty(xout, "CFBundleDisplayName", displayName, 2);
            writeProperty(xout, "CFBundleInfoDictionaryVersion", "6.0", 2);
            writeProperty(xout, "CFBundleName", name, 2);
            writeProperty(xout, "CFBundlePackageType", OS_TYPE_CODE, 2);
            writeProperty(xout, "CFBundleShortVersionString", shortVersion, 2);
            writeProperty(xout, "CFBundleVersion", version, 2);
            writeProperty(xout, "CFBundleSignature", signature, 2);
            writeProperty(xout, "NSHumanReadableCopyright", copyright, 2);
            writeProperty(xout, "LSMinimumSystemVersion", minimumSystemVersion, 2);
            writeProperty(xout, "LSApplicationCategoryType", applicationCategory, 2);
            writeProperty(xout, "LSUIElement", hideDockIcon, 2);
            writeProperty(xout, "NSHighResolutionCapable", highResolutionCapable, 2);
            writeProperty(xout, "NSSupportsAutomaticGraphicsSwitching", supportsAutomaticGraphicsSwitching, 2);
            writeProperty(xout, "IgnorePSN", ignorePSN, 2);

            writeProperty(xout, "NSRequiresAquaSystemAppearance", requiresAquaAppearance, 2);
            
            if(registeredProtocols.size() > 0){
                writeKey(xout, "CFBundleURLTypes", 2);
                writeIndentation(xout, 2);
                xout.writeStartElement(ARRAY_TAG);
                xout.writeCharacters("\n");
                writeIndentation(xout, 3);
                xout.writeStartElement(DICT_TAG);
                xout.writeCharacters("\n");

                writeProperty(xout, "CFBundleURLName", identifier, 4);
                writeStringArray(xout, "CFBundleURLSchemes",registeredProtocols, 4);

                writeIndentation(xout, 3);
                xout.writeEndElement();
                xout.writeCharacters("\n");
                writeIndentation(xout, 2);
                xout.writeEndElement();
                xout.writeCharacters("\n");
            }

            // Write runtime
            if (runtime != null) {
                writeProperty(xout, "JVMRuntime", runtime.getDir().getParentFile().getParentFile().getName(), 2);
            } else if (jlink != null) {
                writeProperty(xout, "JVMRuntime", jlink.getDir().getParentFile().getParentFile().getName(), 2);
            }

            if(jvmRequired != null) {
                writeProperty(xout, "JVMVersion", jvmRequired, 2);
            }

            writeProperty(xout, "JVMRunPrivileged", privileged, 2);

            writeProperty(xout, "JREPreferred", jrePreferred, 2);
            writeProperty(xout, "JDKPreferred", jdkPreferred, 2);

            writeProperty(xout, "WorkingDirectory", workingDirectory, 2);

            // Write jnlp launcher name - only if set
            writeProperty(xout, "JVMJNLPLauncher", jnlpLauncherName, 2);

            // Write main class name - only if set. There should only one be set
            writeProperty(xout, "JVMMainClassName", mainClassName, 2);

           // Write classpaths in plist, if specified
            if (!plistClassPaths.isEmpty()) {
                writeStringArray(xout,"JVMClassPath", plistClassPaths, 2);
            }

            // Write whether launcher be verbose with debug msgs
            writeProperty(xout, "JVMDebug", isDebug, 2);

            // Write jar launcher name
            writeProperty(xout, "JVMJARLauncher", jarLauncherName, 2);

            // Write CFBundleDocument entries
            writeKey(xout, "CFBundleDocumentTypes", 2);
            writeBundleDocuments(xout, bundleDocuments, 2);

            // Write Type Declarations
            if (! exportedTypeDeclarations.isEmpty()) {
                writeKey(xout, "UTExportedTypeDeclarations", 2);
                writeTypeDeclarations(xout, exportedTypeDeclarations, 2);
            }
            if (! importedTypeDeclarations.isEmpty()) {
                writeKey(xout, "UTImportedTypeDeclarations", 2);
                writeTypeDeclarations(xout, importedTypeDeclarations, 2);
            }

            // Write architectures
            writeStringArray(xout, "LSArchitecturePriority", architectures, 2);

            // Write Environment
            writeKey(xout, "LSEnvironment", 2);
            writeIndentation(xout, 2);
            xout.writeStartElement(DICT_TAG);
            xout.writeCharacters("\n");
            writeKey(xout, "LC_CTYPE", 3);
            writeString(xout, "UTF-8", 3);

            for (Environment environment : environments) {
                writeProperty(xout, environment.getName(), environment.getValue(), 3);
            }

            writeIndentation(xout, 2);
            xout.writeEndElement();
            xout.writeCharacters("\n");

            // Write options
            writeKey(xout, "JVMOptions", 2);

            writeIndentation(xout, 2);
            xout.writeStartElement(ARRAY_TAG);
            xout.writeCharacters("\n");

            for (Option option : options) {
                if (option.getName() == null) writeString(xout, option.getValue(), 3);
            }

            writeIndentation(xout, 2);
            xout.writeEndElement();
            xout.writeCharacters("\n");

            // Write default options
            writeKey(xout, "JVMDefaultOptions", 2);

            writeIndentation(xout, 2);
            xout.writeStartElement(DICT_TAG);
            xout.writeCharacters("\n");

            for (Option option : options) {
                if (option.getName() != null) {
                    writeProperty(xout, option.getName(), option.getValue(), 3);
                }
            }

            writeIndentation(xout, 2);
            xout.writeEndElement();
            xout.writeCharacters("\n");

            // Write arguments
            writeStringArray(xout, "JVMArguments", arguments, 2);

            // Write arbitrary key-value pairs
            for (PlistEntry item : plistEntries) {
                writeKey(xout, item.getKey(), 2);
                writeValue(xout, item.getType(), item.getValue(), 2);
            }

            // End root dictionary
            writeIndentation(xout, 1);
            xout.writeEndElement();
            xout.writeCharacters("\n");

            // End root element
            xout.writeEndElement();
            xout.writeCharacters("\n");

            // Close document
            xout.writeEndDocument();
            xout.writeCharacters("\n");

            out.flush();
        } catch (XMLStreamException exception) {
            throw new IOException(exception);
        } finally {
            out.close();
        }
    }

    private void writeKey(XMLStreamWriter xout, String key, int indentationDepth) throws XMLStreamException {
        writeIndentation(xout, indentationDepth);
        xout.writeStartElement(KEY_TAG);
        xout.writeCharacters(key);
        xout.writeEndElement();
        xout.writeCharacters("\n");
    }

    private void writeValue(XMLStreamWriter xout, String type, String value, int indentationDepth) throws XMLStreamException {
        if (type == null) {
            type = STRING_TAG;
        }
        if ("boolean".equals(type)) {
            writeBoolean(xout, "true".equals(value), indentationDepth);
        } else {
            writeIndentation(xout, indentationDepth);
            xout.writeStartElement(type);
            xout.writeCharacters(value);
            xout.writeEndElement();
            xout.writeCharacters("\n");
        }
    }

    private void writeString(XMLStreamWriter xout, String value, int indentationDepth) throws XMLStreamException {
        writeIndentation(xout, indentationDepth);
        xout.writeStartElement(STRING_TAG);
        xout.writeCharacters(value);
        xout.writeEndElement();
        xout.writeCharacters("\n");
    }

    private void writeBoolean(XMLStreamWriter xout, boolean value, int indentationDepth) throws XMLStreamException {
        writeIndentation(xout, indentationDepth);
        xout.writeEmptyElement(value ? "true" : "false");
        xout.writeCharacters("\n");
    }

    private void writeProperty(XMLStreamWriter xout, String key, Boolean value, int indentationDepth) throws XMLStreamException {
        if (value != null) {
            writeKey(xout, key, indentationDepth);
            writeBoolean(xout, value, indentationDepth);
        }
    }

    private void writeProperty(XMLStreamWriter xout, String key, Object value, int indentationDepth) throws XMLStreamException {
        if (value != null) {
            writeKey(xout, key, indentationDepth);
            writeString(xout, value.toString(), indentationDepth);
        }
    }

    public void writeStringArray(XMLStreamWriter xout, final String key,
            final Iterable<String> values, int indentationDepth) throws XMLStreamException {
        if (values != null) {
            writeKey(xout, key, indentationDepth);
            writeIndentation(xout, indentationDepth);
            xout.writeStartElement(ARRAY_TAG);
            xout.writeCharacters("\n");
            for(String singleValue : values) {
                writeString(xout, singleValue, indentationDepth + 1);
            }
            writeIndentation(xout, indentationDepth);
            xout.writeEndElement();
            xout.writeCharacters("\n");
        }
    }

  public void writeBundleDocuments(XMLStreamWriter xout,
                                   final ArrayList<BundleDocument> bundleDocuments,
                                   int indentationDepth) throws XMLStreamException {
    writeIndentation(xout, indentationDepth);
    xout.writeStartElement(ARRAY_TAG);
    xout.writeCharacters("\n");

    for(BundleDocument bundleDocument: bundleDocuments) {
      writeIndentation(xout, indentationDepth + 1);
      xout.writeStartElement(DICT_TAG);
      xout.writeCharacters("\n");

      final List<String> contentTypes = bundleDocument.getContentTypes();
      if (contentTypes != null) {
        writeStringArray(xout, "LSItemContentTypes", contentTypes, indentationDepth + 2);
      } else {
        writeStringArray(xout, "CFBundleTypeExtensions", bundleDocument.getExtensions(), indentationDepth + 2);
        writeProperty(xout, "LSTypeIsPackage", bundleDocument.isPackage(), indentationDepth + 2);
      }
      writeStringArray(xout, "NSExportableTypes", bundleDocument.getExportableTypes(), indentationDepth + 2);

      final File ifile = bundleDocument.getIconFile();
      writeProperty(xout, "CFBundleTypeIconFile", ifile != null ?
                                                  ifile.getName() : bundleDocument.getIcon(), indentationDepth + 2);

      writeProperty(xout, "CFBundleTypeName", bundleDocument.getName(), indentationDepth + 2);
      writeProperty(xout, "CFBundleTypeRole", bundleDocument.getRole(), indentationDepth + 2);
      writeProperty(xout, "LSHandlerRank", bundleDocument.getHandlerRank(), indentationDepth + 2);

      writeIndentation(xout, indentationDepth + 1);
      xout.writeEndElement();
      xout.writeCharacters("\n");
    }

    writeIndentation(xout, indentationDepth);
    xout.writeEndElement();
    xout.writeCharacters("\n");
  }

  public void writeTypeDeclarations(XMLStreamWriter xout,
            final ArrayList<TypeDeclaration> typeDeclarations, int indentationDepth) throws XMLStreamException {
        writeIndentation(xout, indentationDepth);
        xout.writeStartElement(ARRAY_TAG);
        xout.writeCharacters("\n");
        for (TypeDeclaration typeDeclaration: typeDeclarations) {

            writeIndentation(xout, indentationDepth + 1);
            xout.writeStartElement(DICT_TAG);
            xout.writeCharacters("\n");

            writeProperty(xout, "UTTypeIdentifier", typeDeclaration.getIdentifier(), indentationDepth + 2);
            writeProperty(xout, "UTTypeReferenceURL", typeDeclaration.getReferenceUrl(), indentationDepth + 2);
            writeProperty(xout, "UTTypeDescription", typeDeclaration.getDescription(), indentationDepth + 2);

            final File ifile = typeDeclaration.getIconFile();
            writeProperty(xout, "UTTypeIconFile", ifile != null ?
                        ifile.getName() : typeDeclaration.getIcon(), indentationDepth + 2);

            writeStringArray(xout, "UTTypeConformsTo", typeDeclaration.getConformsTo(), indentationDepth + 2);

            writeKey(xout, "UTTypeTagSpecification", indentationDepth + 2);

            writeIndentation(xout, indentationDepth + 2);
            xout.writeStartElement(DICT_TAG);
            xout.writeCharacters("\n");

            writeStringArray(xout, "com.apple.ostype", typeDeclaration.getOsTypes(), indentationDepth + 3);
            writeStringArray(xout, "public.filename-extension", typeDeclaration.getExtensions(), indentationDepth + 3);
            writeStringArray(xout, "public.mime-type", typeDeclaration.getMimeTypes(), indentationDepth + 3);

            writeIndentation(xout, indentationDepth + 2);
            xout.writeEndElement();
            xout.writeCharacters("\n");

            writeIndentation(xout, indentationDepth + 1);
            xout.writeEndElement();
            xout.writeCharacters("\n");
        }

        writeIndentation(xout, indentationDepth);
        xout.writeEndElement();
        xout.writeCharacters("\n");
    }

    public void writeIndentation(XMLStreamWriter xout, int depth) throws XMLStreamException {
        for (int i = 0; i < depth; i++) {
            xout.writeCharacters("    ");
        }
    }

    private void writePkgInfo(File file) throws IOException {
        Writer out = new BufferedWriter(new FileWriter(file));

        try {
            out.write(OS_TYPE_CODE + signature);
            out.flush();
        } finally {
            out.close();
        }
    }

    private static void delete(File file) throws IOException {
        Path filePath = file.toPath();

        if (Files.exists(filePath, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isDirectory(filePath, LinkOption.NOFOLLOW_LINKS)) {
                File[] files = file.listFiles();

                for (int i = 0; i < files.length; i++) {
                    delete(files[i]);
                }
            }

            Files.delete(filePath);
        }
    }

    private static void copy(URL location, File file) throws IOException {
        try (InputStream in = location.openStream()) {
            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (Exception exc)
        {
        System.err.println ("Trying to copy " + location + " to " + file);
            throw exc;
        }
    }

    static void copy(File source, File destination) throws IOException {
        Path sourcePath = source.toPath();
        Path destinationPath = destination.toPath();

        destination.getParentFile().mkdirs();

        Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);

        if (Files.isDirectory(sourcePath)) {
            String[] files = source.list();

            for (int i = 0; i < files.length; i++) {
                String file = files[i];
                copy(new File(source, file), new File(destination, file));
            }
        }
    }
}
