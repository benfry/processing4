package processing.app;

import java.io.File;

public class Base {
    static private final int REVISION = 1294;

    static private File settingsOverride;
    /**
     * @return the current revision number, safe to be used for update checks
     */
    static public int getRevision() {
        return REVISION;
    }
    /**
     * Get the directory that can store settings. (Library on OS X, App Data or
     * something similar on Windows, a dot folder on Linux.) Removed this as a
     * preference for 3.0a3 because we need this to be stable, but adding back
     * for 4.0 beta 4 so that folks can do 'portable' versions again.
     */
    static public File getSettingsFolder() {
        File settingsFolder = null;

        try {
            settingsFolder = Platform.getSettingsFolder();

            // create the folder if it doesn't exist already
            if (!settingsFolder.exists()) {
                if (!settingsFolder.mkdirs()) {
                    System.err.println("Could not create the folder " + settingsFolder);

                }
            }
        } catch (Exception e) {
            System.err.println("Could not get the settings folder");
        }
        return settingsFolder;
    }



    static public File getSettingsOverride() {
        return settingsOverride;
    }

    /**
     * Convenience method to get a File object for the specified filename inside
     * the settings folder. Used to get preferences and recent sketch files.
     * @param filename A file inside the settings folder.
     * @return filename wrapped as a File object inside the settings folder
     */
    static public File getSettingsFile(String filename) {
        return new File(getSettingsFolder(), filename);
    }
}
