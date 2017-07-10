# Pieconomy

Pieconomy is an economy plugin which allows you to use items as currency and inventories as accounts. With the default configuration, if I had three gold ingots and one gold nugget, and ran `/bal`, it would tell me I had `28 G`. If I dropped an ingot, I would have `19 G`. If I ran `/pay turtledude01 5`, he would get 5 gold nuggets, and I would lose an ingot and gain 4 nuggets (as change). And any other plugin using the economy will work properly (provided it's written properly and follows the [Economy Best Practices][1]).  

This plugin is for people who want a new style of economy, one where there isn't some magic number in memory that increments when you get paid. The economy becomes real, and part of the game instead of part of a plugin. Great for RPG-style servers, or ones focused on survival.

### Features

* The obvious one. Your players' accounts are their inventories; their money is their items. You say what items are what currencies - for instance, you could make an economy based around a mod which adds coins.
* Multiple currencies - you could have gold AND dollars AND vote tokens AND cosmetic credits, all at the same time, all using items. No upper limit.
    * Any plugin can add new currencies to the registry if they want to. It's still up to you to say what items back it, though.
* Server accounts - you can create virtual accounts backed by the server, which players can pay to, and people with appropriate permissions can manipulate.
    * You can configure which (if any) currencies can be given to an account, and which (if any) currencies the account can have a negative amount for.
* Total admin control - Staff can add and remove money from anyone, set anyone's balance, and transfer money between accounts. 
  
### Things to keep in mind

Admins:  

* Change can be made whenever money is paid. Don't assign monetary values to items you don't want people to be able to convert between (hence why the default config uses gold ingots, nuggets, and blocks).
* Whatever the smallest valued item a currency has is, that's the smallest value that is used by the plugin. If you run off of USD, but don't implement pennies, for instance, values (in any transaction) smaller than 5&cent; will be simply ignored.
  
Players:  

* If you expect to receive money, make sure to have space in your inventory. It's up to whatever plugin does the economy request how to handle a lack of space for money. Pieconomy also cannot combine items for you if space is low.
* Be careful not to pay players with the same name as server accounts, or vice versa. If there is a name conflict, you can write `player:<playername>` or `server:<accountname>` wherever accounts are used in this plugin's commands, and tab-complete is your friend. This will NOT work for commands not from this plugin.
* Also keep in mind item 2 of the Admins section.

Developers:  

This plugin runs a few edge cases you don't expect to see in economy plugins. Player accounts can and will return `ResultType.ACCOUNT_NO_SPACE`. They can even return that when _withdrawing_, due to the making of change. They can even return that, but be successful on a _larger_ amount, due to a full inventory but non-full stacks. Server accounts can report a balance, and successfully withdraw a balance, but return `ACCOUNT_NO_SPACE` if you try to put it back again due to the currency not being allowed on that account. They can also have negative values on any given currency.  
However, I still have no idea how Contexts work, or what they even mean, so I ignore them completely.  

All three:  

Offline players cannot be interacted with, because Sponge doesn't allow you to get their inventories.

### Important!

**THIS PLUGIN IS IN BETA!** There may be bugs, and I haven't been able to test it any further than myself on my own test server. In a real environment it could possibly behave unexpectedly. Report any and all bugs you find to the issue tracker, along with as precise a description of the circumstances as you possibly can. I am especially looking for any errors saying `Unknown error regarding account balance subtraction calculations.`, or `/withdraw` making incorrect change. Thank you in advance for your help and support.

### Commands

##### Player commands

`/pay <to> <amount> [<currency>]`  
Pays someone else some money. Easy enough.  

`/bal [<who>] [<currency>]`  
Retrieve your balance, or someone else's. If you're the console, `who` isn't optional.  

##### Admin commands

`/deposit <to> <amount> [<currency>]`  
Add money to an account. Requires `pieconomy.admin.deposit`.  

`/withdraw <from> <amount> [<currency>]`  
Remove money from an account. Requires `pieconomy.admin.withdraw`.  

`/transfer <from> <to> <amount> [<currency>]`  
Transfer money from one account to another. Requires `pieconomy.admin.transfer`.  

`/setbal <who> <amount> [<currency>]`  
Set the balance of an account directly. Requires `pieconomy.admin.setbal`.
  
### Configuration

What follows is most of the default configuration, plus explanatory comments.

```hocon
# All the items that back the economy.
items {
  # The name of each entry is the ID of the item. Since Sponge rocks, modded items work just fine.
  # To add a data value to this, add '@<data>'. For instance, minecraft:wool@3.
  "minecraft:gold_nugget" {
    # This currency will be defined later on.
    currency = gold
    # And a gold nugget is worth 1 gold.
    amount = 1
  }
  # elided default settings for gold_ingot and gold_block
}

# All the currencies that make up the economy.
currencies {
  # The name of the currency.
  gold {
    # I have 22 G. You might change this to 2 for USD, so I would have $22.00.
    decimal-places = 0
    # May show up in other plugins. The display name of the currency. Component format.
    name = {text = Gold}
    # May show up in other plugins. The plural display name of the currency. Component format.
    plural = {text = Gold}
    # The symbol. All formatted monetary amounts should show it, for instance G or $. Component format.
    symbol = {text = G, color = gold}
    # The format that monetary amounts should show up in. Forgive the verbose format, it's from Sponge.
    # Don't change text or arguments{}, and put {amount} and {symbol} in their own components.
    # Other than that, content{} is in component format.
    format {
      arguments { amount { optional = false }, symbol { optional = false } }
      content {
        text = ""
        extra = [
          {text = "{amount}"}
          {text = " "}
          {text = "{symbol}"}
        ]
      }
      options { openArg = "{", closeArg = "}" }
    }
  }
}

# What currency should be used by default?
default-currency = gold

# Management of server accounts.
server-accounts {
  # Should they even be used?
  enable = true
  # How often, in minutes, should they be saved to disk?
  autosave-interval = 20
  # What accounts should exist?
  accounts = [
    # An account entry.
    {
      # The ID of the account. This is used internally, and should not ever be changed.
      # This allows you to change the name while keeping the associated data.
      id = "bank"
      # The name of the account. Players use this in commands and it appears in messages.
      name = "Bank"
      # What currencies can it accept?
      currencies {
        # This account does not specify any currencies.
        values = []
        # This is a blacklist, meaning any currency can be used.
        type = blacklist
      }
      # What currencies can have negative values?
      negative-values {
        # This account doesn't specify any currencies.
        values = []
        # This is a whitelist, meaning no currencies can have negative values.
        type = whitelist
      }
    }
  ]
}

# Please don't touch.
version = 1
```

### Upcoming features

* A better `format{}`.
* Per-server-account permissions.
* Caching offline players' balances and transactions, and finalizing them upon login.

### Changelog

0.1.0: Initial release.  
0.2.0: Allowed using data values, and fixed a rounding bug.

##### Note

This plugin uses [bStats][2], which collects data about your server. This data is in no way intrusive, is completely anonymized, and has a negligible impact on server performance, so there is no reason whatsoever to disable it. However, if you wish to anyway, simply set `enabled` to `false` in `config/bStats/config.conf`.

[1]: https://docs.spongepowered.org/stable/en/plugin/economy/practices.html
[2]: https://bstats.org/