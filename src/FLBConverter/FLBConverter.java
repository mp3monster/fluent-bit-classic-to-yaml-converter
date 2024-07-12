/**
 * This is a single file application (meaning we can skip the jar generation task).
 * Its purpose is to read a Fluent Bit classic file and generate a YAML representation
 * 
 * @author Phil Wilkins
 * @Email code@mp3monster.org
 * @license Apache 2.0
 * 
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
import java.util.LinkedHashMap;

import java.util.Iterator;

/**
 * This is the tools main class - so has the main methods etc
 */
class FLBConverter {

  /**
   * Constants starting FLB are relatyed to configuration of this utility
   */
  private static final String INCLUDES_LBL = "#INCLUDES:";
  private static final String HELP = "--help";
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

  private static final String FLB_IDIOMATICFORM = "FLB_IDIOMATICFORM";
  private static final String FLB_IDIOMATICFORM_HELP = "When set the attributev names are converted to use the Kubernmetes idiomatic format";

  /**
   * Constants post fixed with CLASSIC are strings we search in the classic
   * format files
   * The post fixed names with YAML are the labels used in the YAML configuration
   * file
   */
  private static final String FILTERCLASSIC = "[FILTER]";
  private static final String OUTPUTCLASSIC = "[OUTPUT]";
  private static final String INPUTCLASSIC = "[INPUT]";
  private static final String SERVICECLASSIC = "[SERVICE]";
  private static final String INCLUDECLASSIC = "@include";

  private static final String NL = "\n";
  private static final String TRUE = "true";
  private static final String REPORT_EXTN = ".report";
  private static final String PIPELINEYAMLLBL = "pipeline:\n";

  /**
   * strings to use in the YAML file
   */
  private static final String OUTPUTSYAML = "outputs";
  private static final String FILTERSYAML = "filters";
  private static final String INPUTSYAML = "inputs";
  private static final String SERVICEYAMLLBL = "service:\n";

