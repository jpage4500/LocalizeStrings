import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * convert Android strings into iOS localized strings.
 */
public class LocalizeStrings {

    private static final String TRANSLATE_TO = "TRANSLATE TO ";

    // map of language code (ie: "en", "es") to Map<String, String> which contains key ("text_ok") to value ("OK")
    private static Map<String, Map<String, String>> mLangMap = new TreeMap<String, Map<String, String>>();

    private static String DEFAULT_LANGUAGE = "en";

    // what resources we're looking for..
    private static String USE_STRING = "string";

    private static int mNumLocalizedStrings;

    private static File iosRoot;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Program to convert Android strings into iOS localized strings");
            System.out.println("usage: LocalizeStrings <Android path> <iOS path>");
            System.out.println("- where <path> is the path to an Android project (where AndroidManifest.xml exists)");
            System.out.println("");
            System.out.println("eg: java LocalizeStrings ~/working/mobeam/beepngo-android/BeepNGo ~/working/mobeam/beepngo-ios/Beepngo");
            System.exit(0);
        }

        String androidRoot = args[0];

        File mainFile = new File(androidRoot + "/AndroidManifest.xml");
        if (mainFile.exists() == false) {
            System.out.println("file: " + mainFile + " does not exist!\nBase directory should point to an Android project.");
            System.exit(0);
        }

        iosRoot = new File(args[1]);
        if (iosRoot.exists() == false) {
            System.out.println("iOS directory: " + mainFile + " does not exist!\nBase directory should point to an iOS project root directory.");
            System.exit(0);
        }

        File resDir = new File(androidRoot + "/res");

        System.out.println("Indexing strings...");

        // index strings in all .xml files in values*/ directory
        indexStrings(resDir);

        // print out # of strings found for each language
        Iterator<String> it = mLangMap.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            Map<String, String> valueMap = mLangMap.get(key);
            if (valueMap.size() > 0) {
                System.out.println("language:" + key + ", strings:" + valueMap.size());
            }
        }

        // find and replace all .m files in iOS directory which have a string-value match
        searchDirForUse(iosRoot);

        System.out.println("Localized " + mNumLocalizedStrings + " strings");

        // add remaining strings to iOS localized file even if they're not used
        Map<String, String> englishMap = mLangMap.get(DEFAULT_LANGUAGE);
        if (englishMap != null) {
            it = englishMap.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();
                String value = englishMap.get(key);
                addLocalizedString(key, value);
            }

            // next, find any untranslated strings (ie: in English but not other translations) and add these
            // print out # of strings found for each language
            it = mLangMap.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();
                if (key.equals(DEFAULT_LANGUAGE)) {
                    continue;
                }
                // key = non-English key-value maps
                Map<String, String> otherLangMap = mLangMap.get(key);
                // find difference in keys
                Set<String> englishKeySet = new HashSet<String>(englishMap.keySet());
                Set<String> otherLangKeySet = new HashSet<String>(otherLangMap.keySet());
                englishKeySet.removeAll(otherLangKeySet);

                // for each missing key - add English version to translation file
                Iterator<String> missingIt = englishKeySet.iterator();
                while (missingIt.hasNext()) {
                    String missingKey = missingIt.next();
                    String englishString = englishMap.get(missingKey);
                    addLocalizedStringForLanguage(key, missingKey, englishString, false);
                }
            }
        }

    }

    private static void indexStrings(File dir) {
        File[] fileArr = dir.listFiles();
        for (File file : fileArr) {
            String filename = file.getName();
            if (file.isDirectory() && filename.startsWith("values")) {
                indexStrings(file);
            } else if (!file.isDirectory() && filename.startsWith("strings")) {
                String langCode = getLanguageCodeFromDir(dir);
                Map<String, String> stringMap = mLangMap.get(langCode);
                if (stringMap == null) {
                    // first time - add language code and string map
                    stringMap = new HashMap<String, String>();
                    mLangMap.put(langCode, stringMap);
                }
                readFileContents(file, stringMap, langCode);
            }
        }
    }

    private static String getLanguageCodeFromDir(File dir) {
        // get language code from directory (may not exist)
        String dirName = dir.getName();
        int dashPos = dirName.indexOf('-');
        if (dashPos > 0) {
            String langCode = dirName.substring(dashPos + 1);
            // android handles chinese locale differently than iOS; http://www.ibabbleon.com/iOS-Language-Codes-ISO-639.html
            if (langCode.equalsIgnoreCase("zh-rCN")) {
                return "zh-Hans";
            } else if (langCode.equalsIgnoreCase("zh-rTW")) {
                return "zh-Hant";
            }
            return langCode;
        } else {
            // default to english
            return DEFAULT_LANGUAGE;
        }
    }

    private static void readFileContents(File file, Map<String, String> stringMap, String langCode) {
        BufferedReader br = null;
        String beginTag = createBeginTag(USE_STRING);
        String endTag = createEndTag(USE_STRING);
        try {
            br = new BufferedReader(new FileReader(file));
            while (true) {
                String line = br.readLine();
                if (line == null) {
                    break;
                }

                // <string name="network_error">Network Error</string>

                // find: <string name="
                int stPos = line.indexOf(beginTag);
                if (stPos < 0) {
                    continue;
                }
                // find: ">
                int endPos = line.indexOf("\">", stPos);
                if (endPos <= 0) {
                    continue;
                }

                // get key (network_error)
                String key = line.substring(stPos + beginTag.length(), endPos);

                // find: </string>
                stPos = endPos + 2; // move start position to value (past ">)
                endPos = line.indexOf(endTag, stPos);
                if (endPos < 0) {
                    continue;
                }

                // get value (Network Error)
                String value = line.substring(stPos, endPos);

                // log any odd/strange translation values
                // ...em \""\""Recomendados\""\""</string>
                if (value.indexOf("\\\"\"\\\"\"") >= 0) {
                    System.out.println("questionable translation (" + langCode + "): " + value);
                }

                // add key/value to map
                stringMap.put(key, value);
            }
        } catch (Exception e) {
            System.out.println("readFileContents: Error reading file: " + file + ", " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private static String createBeginTag(String key) {
        return "<" + key + " name=\"";
    }

    private static String createEndTag(String key) {
        return "</" + key + ">";
    }

    // ----------------------------------------------------------------------------

    private static void searchDirForUse(File dir) {
        // now, look through all .m files to find matches
        File[] fileArr = dir.listFiles();
        for (File file : fileArr) {
            if (file.isDirectory()) {
                // ignore "external" directory as this isn't our code..
                if (!isIgnoredDirectory(file)) {
                    searchDirForUse(file);
                }
            } else {
                String filename = file.getName();
                if (filename.endsWith(".m") && !isIgnoredFile(filename)) {
                    searchFileForUse(file);
                }
            }
        }
    }

    // check if this directory should be ignored
    private static boolean isIgnoredDirectory(File dir) {
        String name = dir.getName();
        if (!name.equals("external")) {
            return true;
        } else {
            return false;
        }
    }

    // check if this file should be ignored
    private static boolean isIgnoredFile(String name) {
        if (!name.equals("RRSStoryboard.m") && !name.equals("MobeamConstants.m")) {
            return true;
        } else {
            return false;
        }
    }

    private static void searchFileForUse(File file) {
        boolean hasAnyMatches = false;
        StringBuffer replaceLines = new StringBuffer();

        //System.out.println("searching: " + file.getName());

        BufferedReader br = null;
        BufferedWriter bw = null;
        try {
            br = new BufferedReader(new FileReader(file));
            while (true) {
                String line = br.readLine();
                if (line == null) {
                    // done reading file
                    break;
                }

                // ignore commented out lines or log lines
                if (line.contains("DDLog") || line.contains("NSLog") || line.trim().startsWith("//")) {
                    //System.out.println("ignore comments: " + partialLine);
                    replaceLines.append(line);
                    replaceLines.append('\n');
                    continue;
                }

                StringBuffer replaceLine = null;

                // support multiple strings on a single line
                // ex: [self createHeaderCellFor:cell withStepNum:@"3" totalSteps:@"OF THREE" description:@"Select card image" backgroundColor:[UIColor bngColor_greenColor]];
                int linePos = 0;
                for (int stPos = 0; stPos < line.length();) {
                    // find hardcoded strings: (eg: self.location.text = @"Nearby";)
                    stPos = line.indexOf("@\"", stPos);
                    if (stPos < 0) {
                        // nothing on this line
                        break;
                    }
                    int endPos = line.indexOf("\"", stPos + 2); // +2 to skip past @"
                    if (endPos < 0) {
                        // nothing on this line
                        break;
                    }

                    // make sure this string hasn't already been localized!
                    // self.location.text = NSLocalizedString(@"Nearby", @"nearby");
                    String partialLine = line.substring(linePos, endPos);
                    if (partialLine.contains("NSLocalizedString")) {
                        //System.out.println("already localized: " + partialLine);
                        // continue searching on this line
                        stPos = endPos + 1;
                        continue;
                    }

                    // get string (Nearby)
                    String value = line.substring(stPos + 2, endPos);
                    // ignore very short and unusual iOS strings: @"%s"
                    if (value.length() <= 1 || value.equals("%s")) {
                        // continue searching on this line
                        stPos = endPos + 1;
                        continue;
                    }

                    // check if this string matches English version of Android strings
                    String androidKey = findKeyForValue(value);

                    // can't localize const strings
                    // static NSString *const DISP_GENDER_MALE = @"Male";
                    if (partialLine.contains("const") || partialLine.contains("static")) {
                        if (androidKey != null) {
                            // log this so we can move string
                            System.out.println("can't localize static! key: " + androidKey + ", file: " + file.getName() + ", line: " + partialLine);
                        }
                        // continue searching on this line
                        stPos = endPos + 1;
                        continue;
                    }

                    if (androidKey != null) {
                        //System.out.println("found key:" + androidKey + ", str:" + value);
                        mNumLocalizedStrings++;
                        if (replaceLine == null) {
                            replaceLine = new StringBuffer();
                        }
                        // append start of line up to what we want to replace
                        // eg: "self.location.text = "
                        replaceLine.append(line.substring(linePos, stPos));

                        // create replacement value
                        replaceLine.append("NSLocalizedString(@\"");
                        replaceLine.append(androidKey);
                        replaceLine.append("\", @\"");
                        replaceLine.append(value);
                        replaceLine.append("\")");

                        addLocalizedString(androidKey, value);

                        linePos = endPos + 1;
                    }

                    // continue searching on this line
                    stPos = endPos + 1;
                }

                if (replaceLine != null) {
                    // found at least 1 match.. append remaining portion of line
                    replaceLine.append(line.substring(linePos));

                    // add line to be replaced
                    replaceLines.append(replaceLine.toString());

                    hasAnyMatches = true;
                } else {
                    // just copy entire line as-is
                    replaceLines.append(line);
                }
                replaceLines.append('\n');
            }

            // check if any lines need to be replaced
            if (hasAnyMatches) {
                // replace file with updated version
                bw = new BufferedWriter(new FileWriter(file));
                bw.write(replaceLines.toString());
                bw.close();
            }
        } catch (Exception e) {
            System.out.println("searchFileForUse: Error reading file: " + file + ", " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                }
            }
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException e) {
                }
            }
        }
    }

    // find English value and return matching key
    private static String findKeyForValue(String englishStr) {
        Map<String, String> stringMap = mLangMap.get(DEFAULT_LANGUAGE);
        if (stringMap != null) {
            Iterator<String> it = stringMap.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();
                String value = stringMap.get(key);
                // look for match
                if (value.equals(englishStr)) {
                    // found!
                    return key;
                }
            }
        }
        // not found
        return null;
    }

    // add androidKey to iOS strings (all languages)
    private static void addLocalizedString(String androidKey, String englishValue) {
        // iterate through each language code
        Iterator<String> it = mLangMap.keySet().iterator();
        while (it.hasNext()) {
            String langCode = it.next();
            Map<String, String> stringMap = mLangMap.get(langCode);
            if (stringMap.containsKey(androidKey)) {
                // get translated value
                String localizedValue = stringMap.get(androidKey);
                // add/update localization file
                addLocalizedStringForLanguage(langCode, androidKey, localizedValue, true);
            }
        }
    }

    private static void addLocalizedStringForLanguage(String langCode, String androidKey, String localizedValue, boolean isTranslated) {
        File localizedDir = new File(iosRoot, "resources/" + langCode + ".lproj");
        if (!localizedDir.exists()) {
            localizedDir.mkdir();
        }

        File localizedFile = new File(localizedDir, "Localizable.strings");
        if (!localizedFile.exists()) {
            try {
                localizedFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("ERROR creating file:" + localizedFile);
                return;
            }
        }

        StringBuffer replaceLines = new StringBuffer();

        BufferedReader br = null;
        BufferedWriter bw = null;
        try {
            br = new BufferedReader(new FileReader(localizedFile));
            while (true) {
                String line = br.readLine();
                if (line == null) {
                    // done reading file
                    break;
                }

                boolean skipLine = false;

                // look for key
                // format: "<key>" = "<value>"
                // get key
                int stPos = line.indexOf("\"");
                if (stPos >= 0) {
                    int endPos = line.indexOf("\"", stPos + 1);
                    if (endPos > 0) {
                        String key = line.substring(stPos + 1, endPos);
                        if (key.equals(androidKey)) {
                            if (isTranslated && line.contains(TRANSLATE_TO)) {
                                // ignore this untranslated line and use translated version instead
                                skipLine = true;
                            } else {
                                //System.out.println("key already exists: " + key);
                                // nothing to do!
                                return;
                            }
                        }
                    }
                }

                if (!skipLine) {
                    // copy line as-is
                    replaceLines.append(line);
                    replaceLines.append('\n');
                }
            }

            String formattedValue = fixLocalizedString(localizedValue);

            // add new key/value
            replaceLines.append("\"" + androidKey + "\" = \"" + formattedValue + "\";");

            if (!isTranslated) {
                replaceLines.append(" // " + TRANSLATE_TO + langCode);
            }
            replaceLines.append('\n');

            // replace file with updated version
            bw = new BufferedWriter(new FileWriter(localizedFile));
            bw.write(replaceLines.toString());
            bw.close();

        } catch (Exception e) {
            System.out.println("addLocalizedString: Error reading file: " + localizedFile + ", " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                }
            }
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private static String fixLocalizedString(String localizedValue) {
        // need to reformat the localizedValue a bit from Android to iOS

        // - badly translated values:
        // ...em \""\""Recomendados\""\""</string>
        localizedValue = localizedValue.replaceAll("\\\"\"", "\\\"");

        // - android quotes: "" -> iOS quotes: \"
        //localizedValue = localizedValue.replaceAll("\"\"", "\\\"");

        // uggghhh.. fix quotes that replaceAll isn't working for
        if (localizedValue.indexOf('"') >= 0) {
            StringBuilder sb = new StringBuilder();
            int stPos = 0;
            while (stPos < localizedValue.length()) {
                int pos = localizedValue.indexOf('"', stPos);
                if (pos < 0) {
                    break;
                }

                // check if line starts with "
                if (pos == 0 && stPos == 0) {
                    //System.out.println("line starts with quote, line: " + localizedValue);
                    // insert "\" in front of quote
                    sb.append("\\\"");
                } else if (localizedValue.charAt(pos - 1) != '\\') {
                    //System.out.println("bad char at: " + pos + ", line: " + localizedValue);
                    // copy line up to quote
                    sb.append(localizedValue.substring(stPos, pos));
                    // insert "\" in front of quote
                    sb.append("\\\"");
                } else {
                    // properly escaped quote.. copy and continue
                    sb.append(localizedValue.substring(stPos, pos + 1));
                }
                stPos = pos + 1;
            }

            if (stPos < localizedValue.length()) {
                // copy remaining part of line
                sb.append(localizedValue.substring(stPos));
            }
            localizedValue = sb.toString();
        }

        // - android params: %02s -> iOS params: %@
        localizedValue = localizedValue.replaceAll("%.*?[ds]", "%@");

        return localizedValue;
    }
}
