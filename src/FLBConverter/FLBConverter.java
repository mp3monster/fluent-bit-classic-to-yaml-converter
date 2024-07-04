/**
 * @author Phil Wilkins
 * @Email code@mp3monster.org
 * @license Apache 2.0
 * 
 * This is a single file application (meaning we can skip the jar generation task).
 * Its purpose is to read a Fluent Bit classic file and generate a YAML representation
 */
package FLBConvertor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

class FLBConverter {

  private static final String FLB_PATH_PREFIX = "FLB_PATH_PREFIX";
  private static final String FLB_PATH_PREFIX_HELP = "Allows us to create a default offset location for our files. Helps when the folder in which the files to be converted are in a different folder";

  private static final String FLB_REPORT_FILE = "FLB_REPORT_FILE";
  private static final String FLB_REPORT_FILE_HELP = "When set to true then the log output will be added to a file";

  private static final String FLB_CONVERT_DEBUG = "FLB_CONVERT_DEBUG";
  private static final String FLB_CONVERT_DEBUG_HELP = "when the value of trues is found debug level logging is enabled.";

  private static final String CONVERSION_LIST = "conversion.list";
  private static final String CONVERSION_LIST_HELP = "Conversion list file provides a means by which we can supply multiple files to convert in one a single execution";

  private static final String FLB_CLASSIC_FN = "FLBClassicFN";
  private static final String FLB_CLASSIC_FN_HELP = "This environment variable can be used to identify a single conversion";

  private static final String NL = "\n";
  private static final String TRUE = "true";
  private static final String REPORT_EXTN = ".report";
  private static final String PIPELINEYAMLLBL = "pipeline:\n";
  private static final String SERVICEYAMLLBL = "service:\n";
  private static final String FILTERCLASSIC = "[FILTER]";
  private static final String OUTPUTCLASSIC = "[OUTPUT]";
  private static final String INPUTCLASSIC = "[INPUT]";
  private static final String SERVICECLASSIC = "[SERVICE]";
  private static final String OUTPUTSYAML = "outputs";
  private static final String FILTERSYAML = "filters";
  private static final String INPUTSYAML = "inputs";

  private static final String INFO_LBL = "info:";
  private static final String DEBUG_LBL = "DEBUG:";
  private static final String ERROR_LBL = "ERROR:";

  /**
   * Define the debug flag globally - nothing is gained by passing it around
   */
  private static boolean debug = false;
  private static boolean useIdiomaticForm = false;
  private static boolean logToFile = false;
  private static FileWriter converterReport = null;

  /**
   * Define the different plugin types.
   */
  enum PluginType {
    INPUT, OUTPUT, FILTER, SERVICE
  };

  static class HashMapArray extends HashMap<String, ArrayList<String>> {
  }

  /**
   * Writes a debug message if debug is allowed. If the report file is setup
   * then we'll also add the debug message to that as well
   * 
   * @param msg the message to write as a dubg statment
   */
  static void debug(String msg) {
    if (debug) {
      final String logStr = DEBUG_LBL + msg;
      System.out.println(logStr);
      if (logToFile && converterReport != null) {
        try {
          converterReport.write(logStr + NL);
        } catch (IOException err) {
          System.out.println("Unable to record debug to report file");
        }
      }
    }
  }

  /**
   * Writes a log message. If the report file is setup
   * then we'll also add the debug message to that as well
   * 
   * @param msg message to be logged
   */
  static void info(String msg) {
    final String logStr = INFO_LBL + msg;
    System.out.println(logStr);
    if (logToFile && converterReport != null) {
      try {
        converterReport.write(logStr + NL);
      } catch (IOException err) {
        System.out.println("Unable to record info to report file");
      }
    }
  }

  /**
   * If an error needs to be logged use this method. If the file is enabled then
   * we add the log to the file as well.
   * 
   * @param msg message to be displayed
   */
  static void err(String msg) {
    final String logStr = ERROR_LBL + msg;
    System.out.println(logStr);
    if (logToFile && converterReport != null) {
      try {
        converterReport.write(logStr + NL);
      } catch (IOException err) {
        System.out.println("Unable to record ERROR to report file");
      }
    }
  }

