package processing.app;

import java.io.File;

public class Platform {
    static public File getSettingsFolder() {
        File settingsFolder = null;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            settingsFolder = new File(System.getProperty("user.home") + "/Library/Processing");
        } else if (os.contains("windows")) {
            String appData = System.getenv("APPDATA");
            if (appData == null) {
                appData = System.getProperty("user.home");
            }
            settingsFolder = new File(appData + "\\Processing");
        } else {
            settingsFolder = new File(System.getProperty("user.home") + "/.processing");
        }
        return settingsFolder;
    }
}
