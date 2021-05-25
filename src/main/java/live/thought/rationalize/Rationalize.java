package live.thought.rationalize;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import live.thought.thought4j.ThoughtRPCClient;

public class Rationalize
{
  /** RELEASE VERSION */
  public static final String VERSION    = "v0.1";
  public static final int    CHUNK_SIZE = 10000;

  /** Connection for Thought daemon */
  private ThoughtRPCClient   client;

  private Config             config;

  public Rationalize(Config config)
  {
    this.config = config;

    URL url = null;
    try
    {
      StringBuilder sb = new StringBuilder("http://").append(config.getUser()).append(":").append(config.getPassword())
          .append("@").append(config.getHost()).append(":").append(config.getPort()).append("/");
      url = new URL(sb.toString());
      client = new ThoughtRPCClient(url);
    }
    catch (MalformedURLException e)
    {
      throw new IllegalArgumentException("Invalid URL: " + url);
    }
  }

  private List<FundingLine> parseInputFile(File inputFile)
  {
    List<FundingLine> lines = new ArrayList<FundingLine>();
    try (Scanner scanner = new Scanner(inputFile);)
    {
      while (scanner.hasNextLine())
      {
        String line = scanner.nextLine().trim();
        // Ignore blank lines and comments
        if (line.length() > 0 && !line.startsWith("#"))
        {
          String[] values = line.split(",");
          if (values.length == 2)
          {
            FundingLine f = new FundingLine();
            f.setAmount(Double.parseDouble(values[0].trim()));
            f.setAccounts(Integer.parseInt(values[1].trim()));
            lines.add(f);
          }
        }
      }
    }
    catch (FileNotFoundException e)
    {
      // Should already be handled, just return empty list.
    }

    return lines;
  }

  public void run()
  {
    Console.output("Beginning Rationalization.");
    boolean           moreElectricity = true;
    File              inputFile       = new File(config.getAccountFileName());
    File              outputFile      = new File(config.getOutputFileName());
    PrintWriter       pw              = null;
    List<FundingLine> fundingLines    = null;

    if (inputFile.canRead())
    {
      try
      {
        fundingLines = parseInputFile(inputFile);
        if (fundingLines.isEmpty())
        {
          Console.output("No funding lines found in input file.");
          moreElectricity = false;
        }
      }
      catch (Exception e)
      {
        e.printStackTrace();
        // Better error message
        moreElectricity = false;
      }
    }

    // TODO: Validate that the source account has enough coin.
    String sourceAccount = config.getSourceAccount() == null ? "" : config.getSourceAccount();
    double total         = 0.0;
    for (FundingLine line : fundingLines)
    {
      total += line.getAmount() * line.getAccounts();
    }
    double source = client.getBalance(sourceAccount);
    if (total > source)
    {
      Console.output("Funding total specified greater than source account balance.");
      moreElectricity = false;
    }
    else
    {
      try
      {
        Console.output("Sufficient funds verified.");
        outputFile.createNewFile();
        pw = new PrintWriter(outputFile);
      }
      catch (Exception e)
      {
        Console.output("Unable to create output file.");
        moreElectricity = false;
      }
    }

    while (moreElectricity)
    {
      for (FundingLine line : fundingLines)
      {
        int lineIndex = 1;
        Console.output("Beginning funding line " + Integer.toString(lineIndex));
        int accounts = line.getAccounts();
        for (int idx = 1; idx <= accounts; idx++)
        {
          String     currentName = String.format("%s-%d-%d", config.getPrefix(), lineIndex, idx);
          // Create a new private key and import it into the wallet for safety
          PrivateKey accountKey  = new PrivateKey(config.isTestnet());
          client.importPrivKey(accountKey.toString(), currentName, false);
          // Get a public key
          List<String> accountAddrs = client.getAddressesByAccount(currentName);
          // Send the coin in chunks to avoid overly large transaction.
          double       remaining    = line.getAmount();
          double       sent         = 0.0;
          int          chunks       = ((int) line.getAmount()) / CHUNK_SIZE + 1;
          for (int j = 0; j < chunks; j++)
          {
            double chunk = remaining < CHUNK_SIZE ? remaining : CHUNK_SIZE;
            try
            {
              client.sendFrom(sourceAccount, accountAddrs.get(0), line.getAmount(), 6);
              remaining = remaining - chunk;
              sent += chunk;
            }
            catch (Exception e)
            {
              Console.output("Error sending chunk.");
              e.printStackTrace();
              moreElectricity = false;
              break;
            }
          }
          if (sent > 0)
          {
            // Write to the output file.
            pw.append(String.format("%s,%s,%s\n", accountKey.toString(), accountAddrs.get(0), Double.toString(sent)));
            Console.output("Sent " + Double.toString(sent) + " to account " + currentName + "(" + accountAddrs.get(0) + ")");
          }
          lineIndex += 1;
        }
      }
      moreElectricity = false;
    }
    pw.close();
    Console.output("Rationalization complete.");
  }

  public static void main(String[] args)
  {
    Config      config = new Config(args);
    Rationalize app    = new Rationalize(config);
    app.run();
    Console.end();
  }
}
