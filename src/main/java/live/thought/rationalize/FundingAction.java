package live.thought.rationalize;

public class FundingAction
{
  protected String name;
  protected double amount;
  protected String privateKey;
  protected String publicKey;
  
  public FundingAction(String name, double amount)
  {
    this.name = name;
    this.amount = amount;
  }

  public String getName()
  {
    return name;
  }

  public void setName(String name)
  {
    this.name = name;
  }

  public double getAmount()
  {
    return amount;
  }

  public void setAmount(double amount)
  {
    this.amount = amount;
  }

  public String getPrivateKey()
  {
    return privateKey;
  }

  public void setPrivateKey(String privateKey)
  {
    this.privateKey = privateKey;
  }

  public String getPublicKey()
  {
    return publicKey;
  }

  public void setPublicKey(String publicKey)
  {
    this.publicKey = publicKey;
  }
  
  public String toString()
  {
    return String.format("%s,%s,%s,%s\n", name, privateKey, publicKey, Double.toString(amount));
  }
}
