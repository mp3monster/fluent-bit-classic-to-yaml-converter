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

    HashMap<String, String> attributes = new HashMap<String, String>();

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

    public void add(String attribute) {
      assert (attribute == null) : "Received null string";
      attribute = attribute.trim();
      assert (attribute.length() > 0) : "Received empty string";

      String attributeName = null;
      int sepPos = attribute.indexOf(SEPARATOR);
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
          attributes.put(attributeName, attributeValue);
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
        line = indenter(ATTRIBUTEINDENT) + key + ": " + attributes.get(key);
        debug(line);
        YAMLoutput = YAMLoutput + line + "\n";
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

  private static ArrayList<Plugin> inputs = null; // new ArrayList<Plugin>();
  private static ArrayList<Plugin> outputs = null; // new ArrayList<Plugin>();
  private static ArrayList<Plugin> filters = null; // new ArrayList<Plugin>();

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
        if (line.startsWith("#")) {
          // add the comment to the current plugin
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
            currentPlugin.add(line);
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

    String postfix = "conf";
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
      consumeClassicFile(new BufferedReader(fr));

      info("InputFile:" + inFileName + " --> " + outFileName);
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
    String debugFlag = System.getenv("FLB_CONVERT_DEBUG");
    if ((debugFlag != null) && (debugFlag.trim().equalsIgnoreCase("true"))) {
      debug = true;

      debug("Env flag for debug set");
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