  /**
   * This class helps us process the plugins and directives. Each plugin will have
   * an instance of this class. We track the plugin type so we can get the order
   * of output correct
   */
  static class Plugin {
    private static final String WILDCARD = "*";
    private static final String DUMMYATTR = "dummy";
    private static final String COMMENT = "#";
    private static final String INCLUDE = "@include";
    private static final String NAMEATTR = "name";
    private static final String SEPARATOR = " ";
    static final int PLUGININDENT = 1;
    static final int NAMEINDENT = 2;
    static final int ATTRIBUTEINDENT = 3;
    public PluginType pluginType;
    private String name = null;

    /**
     * As certin attributes are allowed to reoccur such as the rules in the modifier
     * filter plugin we store each attribute name as a HashMap containing array
     * lists. We've declared our our own class for this to simplify the use of the
     * definition
     */
    HashMapArray attributes = new HashMapArray();

    String indenter(int depth) {
      String indent = "";
      for (int idx = 0; idx < depth; idx++) {
        indent = indent + "  ";
      }
      return indent;
    }

    public Plugin(PluginType type) {
      this.pluginType = type;
    }

    /**
     * We examine the line to see if the @includes is used. Fluent Bit allows us to
     * set attribute elements with an @includes, however in YAML its use is a lot
     * more restrictive. So when we encounter its use we need to log it
     * 
     * @param line   the line to examine
     * @param lineNo position in the source file
     * @return true if an includes is identified
     */
    static boolean checkForInclusion(String line, int lineNo) {
      boolean found = false;
      String tempLine = line.toLowerCase();
      if (tempLine.contains(INCLUDE)) {
        info("Warning: @include found at line " + lineNo + "  >>  " + line);
        found = true;
      }

      return found;
    }

    private String toIdiomaticForm(String attributeName) {

      StringBuilder builder = new StringBuilder(attributeName);

      // Traverse the string character by
      // character and remove underscore
      // and capitalize next letter
      for (int i = 0; i < builder.length(); i++) {

        // Check char is underscore
        if (builder.charAt(i) == '_') {

          builder.deleteCharAt(i);
          builder.replace(
              i, i + 1,
              String.valueOf(
                  Character.toUpperCase(
                      builder.charAt(i))));
        }
      }

      // Return in String type
      return builder.toString();

    }

    /**
     * 
     * Each attribute is processed into this instance of a plugin.
     * If any issues are identified such as the use of
     * the @includes feature this is logged to
     * info. We also deal with any of the foible such as quoting
     * wildcards and dummy attributes
     * 
     * @param attribute the attribute line from the source file
     * @param lineNo    the line number for this attribute in the source file
     * 
     */
    public void add(String attribute, int lineNo) {
      boolean hasInclusion = false;
      if (attribute == null) {
        return;
      }
      attribute = attribute.trim();

      if (attribute.length() == 0) {
        return;
      }
      hasInclusion = checkForInclusion(attribute, lineNo);

      String attributeName = null;
      int sepPos = 0;
      if (attribute.startsWith(COMMENT)) {
        sepPos = 1;
      } else {
        sepPos = attribute.indexOf(SEPARATOR);
      }
      if (sepPos > 0) {
        attributeName = attribute.substring(0, sepPos).trim();
        if (hasInclusion) {
          attributeName = "#" + attributeName;
          // its not already a comment - let's comment out the inclusion
        }
        // we need to handle the name attribute slightly differently
        if (attributeName.equalsIgnoreCase(NAMEATTR)) {
          this.name = attribute.substring(sepPos);
        } else {
          // determine whether there is a wildcard involved, correct the quotations
          String attributeValue = attribute.substring(sepPos).trim();
          if (attributeValue.equalsIgnoreCase(WILDCARD)) {
            attributeValue = "'*'";
          }
          // ensure that dummy attributes are correctly quoted
          if ((attributeName.equalsIgnoreCase(DUMMYATTR)) && (!attributeValue.startsWith("'"))) {
            attributeValue = "'" + attributeValue + "'";
          }
          ArrayList<String> values = null;
          if (attributes.containsKey(attributeName)) {
            values = attributes.get(attributeName);
          } else {
            values = new ArrayList<String>();
          }
          values.add(attributeValue);
          attributes.put(attributeName, values);
        }
      } else {
        info("Cant process attribute:" + attribute);
      }

    }

