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
  public static final String  VERSION         = "v0.1";
  public static final int     CHUNK_SIZE      = 10000;
  public static final int     CONF_CHUNK_SIZE = 2000;

  /** Connection for Thought daemon */
  private ThoughtRPCClient    client;
  private Config              config;
  private List<Unspent>       unspentCache;
  private List<FundingAction> actionList      = new ArrayList<FundingAction>();

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
    Console.output(String.format("@|bg_blue,fg_white rationalize %s: A coin management utility for Thought Network.|@", VERSION));
    long timestamp = System.currentTimeMillis();
    Console.output(String.format("Beginning Rationalization (timestamp %s).", Long.toString(timestamp)));
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
    if (moreElectricity)
    {
      int lineIndex = 1;
      // Create a list of funding actions to take
      for (FundingLine line : fundingLines)
      {
        int numAccounts = line.getAccounts();
        for (int i = 1; i <= numAccounts; i++)
        {
          String        name = String.format("%s-%s-%d-%d", config.getPrefix(), Long.toString(timestamp), lineIndex, i);
          FundingAction act  = new FundingAction(name, line.getAmount());
          actionList.add(act);
          total += line.getAmount();
        }
        lineIndex++;
      }
      // Validate that the source addresses have enough coin.
      double              source   = 0.0;
      Map<String, Double> balances = client.listAddressBalances(0.01);
      for (String s : sourceAddresses)
      {
        Double val = balances.get(s);
        if (null != val)
          source += val.doubleValue();
      }
      if (total + (actionList.size() * 0.01) > source) // Fudge factor for transaction fees
      {
        Console.output("Funding total specified greater than source account balance.");
        Console.output("Total: " + Double.toString(total) + ", Balance: " + Double.toString(source));
        moreElectricity = false;
      }
      else
      {
        Console.output("Sufficient funds verified.");
        try
        {       
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
      double totalCached = 0.0;
      double totalNeeded = total + (actionList.size() * 0.01); // Fudge for transaction fees
      if (config.getFundingStrategy().equals(Config.NEWEST_FIRST))
      {
        totalCached = getUnspentNewestFirst(totalNeeded, sourceAddresses); 
      }
      else
      {
        totalCached = getUnspentOldestFirst(totalNeeded, sourceAddresses);
      }
      if (null == unspentCache || unspentCache.isEmpty() || totalCached < totalNeeded)
      {
        Console.output("Unable to retrieve sufficient unspent inputs.");
        moreElectricity = false;
      }
      else
      {
        if (Console.getLevel() > 0)
        {
          for (Unspent u : unspentCache)
          {
            Console.debug(u, 1);
          }
        }
      }
    }

    // Handle the case of large unspent, where multiple outputs make more sense.
    if (moreElectricity)
    {
      Console.output("Searching for large unspent.");
      boolean searchFinished = false;
      while (!searchFinished)
      {
        List<FundingAction> searchResults = new ArrayList<FundingAction>();
        Unspent             large         = biggestUnspent();
        double              target        = large.amount();

        while (target > 1.0) // Fudge for transaction fees
        {
          FundingAction fa = biggestUpTo(target, searchResults);
          if (null == fa)
          {
            break;
          }
          else
          {
            searchResults.add(fa);
            target -= fa.getAmount();
          }
        }
        if (searchResults.size() == 0)
        {
          searchFinished = true;
        }
        else
        {
          for (FundingAction act : searchResults)
          {
            PrivateKey accountKey = new PrivateKey(config.isTestnet());
            client.importPrivKey(accountKey.toString(), act.getName(), false);
            act.setPrivateKey(accountKey.toString());
            // Get a public key
            List<String> accountAddrs = client.getAddressesByAccount(act.getName());
            act.setPublicKey(accountAddrs.get(0));
          }
          try
          {
            sendLarge(large, searchResults);
          }
          catch (ThoughtRPCException e)
          {
            Console.output("@|red Error sending large transaction. |@");
            e.printStackTrace();
            moreElectricity = false;
            break;
          }
          for (FundingAction act : searchResults)
          {
            pw.append(act.toString());
            Console.output("Sent " + Double.toString(act.getAmount()) + " to account " + act.getName() + "("
                + act.getPublicKey() + ")");
          }
          actionList.removeAll(searchResults);
          if (actionList.isEmpty())
          {
            searchFinished = true;
          }
        }
      }
      Console.output("Finished large unspent.");
    }

    // Fund the rest with smaller inputs
    if (moreElectricity)
    {
      for (FundingAction act : actionList)
      {
        // Create a new private key and import it into the wallet for safety
        PrivateKey accountKey = new PrivateKey(config.isTestnet());
        client.importPrivKey(accountKey.toString(), act.getName(), false);
        act.setPrivateKey(accountKey.toString());
        // Get a public key
        List<String> accountAddrs = client.getAddressesByAccount(act.getName());
        act.setPublicKey(accountAddrs.get(0));
        // Send the coin in chunks to avoid overly large transaction.
        double remaining = act.getAmount();
        double sent      = 0.0;
        int    chunks    = ((int) act.getAmount()) / CHUNK_SIZE + 1;
        for (int j = 0; j < chunks; j++)
        {
          double chunk = remaining < CHUNK_SIZE ? remaining : CHUNK_SIZE;
          try
          {
            sendChunk(act.getPublicKey(), act.getAmount());
            remaining = remaining - chunk;
            sent += chunk;
            Thread.sleep(200); // Don't send too fast - give the daemon a break in between.
          }
          catch (InterruptedException i)
          {
            // Don't care.
          }
          catch (ThoughtRPCException e)
          {
            Console.output("@|red Error sending chunk. |@");
            e.printStackTrace();
            moreElectricity = false;
            break;
          }
        }
        if (sent > 0)
        {
          // Write to the output file.
          pw.append(act.toString());
          Console.output(
              "Sent " + Double.toString(sent) + " to account " + act.getName() + "(" + act.getPublicKey() + ")");
        }
        if (!moreElectricity)
        {
          break;
        }
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

  private double getUnspentOldestFirst(double balanceNeeded, String[] addresses)
  {
    Console.output("Fetching unspent inputs oldest first.");
    unspentCache = new ArrayList<Unspent>();
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
          unspentCache.add(u);
        }
      }
      maxConfirmations = minconf - 1;
      if (maxConfirmations <= 6)
        break;
      minconf -= CONF_CHUNK_SIZE;
      if (minconf < 6)
        minconf = 6;
    }
    Console.debug("Final transaction count: " + unspentCache.size(), 1);
    Console.debug("Spendable balance: " + balance, 1);
    return balance;
  }

  private double getUnspentNewestFirst(double balanceNeeded, String[] addresses)
  {
    Console.output("Fetching unspent inputs newest first.");
    unspentCache = new ArrayList<Unspent>();
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
          unspentCache.add(u);
        }
      }
      minconf = maxconf + 1;
      maxconf = maxconf + CONF_CHUNK_SIZE;
      if (maxconf > maxblocks)
        break;
      else if (maxconf + CONF_CHUNK_SIZE > maxblocks)
        maxconf = maxblocks;
    }
    Console.debug("Final transaction count: " + unspentCache.size(), 1);
    Console.debug("Spendable balance: " + balance, 1);
    return balance;
  }

  private double sendChunk(String destinationAddr, double amount)
  {
    double actual = amount;

    Console.debug("Building raw transaction.", 1);
    List<TxInput> inputs       = new ArrayList<TxInput>();

    List<Unspent> spentCache   = new ArrayList<Unspent>();
    double        inputBalance = 0.0;
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
    if (inputBalance < amount)
    {
      Console.output("Not enough unspent left in cache.");
    }
    else
    {
      List<TxOutput> outputs = new ArrayList<TxOutput>();
      TxOutput       output  = new BasicTxOutput(destinationAddr, amount);
      outputs.add(output);

      Console.debug("Creating raw transaction.", 1);
      String transactionHex = client.createRawTransaction(inputs, outputs);

      actual = send(transactionHex);

      unspentCache.removeAll(spentCache);
    }
    return actual;
  }

  private double sendLarge(Unspent large, List<FundingAction> destination)
  {
    double actual = 0.0;

    Console.debug("Building raw transaction.", 1);
    List<TxInput> inputs = new ArrayList<TxInput>();
    inputs.add(large);

    List<TxOutput> outputs = new ArrayList<TxOutput>();

    for (FundingAction act : destination)
    {
      TxOutput output = new BasicTxOutput(act.getPublicKey(), act.getAmount());
      outputs.add(output);
    }

    Console.debug("Creating raw transaction.", 1);
    String transactionHex = client.createRawTransaction(inputs, outputs);

    actual = send(transactionHex);

    unspentCache.remove(large);

    return actual;
  }

  private double send(String rawTransaction)
  {
    double actual = 0.0;
    Console.debug("Funding raw transaction.", 1);

    FundRawTransactionOptions opts = new FundRawTransactionOptions();
    opts.setChangeAddress(config.getSourceAddresses()[0]);

    FundedRawTransaction funded = client.fundRawTransaction(rawTransaction, opts);
    actual += funded.fee();

    Console.debug("Signing raw transaction.", 1);
    String signedHex = client.signRawTransaction(funded.hex());

    Console.debug("Sending raw transaction.", 1);
    String txid = client.sendRawTransaction(signedHex);

    Console.debug("Sent transaction " + txid, 1);

    return actual;
  }

  private Unspent biggestUnspent()
  {
    Unspent biggest = unspentCache.get(0);
    for (Unspent u : unspentCache)
    {
      if (u.amount() > biggest.amount())
      {
        biggest = u;
      }
    }
    Console.debug("Biggest unspent: " + Double.toString(biggest.amount()), 1);
    return biggest;
  }

  private FundingAction biggestUpTo(double max, List<FundingAction> exclude)
  {
    FundingAction retval = null;
    for (FundingAction act : actionList)
    {
      if (!exclude.contains(act))
      {
        if (act.getAmount() < max && retval == null)
        {
          retval = act;
        }
        else if (act.getAmount() < max && act.getAmount() > retval.getAmount())
        {
          retval = act;
        }
      }
    }

    return retval;
  }

  public static void main(String[] args)
  {
    Config      config = new Config(args);
    Rationalize app    = new Rationalize(config);
    app.run();
    Console.end();
  }
}
