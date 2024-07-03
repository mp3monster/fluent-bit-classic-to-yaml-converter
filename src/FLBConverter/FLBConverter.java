package FLBConvertor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

class FLBConverter {

  private static final String NL = "\n";
  private static final String FLB_REPORT_FILE = "FLB_REPORT_FILE";
  private static final String TRUE = "true";
  private static final String FLB_CONVERT_DEBUG = "FLB_CONVERT_DEBUG";
  private static final String CONVERSION_LIST = "conversion.list";
  private static final String PIPELINEYAMLLBL = "pipeline:\n";
  private static final String SERVICEYAMLLBL = "service:\n";
  private static final String FILTERCLASSIC = "[FILTER]";
  private static final String OUTPUTCLASSIC = "[OUTPUT]";
  private static final String INPUTCLASSIC = "[INPUT]";
  private static final String SERVICECLASSIC = "[SERVICE]";
  private static final String OUTPUTSYAML = "outputs";
  private static final String FILTERSYAML = "filters";
  private static final String INPUTSYAML = "inputs";

  private static final String FLB_CLASSIC_FN = "FLBClassicFN";

  private static boolean debug = false;
  private static boolean logToFile = false;
  private static FileWriter converterReport = null;

  /*
   * Define the different plugin types.
   */
  enum PluginType {
    INPUT, OUTPUT, FILTER, SERVICE
  };

  static class HashMapArray extends HashMap<String, ArrayList<String>> {
  }

  /**
   * @param msg
   */
  static void debug(String msg) {
    if (debug) {
      final String logStr = "debug:" + msg;
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
   * @param msg
   */
  static void info(String msg) {
    final String logStr = "info:" + msg;
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
   * @param msg
   */
  static void err(String msg) {
    final String logStr = "ERROR:" + msg;
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
   * an instance of this class
   */
  static class Plugin {
    private static final String WILDCARD = "*";
    private static final String DUMMYATTR = "dummy";
    private static final String COMMENT = "#";
    private static final String INCLUDE = "@include";
    private static final String NAMEATTR = "name";
    public PluginType pluginType;
    private String name = null;
    private static final String SEPARATOR = " ";
    static final int PLUGININDENT = 1;
    static final int NAMEINDENT = 2;
    static final int ATTRIBUTEINDENT = 3;

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

    static boolean checkForInclusion(String line, int lineNo) {
      boolean found = false;
      String tempLine = line.toLowerCase();
      if (tempLine.contains(INCLUDE)) {
        info("Warning: @include found at line " + lineNo + "  >>  " + line);
        found = true;
      }

      return found;
    }

    public void add(String attribute, int lineNo) {
      if (attribute == null) {
        return;
      }
      attribute = attribute.trim();

      if (attribute.length() == 0) {
        return;
      }
      checkForInclusion(attribute, lineNo);

      String attributeName = null;
      int sepPos = 0;
      if (attribute.startsWith(COMMENT)) {
        sepPos = 1;
      } else {
        sepPos = attribute.indexOf(SEPARATOR);
      }
      if (sepPos > 0) {
        attributeName = attribute.substring(0, sepPos).trim();
        if (attributeName.equalsIgnoreCase(NAMEATTR)) {
          this.name = attribute.substring(sepPos);
        } else {
          String attributeValue = attribute.substring(sepPos).trim();
          if (attributeValue.equalsIgnoreCase(WILDCARD)) {
            attributeValue = "\"*\"";
          }
          if ((attributeName.equalsIgnoreCase(DUMMYATTR)) && (!attributeValue.startsWith("\'"))) {
            attributeValue = "\'" + attributeValue + "\'";
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

    String writePrefix() {
      return indenter(NAMEINDENT) + "- name: " + this.name + NL;
    }

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

    public String getName() {
      return pluginType.toString();
    }
  }

  static class ServicePlugin extends Plugin {
    public ServicePlugin() {
      super(PluginType.SERVICE);
    }

    @Override
    String indenter(int depth) {
      return "  ";
    }

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

  private static void writePlugins(ArrayList<Plugin> plugins, BufferedWriter outFile, String label) throws IOException {
    if (!plugins.isEmpty()) {
      outFile.write("  " + label + ":\n");
      Iterator<Plugin> iter = plugins.iterator();
      while (iter.hasNext()) {
        outFile.write(iter.next().write() + NL);
      }
    }

  }

  private static void writeOutput(BufferedWriter outFile) throws IOException {
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

  private static String getOutFile(String inFileName) {
    String outFileName = inFileName;

    final String postfix = "conf";
    if (inFileName.endsWith(postfix)) {
      outFileName = outFileName.substring(0, inFileName.length() - postfix.length());
    }
    return outFileName + "yaml";
  }

  private static void processor(String inFileName, String outFileName) {
    getOutFile(inFileName);
    BufferedWriter outFile = null;
    FileReader fr = null;
    try {
      fr = new FileReader(inFileName);
      info("InputFile:" + inFileName + " --> " + outFileName);
      consumeClassicFile(new BufferedReader(fr));

      info("Inputs:" + inputs.size() + NL + "Filters:" + filters.size() + NL + "Outputs:" + outputs.size() + NL);

      outFile = new BufferedWriter(new FileWriter(outFileName));
      writeOutput(outFile);
      fr.close();

    } catch (

    Exception err) {
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
        err("finally fr err closing file reader\n" + ioErr.getMessage());
      }
    }
  }

  static String getDateStr() {
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    LocalDateTime now = LocalDateTime.now();
    return (dtf.format(now));
  }

  private static boolean checkDebug() {
    String debugFlagStr = System.getenv(FLB_CONVERT_DEBUG);
    if ((debugFlagStr != null) && (debugFlagStr.trim().equalsIgnoreCase(TRUE))) {
      debug = true;
      System.out.println("Env flag for debug set to " + debugFlagStr);
    }
    return debug;
  }

  private static boolean checkReportToFile() {
    String reportFlagStr = System.getenv(FLB_REPORT_FILE);
    if ((reportFlagStr != null) && (reportFlagStr.trim().equalsIgnoreCase(TRUE))) {
      logToFile = true;
      System.out.println("Env flag for reporting to file set to " + reportFlagStr);
    }
    return logToFile;
  }

  private static String getPathPrefix() {
    String pathPrefix = System.getenv("FLB_PATH_PREFIX");
    if (pathPrefix == null) {
      pathPrefix = "";
    } else {
      pathPrefix = pathPrefix.trim();
      if (!pathPrefix.endsWith("\\")) {
        pathPrefix = pathPrefix + "\\";
      }
    }
    return pathPrefix;
  }

  public static void main(String[] args) {
    info("Fluent B it Converter starting ,,,");
    checkDebug();
    String inFileName = null;
    try {
      boolean more = false;
      File conversionList = new File(CONVERSION_LIST);
      BufferedReader br = null;
      FileReader fr = null;

      if (conversionList.exists()) {

        debug("found conversion list file");
        fr = new FileReader(conversionList);
        br = new BufferedReader(fr);
        inFileName = br.readLine().trim();
        debug("Conversion list file points to:" + inFileName);
      } else {
        debug("No Conversion list");
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
          File reportFile = new File(getOutFile(inFileName) + ".report");
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
}
