package live.thought.rationalize;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import live.thought.thought4j.ThoughtRPCClient;
import live.thought.thought4j.ThoughtRPCException;
import live.thought.thought4j.ThoughtClientInterface.BasicTxInput;
import live.thought.thought4j.ThoughtClientInterface.BasicTxOutput;
import live.thought.thought4j.ThoughtClientInterface.FundRawTransactionOptions;
import live.thought.thought4j.ThoughtClientInterface.FundedRawTransaction;
import live.thought.thought4j.ThoughtClientInterface.TxInput;
import live.thought.thought4j.ThoughtClientInterface.TxOutput;
import live.thought.thought4j.ThoughtClientInterface.Unspent;

public class Rationalize
{
  /** RELEASE VERSION */
  public static final String VERSION         = "v0.1";
  public static final int    CHUNK_SIZE      = 10000;
  public static final int    CONF_CHUNK_SIZE = 2000;

  /** Connection for Thought daemon */
  private ThoughtRPCClient   client;
  private Config             config;
  private List<Unspent>      unspentCache;

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

  public void run()
  {
    Console.output("Beginning Rationalization.");
    boolean           moreElectricity = true;
    File              inputFile       = new File(config.getFundingFileName());
    File              outputFile      = new File(config.getOutputFileName());
    PrintWriter       pw              = null;
    List<FundingLine> fundingLines    = null;
    String[]          sourceAddresses = config.getSourceAddresses();

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

    double total = 0.0;
    int numSplits = 0;
    if (moreElectricity)
    {
      // Validate that the source addresses have enough coin.  
      for (FundingLine line : fundingLines)
      {
        numSplits += line.getAccounts();
        total += line.getAmount() * numSplits;        
      }
      double              source   = 0.0;
      Map<String, Double> balances = client.listAddressBalances(0.01);
      for (String s : sourceAddresses)
      {
        Double val = balances.get(s);
        if (null != val)
          source += val.doubleValue();
      }
      if (total + (numSplits * 0.01) > source)  // Fudge factor for transaction fees
      {
        Console.output("Funding total specified greater than source account balance.");
        Console.output("Total: " + Double.toString(total) + ", Balance: " + Double.toString(source));
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
    }
    
    if (moreElectricity)
    {
      if (config.getFundingStrategy().equals(Config.NEWEST_FIRST))
      {
        unspentCache = getUnspentNewestFirst(total + (numSplits * 0.01), sourceAddresses); // Fudge for 
      }
      else
      {
        unspentCache = getUnspentOldestFirst(total + (numSplits * 0.01), sourceAddresses);
      }
      if (null == unspentCache || unspentCache.isEmpty())
      {
        Console.output("Unable to retrieve sufficient unspent inputs.");
        moreElectricity = false;
      }
    }  

    if (moreElectricity)
    {
      int lineIndex = 1;
      for (FundingLine line : fundingLines)
      {
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
              sendChunk(accountAddrs.get(0), line.getAmount());
              remaining = remaining - chunk;
              sent += chunk;
              Thread.sleep(200);
            }
            catch (InterruptedException i)
            {
              // Don't care.
            }
            catch (ThoughtRPCException e)
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
            Console.output(
                "Sent " + Double.toString(sent) + " to account " + currentName + "(" + accountAddrs.get(0) + ")");
          }
        }
        if (!moreElectricity)
        {
          break;
        }
        lineIndex += 1;
      }
    }
    if (null != pw)
    {
      pw.close();
    }
    Console.output("Rationalization complete.");
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

  private List<Unspent> getUnspentOldestFirst(double balanceNeeded, String[] addresses)
  {
    List<Unspent> retval = new ArrayList<Unspent>();
    Console.debug("Getting block count.", 1);
    int maxConfirmations = client.getBlockCount();
    Console.debug("Found " + maxConfirmations + " blocks.", 1);

    double balance = 0.0;
    Console.output("Building unspent cache.");
    int minconf = maxConfirmations - CONF_CHUNK_SIZE;

    while (balance < balanceNeeded)
    {
      Console.debug("  Fetching unspent at minconf of " + minconf, 1);
      List<Unspent> unspent = client.listUnspent(minconf, maxConfirmations, addresses);
      Console.debug("  Transaction count: " + unspent.size(), 1);

      for (Unspent u : unspent)
      {
        if (u.solvable() && u.spendable())
        {
          balance += u.amount();
          retval.add(u);
        }
      }
      maxConfirmations = minconf - 1;
      if (maxConfirmations <= 6)
        break;
      minconf -= CONF_CHUNK_SIZE;
      if (minconf < 6)
        minconf = 6;
    }
    Console.debug("Final transaction count: " + retval.size(), 1);
    Console.debug("Spendable balance: " + balance, 1);
    return retval;
  }

  private List<Unspent> getUnspentNewestFirst(double balanceNeeded, String[] addresses)
  {
    List<Unspent> retval = new ArrayList<Unspent>();
    Console.debug("Getting block count.", 1);
    int maxblocks = client.getBlockCount();
    Console.debug("Found " + maxblocks + " blocks.", 1);

    double balance = 0.0;
    Console.output("Building unspent cache.");
    int minconf = 6;
    int maxconf = minconf + CONF_CHUNK_SIZE;

    while (balance < balanceNeeded)
    {
      Console.debug("  Fetching unspent at maxconf of " + maxconf, 1);
      List<Unspent> unspent = client.listUnspent(minconf, maxconf, addresses);
      Console.debug("  Transaction count: " + unspent.size(), 1);

      for (Unspent u : unspent)
      {
        if (u.solvable() && u.spendable())
        {
          balance += u.amount();
          retval.add(u);
        }
      }
      minconf = maxconf + 1;
      maxconf = maxconf + CONF_CHUNK_SIZE;
      if (maxconf > maxblocks)
        break;
      else if (maxconf + CONF_CHUNK_SIZE > maxblocks)
        maxconf = maxblocks;
    }
    Console.debug("Final transaction count: " + retval.size(), 1);
    Console.debug("Spendable balance: " + balance, 1);
    return retval;
  }

  private double sendChunk(String destinationAddr, double amount)
  {
    double actual = amount;
    
    Console.debug("Building raw transaction.", 1);
    List<TxInput> inputs = new ArrayList<TxInput>();
    
    List<Unspent> spentCache = new ArrayList<Unspent>();
    double inputBalance = 0.0;
    for (Unspent u : unspentCache)
    {
      TxInput input = new BasicTxInput(u.txid(), u.vout());
      inputs.add(input);
      spentCache.add(u);
      inputBalance += u.amount();
      Console.debug("  Adding " + u.amount() + " THT to inputs.", 1);
      if (inputBalance > amount)
      {
        break;
      }
    }
     
    List<TxOutput> outputs = new ArrayList<TxOutput>();
    TxOutput output = new BasicTxOutput(destinationAddr, amount);
    outputs.add(output);
    
    Console.debug("Creating raw transaction.", 1);
    String transactionHex = client.createRawTransaction(inputs, outputs);
   
    Console.debug("Funding raw transaction.", 1);

    FundRawTransactionOptions opts = new FundRawTransactionOptions();
    opts.setChangeAddress(config.getSourceAddresses()[0]);
    
    FundedRawTransaction funded = client.fundRawTransaction(transactionHex, opts);
    actual += funded.fee();
    
    Console.debug("Signing raw transaction.", 1);
    String signedHex = client.signRawTransaction(funded.hex());

    Console.debug("Sending raw transaction.", 1);
    String txid = client.sendRawTransaction(signedHex);
    
    Console.debug("Sent transaction " + txid, 1);
    
    unspentCache.removeAll(spentCache);
    
    return actual;
  }
  
  public static void main(String[] args)
  {
    Config      config = new Config(args);
    Rationalize app    = new Rationalize(config);
    app.run();
    Console.end();
  }
}