    /**
     * Provides a correctly formatted plugin name - we declare this separatelty so
     * anything that is handled using the plugin object such as a service can have
     * this action overridden
     * 
     * @return returns the name label with correct indentation for the YAML config
     */
    String writePrefix() {
      return indenter(NAMEINDENT) + "- name: " + this.name + NL;
    }

    /**
     * We build the YAML representation of the plugin, iterating over the data
     * structure and handling the possibility of having multiple attributes of the
     * same type
     * 
     * @return string containing the plugin in YAML format
     */
    public String write() {
      String YAMLoutput = writePrefix();
      Iterator<String> iter = attributes.keySet().iterator();
      String key = null;
      String line = null;

      while (iter.hasNext()) {
        key = iter.next();
        ArrayList<String> multiValues = attributes.get(key);
        Iterator<String> valIter = multiValues.iterator();
        String value = null;
        while (valIter.hasNext()) {
          value = valIter.next();
          if (key.equals(COMMENT)) {
            line = indenter(ATTRIBUTEINDENT) + key + value;
          } else {
            line = indenter(ATTRIBUTEINDENT) + key + ": " + value;
          }
          debug(line);
          YAMLoutput = YAMLoutput + line + NL;
        }
      }

      return YAMLoutput;
    }

    // public String getName() {
    // return pluginType.toString();
    // }
  }

  /**
   * We need the rewepresentation of services to differ from inputs, outputs and
   * filters. The level of YAML indentation is different, and we don't have a name
   * attribute
   */
  static class ServicePlugin extends Plugin {
    public ServicePlugin() {
      super(PluginType.SERVICE);
    }

    /**
     * Provide identation that reflects the service block
     */
    @Override
    String indenter(int depth) {
      return "  ";
    }

    /**
     * We don't want to handle a name attribute in a manner that is different to
     * other service attributes.
     */
    @Override
    String writePrefix() {
      return "";
    }

  }

  private static Plugin service = null;

  private static ArrayList<Plugin> inputs = null;
  private static ArrayList<Plugin> outputs = null;
  private static ArrayList<Plugin> filters = null;

  /**
   * Depending upon the the label in the classic file, we need to decide which
   * group of plugins to add the latest definition to.
   * 
   * @param currentPlugin
   */
  private static void storePlugin(Plugin currentPlugin) {
    if (currentPlugin != null) {
      switch (currentPlugin.pluginType) {
        case FILTER:
          filters.add(currentPlugin);
          break;
        case INPUT:
          inputs.add(currentPlugin);
          break;
        case OUTPUT:
          outputs.add(currentPlugin);
          break;
        case SERVICE:
          service = currentPlugin;
          break;
        default:
          info("Unknown plugin type");
          break;

      }
    } else {
      debug("No plugin to store");
    }
  }

  /**
   * This method reads the classic configuration file and handles when we need to
   * start a new plugin object, and when to add a plugin into the correct array of
   * plugins. Note we only allow one service definition
   * 
   * @param classicFile the reader object for the classiv file
   * @throws IOException if we fail to read the file properly
   */
  private static void consumeClassicFile(BufferedReader classicFile) throws IOException {
    Plugin currentPlugin = null;
    int lineCount = 0;
    String line = null;
    while ((line = classicFile.readLine()) != null) {
      lineCount++;
      line = line.trim();

      debug(line);

      if (line.length() > 0) {
        if (line.equalsIgnoreCase(SERVICECLASSIC)) {
          storePlugin(currentPlugin);
          currentPlugin = new ServicePlugin();
        } else if (line.equalsIgnoreCase(INPUTCLASSIC)) {
          storePlugin(currentPlugin);
          currentPlugin = new Plugin(PluginType.INPUT);
        } else if (line.equalsIgnoreCase(OUTPUTCLASSIC)) {
          storePlugin(currentPlugin);
          currentPlugin = new Plugin(PluginType.OUTPUT);
        } else if (line.equalsIgnoreCase(FILTERCLASSIC)) {
          storePlugin(currentPlugin);
          currentPlugin = new Plugin(PluginType.FILTER);
        } else {
          if (currentPlugin == null) {
            info("Can't allocate process line:>" + line + "< (" + lineCount + ")");
          } else {
            currentPlugin.add(line, lineCount);
          }
        }
      }
    }
    storePlugin(currentPlugin);

  }

