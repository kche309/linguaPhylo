package lphy.evolution.io;

import lphy.evolution.alignment.Alignment;
import lphy.graphicalModel.Value;

import java.util.Map;

/**
 * Cache {@link Alignment} and store options.
 * For example, {@link #AGE_DIRECTION} and {@link #AGE_REGEX}.
 * @author Walter Xie
 */
public final class NexusOptions {

    protected static final String AGE_DIRECTION = "ageDirection";
    protected static final String AGE_REGEX = "ageRegex";

    public static final String OPT_DESC = "the map containing optional arguments and their values for reuse, " +
            "                          such as " + AGE_DIRECTION + " and " + AGE_REGEX + ".";

    public static String getAgeDirectionStr(Value<Map<String, String>> optionsVal) {
        Map<String, String> options = optionsVal == null ? null : optionsVal.value();
        return options == null ? null : options.get(AGE_DIRECTION);
    }

    public static String getAgeRegxStr(Value<Map<String, String>> optionsVal) {
        Map<String, String> options = optionsVal == null ? null : optionsVal.value();
        return options == null ? null : options.get(AGE_REGEX);
    }

    public static boolean isSame(Map<String, String> options1, Map<String, String> options2) {
        if (options1 == options2) return true; // include null == null
        if (options1 == null || options2 == null) return false;
        for (Map.Entry<String, String> entry : options1.entrySet()) {
            // no key
            if (!options2.containsKey(entry.getKey())) return false;
            // not same value
            String opt2Val = options2.get(entry.getKey());
            if (!entry.getValue().equals(opt2Val)) return false;
        }
        return true;
    }



//    protected Alignment cachedAlignment = null;
//    protected String currentFileName = "";
//    protected Map<String, String> options;
////    protected NexusParser parser;
//
//    //*** Singleton ***//
//    private static NexusOptions instance;
//    private NexusOptions(){}
//    // cache the data from one Nexus file
//    public static NexusOptions getInstance(){
//        if(instance == null)
//            instance = new NexusOptions();
//        return instance;
//    }
//
//
//    public static void validate() {
//
//    }
//
//    /**
//     *
//     * @param fileName  the name of Nexus file.
//     * @param  options  keys are {@link #AGE_DIRECTION} and {@link #AGE_REGEX}.
//     *                  If set new Map<String, String> options, then read file again.
//     * @return either {@link SimpleAlignment} or {@link CharSetAlignment} if charsets are defined in Nexus.
//     */
//    public Alignment getAlignment(String fileName, Map<String, String> options, boolean ignoreCharset) {
//        // refresh cache
//        if (cachedAlignment == null || !currentFileName.equalsIgnoreCase(fileName) || hasOptions(options) ) {
//            Objects.requireNonNull(instance).options = options;
//            return readAlignment(fileName, ignoreCharset);
//        }
//        return cachedAlignment;
//    }
//
//    /**
//     * @see #getAlignment(String, Map, boolean)
//     */
//    public Alignment getAlignment(String fileName, boolean ignoreCharset) {
//        // refresh cache
//        if (cachedAlignment == null || !currentFileName.equalsIgnoreCase(fileName) )
//            return readAlignment(fileName, ignoreCharset);
//        return cachedAlignment;
//    }
//
//    // read alignment(s) from file
//    protected Alignment readAlignment(String fileName, boolean ignoreCharset) {
//        if (fileName == null)
//            throw new IllegalArgumentException("The file name can't be null!");
//        currentFileName = fileName;
//
//        // validate postfix
//        NexusParser importer = new NexusParser(currentFileName);
//
//        String ageDirectionStr = getAgeDirectionStr();
//        String ageRegxStr = getAgeRegxStr();
//
//        // either {@link SimpleAlignment} or {@link CharSetAlignment}
//        return importer.getLPhyAlignment(ignoreCharset, ageDirectionStr, ageRegxStr);
//    }
//
//    protected boolean hasOptions(Map<String, String> options) {
//        if (options == null || !( options.containsKey(AGE_DIRECTION) || options.containsKey(AGE_REGEX) ) )
//            return false;
//        return true;
//    }
//
//    // whether to set new options
//    protected boolean setOptions(Map<String, String> options) {
//        if (this.options == options) return false; // include null == null
//        if (this.options == null || options == null) {
//            this.options = options;
//            return true;
//        }
//        for (Map.Entry<String, String> entry : this.options.entrySet()) {
//            // no key
//            if (!options.containsKey(entry.getKey())) {
//                this.options = options;
//                return true;
//            }
//            // not same value
//            String opt2Val = options.get(entry.getKey());
//            if (!entry.getValue().equals(opt2Val)) {
//                this.options = options;
//                return true;
//            }
//        }
//        return false;
//    }


}
