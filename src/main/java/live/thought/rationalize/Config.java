package live.thought.rationalize;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Config
{
  /** Options for the command line parser. */
  protected static final Options           options            = new Options();
  /** The Commons CLI command line parser. */
  protected static final CommandLineParser gnuParser          = new GnuParser();
  /** Default values for connection. */
  private static final String              DEFAULT_HOST       = "localhost";
  private static final String              DEFAULT_PORT       = "11617";
  private static final String              DEFAULT_USER       = "user";
  private static final String              DEFAULT_PASS       = "password";
  private static final String              DEFAULT_PROPERTIES = "rationalize.properties";
  private static final String              DEFAULT_INTERVAL   = Integer.toString(120);   // 2 minutes

  private static final String              HOST_PROPERTY      = "host";
  private static final String              PORT_PROPERTY      = "port";
  private static final String              USER_PROPERTY      = "user";
  private static final String              PASS_PROPERTY      = "password";
  private static final String              ACCOUNT_PROPERTY   = "account";
  private static final String              INTR_PROPERTY      = "interval";
  private static final String              HELP_OPTION        = "help";
  private static final String              CONFIG_OPTION      = "config";

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

  protected String            host;
  protected int               port;
  protected String            user;
  protected String            password;

  protected String            sourceAddress;
  protected double            sourceFundAmount;
  protected String            lowFundingPolicy;
  protected int               interval;

  protected List<FundingLine> fundingLines = new ArrayList<FundingLine>();

  public Config(String[] args)
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

      host = props.getProperty(HOST_PROPERTY, DEFAULT_HOST);
      port = Integer.parseInt(props.getProperty(PORT_PROPERTY, DEFAULT_PORT));
      user = props.getProperty(USER_PROPERTY, DEFAULT_USER);
      password = props.getProperty(PASS_PROPERTY, DEFAULT_PASS);
      interval = Integer.parseInt(props.getProperty(INTR_PROPERTY, DEFAULT_INTERVAL));
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

  public String getSourceAddress()
  {
    return sourceAddress;
  }

  public void setSourceAddress(String sourceAddress)
  {
    this.sourceAddress = sourceAddress;
  }

  public double getSourceFundAmount()
  {
    return sourceFundAmount;
  }

  public void setSourceFundAmount(double sourceFundAmount)
  {
    this.sourceFundAmount = sourceFundAmount;
  }

  public String getLowFundingPolicy()
  {
    return lowFundingPolicy;
  }

  public void setLowFundingPolicy(String lowFundingPolicy)
  {
    this.lowFundingPolicy = lowFundingPolicy;
  }

  public String getHost()
  {
    return host;
  }

  public void setHost(String host)
  {
    this.host = host;
  }

  public int getPort()
  {
    return port;
  }

  public void setPort(int port)
  {
    this.port = port;
  }

  public String getUser()
  {
    return user;
  }

  public void setUser(String user)
  {
    this.user = user;
  }

  public String getPassword()
  {
    return password;
  }

  public void setPassword(String password)
  {
    this.password = password;
  }

  public int getInterval()
  {
    return interval;
  }

  public void setInterval(int interval)
  {
    this.interval = interval;
  }

  public List<FundingLine> getFundingLines()
  {
    return fundingLines;
  }

  public void setFundingLines(List<FundingLine> fundingLines)
  {
    this.fundingLines = fundingLines;
  }

  public static void usage()
  {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("Rationalize", options);
  }

  static class FundingLine
  {
    protected double amount;
    protected int    accounts;

    public double getAmount()
    {
      return amount;
    }

    public void setAmount(double amount)
    {
      this.amount = amount;
    }

    public int getAccounts()
    {
      return accounts;
    }

    public void setAccounts(int accounts)
    {
      this.accounts = accounts;
    }
  }
}
