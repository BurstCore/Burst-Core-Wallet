This is a modified version of the Nxt Reference Software (NRS), which does not
use the public Nxt blockchain.

It is configured to run as a testnet only, with 10 well-known genesis block
accounts, having as passwords the numbers 0 to 9, each funded with 100M NXT.

By default no peers are defined, but since networking is enabled, a node
can become part of a network by configuring default peer addresses in the
conf/nxt.properties file.

To allow anyone to print money, the Genesis account, with the password "Nxt",
can still send NXT even though its balance is negative.

This evaluation release will be useful to application developers who plan to
eventually use the public Nxt blockchain, but also to businesses evaluating
the suitability of the Nxt platform to run their own private blockchains.

With this "personal blockchain" solution, such users will now be able to
develop and test Nxt-based applications on a private, local testnet, without
having to join the public testnet or the production blockchain.

