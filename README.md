# Rationalize
### Coin management utility for organizing Thought coin into discrete accounts. ###  
The rationalize utility takes as input a source address or addresses from which inputs are withdrawn, and a list of amounts to output.  The program creates new labeled key pairs in the wallet, and transfers the specified amount of THT to those addresses.  Configuration options allow the user to specify whether to use newer or older inputs first.  The program will attempt to satisfy each of the funding actions specified from the largest unspent inputs first, then by consolidating smaller inputs.  A comma-separated values (CSV) file is provided listing the labeled key pairs and amounts disbursed. See the Running section below for instructions and configuration options.  
  
Note:  The new private/public key pairs created in this process are not derived from the wallet's HD key.  After using this utility, make sure to create new backups of the wallet file.  

### Building ###
Building rationalize requires Java 8 (or higher) Development Kit and Maven.

Rationalize depends on the [thought4j RPC library] (https://github.com/thoughtnetwork/thought4j).  
Clone and install thought4j before building rationalize.

```
git clone https://github.com/thoughtnetwork/thought4j.git  
cd thought4j  
mvn install  
```

Once thought4j is installed, clone and build rationalize.

```
git clone https://github.com/philgrim2/rationalize  
cd rationalize  
mvn install  
```

The build will produce a shaded jar file in the target directory of the repository.  

### Running ###
Running rationalize requires a running Thought wallet or Thought daemon with the RPC server enabled.  Binary distributions of a daemon and wallet can be found at https://github.com/thoughtnetwork/thought-wallet.  
  
Rationalize can be configured using command-line options or a properties file.  If both are present, command-line options override property file options.  
  
### Command Line Options ###

```
usage: Rationalize
 -a,--addresses <arg>         One or more (comma-separated) Thought addresses to obtain funds from (required)  
 -d,--debug                   Enable debug output.  Command-line only.  
 -F,--fundingFile <arg>       File containing funding lines (default: rationalize_input.csv)  
 -f,--config <arg>            Configuration file to load options from. Command line options override config file.  
 -H,--host <arg>              Thought RPC server host (default: localhost)  
 -h,--help                    Displays usage information  
 -o,--output <arg>            File to write account keys to (default: rationalize_results.csv)  
 -P,--prefix <arg>            Prefix for created account names. (default: Rationalize)  
 -p,--password <arg>          Thought server RPC password  
 -s,--fundingStrategy <arg>   Strategy for selecting funding inputs (oldest or newest) (default: oldest)  
 -u,--user <arg>              Thought server RPC user 
```
    
#### Example running with command-line options ####  
```
java -jar rationalize-0.1-SNAPSHOT-jar-with-dependencies.jar --host localhost --port 11617 --user jqpublic --password password --addresses 'ks7EMHr7sxSFMPHZkeRbMNuyMswF2t1xcH,ktvP4pzeMqsrHgC8JX6pVBSv3tGVPdHeJi' --fundingStrategy newest --fundingFile rationalize_input.csv --output rationalize_output.csv
```

    
### Properties File ###
  
```
host=localhost  
port=11617  
user=jqpublic  
password=password  
prefix=Test01  
addresses=kvdPDVw6T6ws8N2fAZiaFMHsJLXWDXtHiq  
fundingStrategy=oldest  
fundingFile=rationalize_input.csv  
output=rationalize_output.csv  
```

### Properties ###
- ***host***:  The host where the Thought wallet or daemon is running.  Defaults to localhost.  Note that a wallet or daemon must be configured to allow remote RPC access if you wish to use one on another host.  
- ***port***:  The RPC port to connect to on the Thought wallet or daemon.  Defaults to 10617 (Thought mainnet).    
- ***user***:  The RPC user name to connect as.  
- ***password***:  The password for the given RPC user.  
- ***prefix***:  The prefix to prepend to created address labels.  Labels will follow the pattern of <prefix>-<timestamp>-<funding line>-<account index>.     
- ***addresses***:  The source address or addresses (comma-separated) to pull inputs from.  
- ***fundingStrategy***:  You can choose to have Rationalize select unspent inputs oldest first or newest first.  Default is oldest first.  
- ***fundingFile***:  The file containing funding lines - an amount of THT followed by the number of addresses to create that will receive that amount.  See below for format.  
- ***output***:  The file rationalize will create containing the result of the process, containing the label, private/public key pair, and amount transfered.  This file can be used as a backup for these key pairs, and should be protected just like a wallet backup.  
  
#### Example running with properties file ####
```
java -jar rationalize-0.1-SNAPSHOT-jar-with-dependencies.jar --config rationalize.properties
```
Remember that if command-line options and properties file properties both specified, command-line options take precedence.  

  
### Input File ###  
This sample shows how Rationalize input files should be arranged.  The first column is a floating point number indicating how much coin to transfer.  The second column shows how many new accounts to create that will receive that amount of coin.  
  
For example, this file will result in the creation of 48 accounts.  Five accounts will contain one million coins each, 8 accounts will contain 500,000 coins each, and so on.  Accounts are labeled in the wallet using the timestamp of the run (in Unix time - used to prevent label collision in the wallet), the funding line index, and the index of the account within the funding line. Assuming a label prefix of MYACCOUNTS and a timestamp of 1622407476973, resulting wallet labels will include MYACCOUNTS-1622407476973-1-1 (first funding line, first account) through MYACCOUNTS-1622407476973-6-16 (sixth funding line, 16th account).  

Lines beginning with the octothorpe character will be treated as comments.  
  
```
# This is a comment
1000000.0, 5
500000.0, 8
250000.0, 4
100000.0, 10
50000.0, 5
25000.0, 16
``` 
 
  
