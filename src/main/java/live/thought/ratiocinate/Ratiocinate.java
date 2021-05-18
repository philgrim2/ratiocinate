package live.thought.ratiocinate;

import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.Random;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import live.thought.thought4j.ThoughtRPCClient;

public class Ratiocinate
{
  /** RELEASE VERSION */
  public static final String               VERSION               = "v0.1";
  /** Options for the command line parser. */
  protected static final Options           options               = new Options();
  /** The Commons CLI command line parser. */
  protected static final CommandLineParser gnuParser             = new GnuParser();
  /** Default values for connection. */
  private static final String              DEFAULT_HOST          = "localhost";
  private static final String              DEFAULT_PORT          = "10617";
  private static final String              DEFAULT_USER          = "user";
  private static final String              DEFAULT_PASS          = "password";
  private static final String              DEFAULT_INTERVAL      = Integer.toString(120);                 // 2 minutes


  private static final String              HOST_PROPERTY         = "host";
  private static final String              PORT_PROPERTY         = "port";
  private static final String              USER_PROPERTY         = "user";
  private static final String              PASS_PROPERTY         = "password";
  private static final String              ACCOUNT_PROPERTY      = "account";
  private static final String              INTR_PROPERTY         = "interval";
  private static final String              HELP_OPTION           = "help";
  private static final String              CONFIG_OPTION         = "config";

  private static Random                    random                = new Random(System.currentTimeMillis());

  /** Connection for Thought daemon */
  private ThoughtRPCClient                 client;

  private boolean                          testnet;  
  private int                              interval;


  /** Set up command line options. */
  static
  {
    options.addOption("h", HOST_PROPERTY, true, "Thought RPC server host (default: localhost)");
    options.addOption("P", PORT_PROPERTY, true, "Thought RPC server port (default: 10617)");
    options.addOption("u", USER_PROPERTY, true, "Thought server RPC user");
    options.addOption("p", PASS_PROPERTY, true, "Thought server RPC password");
    options.addOption("a", ACCOUNT_PROPERTY, true, "Thought wallet account name to obtain stake from");
    options.addOption("i", INTR_PROPERTY, true, "Interval in seconds between rounds (default: 2 minutes)");
    options.addOption("H", HELP_OPTION, true, "Displays usage information");
    options.addOption("f", CONFIG_OPTION, true,
        "Configuration file to load options from.  Command line options override config file.");
  }

  public Ratiocinate(Properties props)
  {
    String host = props.getProperty(HOST_PROPERTY, DEFAULT_HOST);
    int    port = Integer.parseInt(props.getProperty(PORT_PROPERTY, DEFAULT_PORT));
    String user = props.getProperty(USER_PROPERTY, DEFAULT_USER);
    String pass = props.getProperty(PASS_PROPERTY, DEFAULT_PASS);
    interval = Integer.parseInt(props.getProperty(INTR_PROPERTY, DEFAULT_INTERVAL));
    
    if (props.getProperty(PORT_PROPERTY, DEFAULT_PORT).startsWith("10"))
    {
      testnet = false;
    }
    else
    {
      testnet = true;
    }

    URL url = null;
    try
    {
      url = new URL("http://" + user + ':' + pass + "@" + host + ":" + port + "/");
      client = new ThoughtRPCClient(url);
    }
    catch (MalformedURLException e)
    {
      throw new IllegalArgumentException("Invalid URL: " + url);
    }
  }

  public void run()
  {
    boolean moreElectricity = true;

    while (moreElectricity)
    {

    }
  }

  protected static void usage()
  {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("Ratiocinate", options);
  }

  public static void main(String[] args)
  {

    CommandLine commandLine = null;

    try
    {
      Properties props = new Properties();
      // Read the command line
      commandLine = gnuParser.parse(options, args);
      // Check for the help option
      if (commandLine.hasOption(HELP_OPTION))
      {
        usage();
        System.exit(0);
      }
      // Check for a config file specified on the command line
      if (commandLine.hasOption(CONFIG_OPTION))
      {
        try
        {
          props.load(new FileInputStream(new File(commandLine.getOptionValue(CONFIG_OPTION))));
        }
        catch (Exception e)
        {
          Console.output(String.format("@|red Specified configuration file %s unreadable or not found.|@",
              commandLine.getOptionValue(CONFIG_OPTION)));
          System.exit(1);
        }
      }
      // Command line options override config file values
      if (commandLine.hasOption(HOST_PROPERTY))
      {
        props.setProperty(HOST_PROPERTY, commandLine.getOptionValue(HOST_PROPERTY));
      }
      if (commandLine.hasOption(PORT_PROPERTY))
      {
        props.setProperty(PORT_PROPERTY, commandLine.getOptionValue(PORT_PROPERTY));
      }
      if (commandLine.hasOption(USER_PROPERTY))
      {
        props.setProperty(USER_PROPERTY, commandLine.getOptionValue(USER_PROPERTY));
      }
      if (commandLine.hasOption(PASS_PROPERTY))
      {
        props.setProperty(PASS_PROPERTY, commandLine.getOptionValue(PASS_PROPERTY));
      }
      if (commandLine.hasOption(ACCOUNT_PROPERTY))
      {
        props.setProperty(ACCOUNT_PROPERTY, commandLine.getOptionValue(ACCOUNT_PROPERTY));
      }
      
      if (commandLine.hasOption(INTR_PROPERTY))
      {
        props.setProperty(INTR_PROPERTY, commandLine.getOptionValue(INTR_PROPERTY));
      }
      
      String account = props.getProperty(ACCOUNT_PROPERTY);
      if (null == account)
      {
        Console.output("@|red No Thought stake account specified.|@");
        usage();
        System.exit(1);
      }

      Ratiocinate app = new Ratiocinate(props);
      app.run();
      Console.end();
    }
    catch (ParseException pe)
    {
      System.err.println(pe.getLocalizedMessage());
      usage();
    }
    catch (Exception e)
    {
      System.err.println(e.getLocalizedMessage());
      e.printStackTrace(System.err);
    }
  }
}
