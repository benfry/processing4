/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2014-19 The Processing Foundation

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License version 2
  as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Properties;

/**
 * Storage class for user preferences and environment settings.
 * <P>
 * This class does not use the Properties class because .properties files use
 * ISO 8859-1 encoding, which is highly likely to be a problem when trying to
 * save sketch folders and locations. Like the rest of Processing, we use UTF8.
 * <p>
 * We don't use the Java Preferences API because it would entail writing to
 * the registry (on Windows), or an obscure file location (on Mac OS X) and
 * make it far more difficult (impossible) to remove the preferences.txt to
 * reset them (when they become corrupt), or to find the the file to make
 * edits for numerous obscure preferences that are not part of the preferences
 * window. If we added a generic editor (e.g. about:config in Mozilla) for
 * such things, we could start using the Java Preferences API. But wow, that
 * sounds like a lot of work. Not unlike writing this paragraph.
 */
public class Preferences {
    static public String get(String attribute /*, String defaultValue */) {
        try {
            var settingsFile = Base.getSettingsFile("preferences.txt");
            var reader = new BufferedReader(new FileReader(settingsFile));

            var settings = new Properties();
            settings.load(reader);
            reader.close();

            return settings.getProperty(attribute);
        }catch (Exception e) {
            return null;
        }
    }
    static public boolean getBoolean(String attribute) {
        String value = get(attribute); //, null);
        return Boolean.parseBoolean(value);
    }
    static public int getInteger(String attribute /*, int defaultValue*/) {
        return Integer.parseInt(get(attribute));
    }
}