  /**
   * Takes the list odf plugins, and writes the correct prefix label before
   * iterrating through the plugins adding them in the correct YAML format
   * 
   * @param plugins the list of plugin objects of a particular type
   * @param outFile the output file buffer
   * @param label   the label for this type of plugin
   * @throws IOException thrown if there id
   */
  private static void writePlugins(ArrayList<Plugin> plugins, BufferedWriter outFile, String label) throws IOException {
    if (!plugins.isEmpty()) {
      outFile.write("  " + label + ":\n");
      Iterator<Plugin> iter = plugins.iterator();
      while (iter.hasNext()) {
        outFile.write(iter.next().write() + NL);
      }
    }

  }

  /**
   * This orchestrates the corret order in which the pipeline is constructed in
   * the YAML file.
   * 
   * @param outFile the buffer write for the output
   * @throws IOException any io errors should lead to us bailing
   */
  private static void writePipelineOutput(BufferedWriter outFile) throws IOException {
    if (service != null) {
      outFile.write(SERVICEYAMLLBL);
      outFile.write(service.write());

      outFile.write(NL);
      outFile.write(PIPELINEYAMLLBL);
      if (!inputs.isEmpty()) {
        writePlugins(inputs, outFile, INPUTSYAML);
      }

      if (!filters.isEmpty()) {
        writePlugins(filters, outFile, FILTERSYAML);
      }

      if (!outputs.isEmpty()) {
        writePlugins(outputs, outFile, OUTPUTSYAML);
      }
    }
    outFile.flush();
    outFile.close();
  }

  /**
   * This works out the name for the output file based on the input name
   * 
   * @param inFileName input (classic) filename
   * @return output file's name.
   * 
   */
  private static String getOutFile(String inFileName) {
    String outFileName = inFileName;

    final String postfix = "conf";
    if (inFileName.endsWith(postfix)) {
      outFileName = outFileName.substring(0, inFileName.length() - postfix.length());
    }
    return outFileName + "yaml";
  }

  /**
   * We don't pass the filename of the report file as this is handled by the
   * logging methods. This method drives the core logic of scanning the Classic
   * file format and once read runs the process of writing each plugin/directive
   * to the output file
   * 
   * @param inFileName
   * 
   * @param outFileName
   */
  private static void processor(String inFileName, String outFileName) {
    getOutFile(inFileName);
    BufferedWriter outFile = null;
    FileReader fr = null;
    try {
      File inFile = new File(inFileName);
      if (!inFile.exists()) {
        err("Cant locate input file:" + inFileName);
        return;
      }
      fr = new FileReader(inFile);
      info("InputFile:" + inFileName + " --> " + outFileName);
      consumeClassicFile(new BufferedReader(fr));

      info("Inputs:" + inputs.size() + NL + "Filters:" + filters.size() + NL + "Outputs:" + outputs.size() + NL);

      outFile = new BufferedWriter(new FileWriter(outFileName));
      writePipelineOutput(outFile);
      fr.close();

    } catch (Exception err) {
      err(err.toString());
      err.printStackTrace();

    } finally {
      try {
        if (outFile != null) {
          outFile.close();
        }
      } catch (IOException ioErr) {
        err("finally err closing file\n" + ioErr.getMessage());
      }
      try {
        if (fr != null) {
          fr.close();
        }
      } catch (IOException ioErr) {
        err("ioError fr err closing file reader\n" + ioErr.getMessage());
      }
    }
  }

  /*
   * Generates the current date and time as a string
   */
  static String getDateStr() {
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    LocalDateTime now = LocalDateTime.now();
    return (dtf.format(now));
  }

  /**
   * Checks for the environment variable that control whether we log to debug
   * level. We've not adopted a logging framework so that we can keep the code as
   * a
   * single class. This means we can run without needing to have a Mave or Gradle
   */
  private static boolean checkDebug() {
    String debugFlagStr = System.getenv(FLB_CONVERT_DEBUG);
    if ((debugFlagStr != null) && (debugFlagStr.trim().equalsIgnoreCase(TRUE))) {
      debug = true;
      System.out.println("Env flag for debug set to " + debugFlagStr);
    }
    return debug;
  }

  /**
   * If the environment variable is set then any info logs are also written to a
   * file that is the same as the output with the filename extended with .report
   * Useful if we're using a conversion.list
   */
  private static boolean checkReportToFile() {
    String reportFlagStr = System.getenv(FLB_REPORT_FILE);
    logToFile = false;
    if ((reportFlagStr != null) && (reportFlagStr.trim().equalsIgnoreCase(TRUE))) {
      logToFile = true;
    }
    debug("Env flag for reporting to file set to " + reportFlagStr);
    return logToFile;
  }