  /**
   * log message prefixes
   */
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
    INPUT, OUTPUT, FILTER, SERVICE, INCLUDES, SET
  };

  /**
   * We've defined this structure to simplify asnd ensure consistency. The Hashamp
   * allows us to look up attribute names, but there are scenarios where an
   * attribute key can be repeated.
   */
  static class HashMapArray extends LinkedHashMap<String, ArrayList<String>> {
  }

  private static SpecialPlugin service = null;
  private static SpecialPlugin includes = null;
  private static ArrayList<Plugin> inputs = null;
  private static ArrayList<Plugin> outputs = null;
  private static ArrayList<Plugin> filters = null;

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
    private static final String NAMEATTR = "name";
    private static final String SEPARATOR = " ";
    static final int PLUGININDENT = 1;
    static final int NAMEINDENT = 2;
    static final int ATTRIBUTEINDENT = 3;

    /** pluginType is an enumeration to make it easy to determine the plugin type */
    public PluginType pluginType;
    /** name represents the name plugin */
    private String name = null;

    /**
     * As certain attributes are allowed to reoccur such as the rules in the
     * modifier
     * filter plugin we store each attribute name as a HashMap containing array
     * lists. We've declared our our own class for this to simplify the use of the
     * definition
     */
    HashMapArray attributes = new HashMapArray();

    /**
     * Standard constructor
     * 
     * @param type this the enumeration showing the type of plugin being handled
     */
    public Plugin(PluginType type) {
      this.pluginType = type;
    }

    /**
     * get the number of different attributes types (some plugins allow multiple
     * occurrences of the same attribute)
     * 
     * @return the number of attributes held by this plugin. We only count one
     *         unique occurence of an attribute
     */
    public int attributeCountByType() {
      return attributes.size();
    }

    /**
     * builds an indent string that will be accepted by YAML
     * 
     * @param depth how many levels of indetation
     * @return the YAML suitable indentation characters
     */
    String indenter(int depth) {
      String indent = "";
      for (int idx = 0; idx < depth; idx++) {
        indent = indent + "  ";
      }
      return indent;
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
      if (tempLine.contains(INCLUDECLASSIC)) {
        info("Warning: @include found at line " + lineNo + "  >>  " + line);
        found = true;
      }

      return found;
    }

    /**
     * converts the attribute name to its idiomatc form if the option is enabled
     * 
     * @param attributeName name to convert
     * @return returns the attribute key in a format dictated by the idiomatv
     */
    private String toIdiomaticForm(String attributeName) {

      if (!useIdiomaticForm) {
        return attributeName;
      }
      attributeName = attributeName.toLowerCase();
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
          if (!attributeName.startsWith(COMMENT)) {
            attributeName = "#" + attributeName;
            // its not already a comment - let's comment out the inclusion
          }
        }
        attributeName = toIdiomaticForm(attributeName);
        // we need to handle the name attribute slightly differently
        if (attributeName.equalsIgnoreCase(NAMEATTR)) {
          this.name = attribute.substring(sepPos).trim();
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

  }

  /**
   * We have extended the plugin to bring together those versions of
   * Plugin that are more specialized
   * such as needing singelton characteristics.
   */
  static class SpecialPlugin extends Plugin {

    /**
     * standard constructor where we'll cascade the informas
     * 
     * @param type the plugin type to be construb
     */
    public SpecialPlugin(PluginType type) {
      super(type);
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

    /**
     * This adds a plugin to the list of plugins, unless the
     * 
     * @param plugin the plugin object to be added to the the appropriate collection
     */
    public void add(Plugin plugin) {
      debug("merging special plugin");
      if (plugin.attributes != null) {
        Iterator keys = (plugin.attributes.keySet()).iterator();

        while (keys.hasNext()) {
          String key = (String) keys.next();
          if (this.attributes.containsKey(key)) {
            ArrayList<String> values = attributes.get(key);
            ArrayList<String> newValues = plugin.attributes.get(key);
            values.addAll(newValues);
            this.attributes.replace((String) key, values);
          }
        }
      }

    }
  }

  /**
   * We need the representation of services to differ from inputs, outputs and
   * filters. The level of YAML indentation is different, and we don't have a name
   * attribute
   */
  static class ServicePlugin extends SpecialPlugin {
    /**
     * default constructor which will push down the plugin type
     */
    public ServicePlugin() {
      super(PluginType.SERVICE);
    }

  }

  /**
   * We need the rewepresentation of services to differ from inputs, outputs and
   * filters. The level of YAML indentation is different, and we don't have a name
   * attribute
   */
  static class IncludesPlugin extends SpecialPlugin {
    /**
     * Standard construct which will pushdown to the basse class the type of okugin
     */
    public IncludesPlugin() {
      super(PluginType.INCLUDES);
    }

    /**
     * Constructor for the includes variant - which allows us to creat
     * 
     * @param line   the line of source to be processed
     * @param lineNo The lineNo reflects where this attribute originated from
     */
    public IncludesPlugin(String line, int lineNo) {
      super(PluginType.INCLUDES);
      add(line, lineNo);
    }

    /**
     * Provide identation that reflects the service block. Using space characters as
     * YAML doesn't allow tab characters
     * 
     * @return the indentation string
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

  /**
   * Depending upon the the label in the classic file, we need to decide which
   * group of plugins to add the latest definition to.
   * 
   * @param currentPlugin the active plugin we've been adding attributes to and
   *                      now needs attaching to the cirrec
   */
  private static void storePlugin(Plugin currentPlugin) {
    if (currentPlugin != null) {
      switch (currentPlugin.pluginType) {
        case FILTER:
          if (filters == null) {
            filters = new ArrayList<Plugin>();
          }
          filters.add(currentPlugin);
          break;
        case INPUT:
          if (inputs == null) {
            inputs = new ArrayList<Plugin>();
          }
          inputs.add(currentPlugin);
          break;
        case OUTPUT:
          if (outputs == null) {
            outputs = new ArrayList<Plugin>();
          }
          outputs.add(currentPlugin);
          break;
        case SERVICE:
          if (service == null) {
            service = (SpecialPlugin) currentPlugin;
            // service needs to be treated as a singleton
          } else {
            service.add((SpecialPlugin) currentPlugin);
            err("Merging a service plugin");
          }
          break;
        case INCLUDES:
          if (includes == null) {
            includes = (IncludesPlugin) currentPlugin;
          } else {
            includes.add((IncludesPlugin) currentPlugin);
          }
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

      debug("consume [" + lineCount + "]:" + line);

      if (line.length() > 0) {
        if (line.toLowerCase().startsWith(INCLUDECLASSIC)) {
          if (includes == null) {
            includes = new IncludesPlugin(line, lineCount);
          } else {
            includes.add(line, lineCount);
          }
        } else if (line.equalsIgnoreCase(SERVICECLASSIC)) {
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
    if ((service != null) && (service.attributeCountByType() > 0)) {
      outFile.write(SERVICEYAMLLBL);
      outFile.write(service.write());
    }
    if ((includes != null) && (includes.attributeCountByType() > 0)) {
      outFile.write(NL);
      outFile.write(INCLUDES_LBL);
      outFile.write(NL);
      outFile.write(includes.write());
    }

    outFile.write(NL);
    outFile.write(PIPELINEYAMLLBL);

    if ((inputs != null) && (!inputs.isEmpty())) {
      writePlugins(inputs, outFile, INPUTSYAML);
    }

    if ((filters != null) && (!filters.isEmpty())) {
      writePlugins(filters, outFile, FILTERSYAML);
    }

    if ((outputs != null) && (!outputs.isEmpty())) {
      writePlugins(outputs, outFile, OUTPUTSYAML);
    }

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
   * @param inFileName  name of the classic file to be processed
   * 
   * @param outFileName name of the file we're going to write YAML to
   */
  private static void processor(String inFileName, String outFileName) {
    BufferedWriter outFile = null;
    BufferedReader br = null;
    FileReader fr = null;
    FileWriter fwr = null;
    try {
      File inFile = new File(inFileName);
      if (!inFile.exists()) {
        err("Cant locate input file:" + inFileName);
        return;
      }
      fr = new FileReader(inFile);
      info("InputFile:" + inFileName + " --> " + outFileName);
      br = new BufferedReader(fr);
      consumeClassicFile(br);

      info("Plugin stats:");
      if (inputs != null) {
        info("Inputs:" + inputs.size());
      }
      if (outputs != null) {
        info("Outputs:" + outputs.size());
      }
      if (filters != null) {
        info("Filters:" + filters.size());
      }
      info("---" + NL);

      fwr = new FileWriter(outFileName);
      outFile = new BufferedWriter(fwr);
      writePipelineOutput(outFile);
      outFile.flush();
      outFile.close();
      fwr.close();
      br.close();
      fr.close();

    } catch (Exception err) {
      err("Processor error: " + err.toString());
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

  /**
   * Generates the current date and time as a string
   * 
   * @return the date time in a formatted string
   */
  static String getDateStr() {
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    LocalDateTime now = LocalDateTime.now();
    return (dtf.format(now));
  }

  /**
   * Checks for the environment variable that control whether we log to debug
   * level. We've not adopted a logging framework so that we can keep the code as
   * a single class. This means we can run without needing to have a Mave or
   * Gradle
   * 
   * @return returns the boolean indicating if debug is enabled
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
   * 
   * @return true if we find the flag for producing the report
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
   * 
   * @return the prefix path
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
   * @return flag indicating whether to use idiomatic form
   */
  private static boolean useIdiomatricForm() {
    boolean flag = false;
    String idiomaticFlagStr = System.getenv(FLB_IDIOMATICFORM);
    logToFile = false;
    if ((idiomaticFlagStr != null) && (idiomaticFlagStr.trim().equalsIgnoreCase(TRUE))) {
      flag = true;
    }
    debug("Env flag for using idomatic form to file set to " + idiomaticFlagStr);

    useIdiomaticForm = flag;
    return flag;
  }

  /**
   * Cleans up a string by trimming if not null. If the result of trim is 0 length
   * string - return as null
   * 
   * @param in the string to clean
   * @return cleaned string or null
   */
  private static String cleanStr(String in) {
    if (in == null) {
      return in;
    }
    in = in.trim();
    if (in.length() == 0) {
      return null;
    }
    return in;
  }

  /**
   * Process the conversion.list file building the files into an arrayList of file
   * names
   * 
   * @return a list of files or null
   */
  private static ArrayList<String> conversionListFiles() {
    ArrayList<String> conListFiles = null;
    File conversionList = new File(getPathPrefix() + CONVERSION_LIST);

    if (conversionList.exists()) {
      conListFiles = new ArrayList<String>();
      try {
        FileReader fr = new FileReader(conversionList);
        BufferedReader br = new BufferedReader(fr);
        debug("found conversion list file");
        String inFileName = null;
        while ((inFileName = cleanStr(br.readLine())) != null) {
          conListFiles.add(inFileName);
          debug("ConversionList identified filename:" + inFileName);
        }
        br.close();
        fr.close();
      } catch (IOException ioErr) {
        err("IO Error processing conversion list:" + ioErr.toString());
      }
      if (conListFiles.isEmpty()) {
        conListFiles = null;
      }

    }

    return conListFiles;
  }

  /**
   * Retrieve the name of the file to process from the environment variable.
   * Return in a list of files or null
   * 
   * @return list with the file name or null
   */
  private static ArrayList<String> envFiles() {
    ArrayList<String> envFiles = null;
    String inFileName = System.getenv(FLB_CLASSIC_FN);
    if (inFileName == null) {
      debug("No env var");
    } else {
      inFileName = inFileName.trim();
      if (inFileName.length() == 0) {
        debug("Env var is empty");
        inFileName = null;
      }
    }
    if (inFileName != null) {
      debug("Env file set to: " + inFileName);
      envFiles = new ArrayList<String>();
      envFiles.add(inFileName);
    }
    return envFiles;
  }

  /**
   * Return a list of files that may be included in the args
   * 
   * TODO extend so that we can handle multiple CLI files
   * 
   * @param args command line args
   * @return list of filenames or null
   */
  private static ArrayList<String> cliFiles(String[] args) {
    ArrayList<String> cliFiles = null;
    if ((args != null) && (args.length > 0)) {
      String inFileName = args[0].trim();
      if ((!inFileName.equalsIgnoreCase(HELP)) && (inFileName.length() > 0)) {
        cliFiles = new ArrayList<String>();
        cliFiles.add(inFileName);
      }
    }
    return cliFiles;
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
   * @param args the command line args
   */
  public static void main(String[] args) {
    if ((args != null) && (args.length > 0) && (args[0].trim().equalsIgnoreCase(HELP))) {
      printHelp();
      System.exit(0);
    }
    ArrayList<String> filesList = null;

    info("Fluent Bit Converter starting ...");
    checkDebug();
    useIdiomatricForm();
    try {
      boolean more = false;

      filesList = cliFiles(args);
      if (filesList == null) {
        filesList = envFiles();
      }
      if (filesList == null) {
        filesList = conversionListFiles();
      }

      if (filesList != null) {
        String inFileName = null;
        Iterator<String> iter = filesList.iterator();
        while (iter.hasNext()) {
          inFileName = getPathPrefix() + iter.next();
          debug("Preparing for " + inFileName);
          inputs = null;
          outputs = null;
          filters = null;
          service = null;
          includes = null;

          if (checkReportToFile()) {
            File reportFile = new File(getOutFile(inFileName) + REPORT_EXTN);
            converterReport = new FileWriter(reportFile);
            converterReport.write("Execution date:" + getDateStr() + NL);
          }
          processor(inFileName, getOutFile(inFileName));

          if (converterReport != null) {
            converterReport.flush();
            converterReport.close();
            converterReport = null;
          }
        }
      }
    } catch (

    Exception err) {
      err(err.getMessage());
      err.printStackTrace();
    }
  }

  /**
   * Generates the CLI help
   */
  static void printHelp() {
    final String pt = " --> ";
    System.out.println(NL);
    System.out.println("FLBConverter help");
    System.out.println(HELP + pt + "This information");
    System.out.println("<filename>" + pt + "Process the file identified by <filename>");
    System.out.println(FLB_CONVERT_DEBUG + pt + FLB_CONVERT_DEBUG_HELP);
    System.out.println(FLB_REPORT_FILE + pt + FLB_REPORT_FILE_HELP);
    System.out.println(FLB_PATH_PREFIX + pt + FLB_PATH_PREFIX_HELP);
    System.out.println(FLB_IDIOMATICFORM + pt + FLB_IDIOMATICFORM_HELP);
    System.out.println(FLB_CLASSIC_FN + pt + FLB_CLASSIC_FN_HELP);
    System.out.println(NL);
    System.out.println(CONVERSION_LIST + pt + CONVERSION_LIST_HELP);
    System.out.println(NL);

  }
}