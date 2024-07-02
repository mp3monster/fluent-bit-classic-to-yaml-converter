package FLBConvertor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;

class FLBConverter {

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

  enum PluginType {
    INPUT, OUTPUT, FILTER, SERVICE
  };

  static class HashMapArray extends HashMap<String, ArrayList<String>> {
  }

  static void debug(String msg) {
    if (debug) {
      System.out.println("debug:" + msg);
    }
  }

  static void info(String msg) {
    System.out.println(msg);
  }

  static void err(String msg) {
    System.out.println("ERROR:" + msg);
  }

  static class Plugin {
    private static final String NAMEATTR = "name";
    public PluginType pluginType;
    private String name = null;
    private static final String SEPARATOR = " ";
    static final int PLUGININDENT = 1;
    static final int NAMEINDENT = 2;
    static final int ATTRIBUTEINDENT = 3;

    // HashMap<String, String> attributes = new HashMap<String,
    // ArrayList<String>>();
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
      if (tempLine.contains("@include")) {
        info("Warning: @include found at line " + lineNo + "  >>  " + line);
        found = true;
      }

      return found;
    }

    public void add(String attribute, int lineNo) {
      // assert (attribute == null) : "Received null string";
      if (attribute == null) {
        return;
      }
      attribute = attribute.trim();

      if (attribute.length() == 0) {
        return;
      }
      // assert (attribute.length() > 0) : "Received empty string";
      checkForInclusion(attribute, lineNo);

      String attributeName = null;
      int sepPos = 0;
      if (attribute.startsWith("#")) {
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
          if (attributeValue.equalsIgnoreCase("*")) {
            attributeValue = "\"*\"";
          }
          if ((attributeName.equalsIgnoreCase("dummy")) && (!attributeValue.startsWith("\'"))) {
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
      return indenter(NAMEINDENT) + "- name: " + this.name + "\n";
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
          if (key.equals("#")) {
            line = indenter(ATTRIBUTEINDENT) + key + value;
          } else {
            line = indenter(ATTRIBUTEINDENT) + key + ": " + value;
          }
          debug(line);
          YAMLoutput = YAMLoutput + line + "\n";
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
        outFile.write(iter.next().write() + "\n");
      }
    }

  }

  private static void writeOutput(BufferedWriter outFile) throws IOException {
    if (service != null) {
      outFile.write(SERVICEYAMLLBL);
      outFile.write(service.write());

      outFile.write("\n");
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

      info("Inputs:" + inputs.size() + "\n" + "Filters:" + filters.size() + "\n" + "Outputs:" + outputs.size() + "\n");

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

  private static void checkDebug() {
    String debugFlag = System.getenv(FLB_CONVERT_DEBUG);
    if ((debugFlag != null) && (debugFlag.trim().equalsIgnoreCase(TRUE))) {
      debug = true;
      System.out.println("Env flag for debug set to " + debugFlag);
    }
  }

  public static void main(String[] args) {
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
        debug("Converion list file points to:" + inFileName);
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
        inputs = new ArrayList<Plugin>();
        outputs = new ArrayList<Plugin>();
        filters = new ArrayList<Plugin>();
        processor(inFileName, getOutFile(inFileName));

        if (more) {
          inFileName = br.readLine().trim();
        } else {
          inFileName = null;
        }
      }
    } catch (Exception err) {
      err(err.getMessage());
      err.printStackTrace();
    }
  }
}