  /**
   * Gets the path prefix env var if set. Then if it is not terminated with slash
   * we add a slash.
   */
  private static String getPathPrefix() {
    String pathPrefix = System.getenv(FLB_PATH_PREFIX);
    if ((pathPrefix == null) || (pathPrefix.length() == 0)) {
      pathPrefix = "";
    } else {
      pathPrefix = pathPrefix.trim();
      if ((!pathPrefix.endsWith("\\")) || (!pathPrefix.endsWith("/"))) {
        if (pathPrefix.contains("\\")) {
          pathPrefix = pathPrefix + "\\";
        } else {
          pathPrefix = pathPrefix + "/";
        }
      }
    }
    debug("setting path prefix to:" + pathPrefix);

    return pathPrefix;

  }

  /**
   * Checks for the idomatic form configuration and sets the flag
   * 
   * @return
   */
  private static boolean useIdiomatricForm() {
    boolean flag = false;
    String idiomaticFlagStr = System.getenv("FLB_IDIOMATICFORM");
    logToFile = false;
    if ((idiomaticFlagStr != null) && (idiomaticFlagStr.trim().equalsIgnoreCase(TRUE))) {
      flag = true;
    }
    debug("Env flag for using idomatic form to file set to " + idiomaticFlagStr);

    useIdiomaticForm = flag;
    return flag;
  }

  /**
   * 
   * The app's name drives the execution of processing one or more classic format
   * files getting the inputfile from one of several different sources. It
   * establishes the data structures that we use to manage the holding of the
   * different plugins.
   * Each new file is processed we reset the constructs to ensure there isn't any
   * accidental cross contamination
   * 
   * @param args
   */
  public static void main(String[] args) {
    info("Fluent Bit Converter starting ...");
    checkDebug();
    useIdiomatricForm();
    String inFileName = null;
    try {
      boolean more = false;
      File conversionList = new File(getPathPrefix() + CONVERSION_LIST);
      BufferedReader br = null;
      FileReader fr = null;

      if (conversionList.exists()) {

        debug("found conversion list file");
        fr = new FileReader(conversionList);
        br = new BufferedReader(fr);
        inFileName = br.readLine().trim();
        debug("Conversion list file points to:" + inFileName);
      } else {
        debug("No conversion list, looked for - " + conversionList.getPath());
      }

      if ((inFileName == null) && (args != null) && (args.length != 0)) {
        inFileName = args[0];
      }
      if (inFileName == null) {
        inFileName = System.getenv(FLB_CLASSIC_FN);
        if (inFileName == null) {
          debug("No env var");
        }
      }

      while (inFileName != null) {
        inFileName = getPathPrefix() + inFileName;
        inputs = new ArrayList<Plugin>();
        outputs = new ArrayList<Plugin>();
        filters = new ArrayList<Plugin>();

        if (checkReportToFile()) {
          File reportFile = new File(getOutFile(inFileName) + REPORT_EXTN);
          converterReport = new FileWriter(reportFile);
          converterReport.write("Execution date:" + getDateStr() + NL);
        }
        processor(inFileName, getOutFile(inFileName));

        if (more) {
          inFileName = br.readLine().trim();
        } else {
          inFileName = null;
        }
        if (converterReport != null) {
          converterReport.flush();
          converterReport.close();
          converterReport = null;
        }
      }
    } catch (Exception err) {
      err(err.getMessage());
      err.printStackTrace();
    }
  }

  /**
   * Generates the CLI help
   */
  static void printHelp() {
    System.out.println(FLB_CONVERT_DEBUG + " -- " + FLB_CONVERT_DEBUG_HELP);
    System.out.println(FLB_REPORT_FILE + " -- " + FLB_REPORT_FILE_HELP);
    System.out.println(FLB_PATH_PREFIX + " -- " + FLB_PATH_PREFIX_HELP);
    System.out.println(FLB_CLASSIC_FN + " -- " + FLB_CLASSIC_FN_HELP);

    System.out.println(NL);
    System.out.println(CONVERSION_LIST + " -- " + CONVERSION_LIST_HELP);

  }
}