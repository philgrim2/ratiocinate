package live.thought.rationalize;

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

public class Rationalize
{
  /** RELEASE VERSION */
  public static final String VERSION = "v0.1";

  private static Random      random  = new Random(System.currentTimeMillis());

  /** Connection for Thought daemon */
  private ThoughtRPCClient   client;

  private Config             config;

  public Rationalize(Config config)
  {
    this.config = config;
    
    URL url = null;
    try
    {
      StringBuilder sb = new StringBuilder("http://").append(config.getUser()).append(":")
          .append(config.getPassword()).append("@").append(config.getHost()).append(":")
          .append(config.getPort()).append("/");
      url = new URL(sb.toString());
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

  public static void main(String[] args)
  {
    Config config = new Config(args);
    Rationalize app = new Rationalize(config);
    app.run();
    Console.end();

  }
}
