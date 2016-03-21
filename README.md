
# Catalysis

Full stack `(+ clj cljs (reagent react.js) datomic datascript datsync)` template.

> The acceleration of a chemical reaction by a catalyst.


## Vision

Catalysis is the intersection of the following:

* Nikita Prokopov's [The Web After Tomorrow](http://tonsky.me/blog/the-web-after-tomorrow/) vision and DataScript library
* The [Re-frame pattern](https://github.com/Day8/re-frame) and "streaming materialized views all the way down" philosophy
* Everything wonderful in Datomic/DataScript

### Web after tomorrow

What if you could write client side code as though it were running on the server with a direct database connection?

### Current state

This is highly alpha and you shouldn't trust anything here.
By reading further you agree to hold the author unaccountable if catalysis eats your socks.


## Usage

Clone, and cd into the project directory (`catalysis`).


### Datomic

First, you'll need to get datomic up and running.
Go get the Datomic Pro Starter edition at <http://www.datomic.com/pricing.html>.
Then go to <https://my.datomic.com/account> and copy the bit that looks like:

```
;; ~/.lein/credentials.clj.gpg (see the Leiningen deploy authentication docs)
{#"my\.datomic\.com" {:username "your@email.com"
                      :password "xxxxxxxxxxxxxxxxxxxxxxxxx"}}
```

Copy over the file from `config/example/env.sh`, and put it in `config/local/env.sh`.
Then copy the username and password into the appropriate spots in that file, leaving something like this:

```
export DATOMIC_USERNAME="your@email.com"
export DATOMIC_PASSWORD="xxxxxxxxxxxxxxxxxxxxx"
```

You can now run `source config/local/env.sh` to get those env variables loaded so Leiningen can download Datomic for you.
This file is in the `.gitignore`, so you don't have to worry about it.


#### Getting the transactor running

Once you have all that set up, you need to download the transactor.
Back on that Datomic Account page, there should be a section with a wget/curl instruction set, looking something like:

```
wget --http-user=chris@thoughtnode.com --http-password=xxxxxxxxxxxxxxxxxxxxxxx https://my.datomic.com/repo/com/datomic/datomic-pro/0.9.5130/datomic-pro-0.9.5130.zip -O datomic-pro-0.9.5130.zip
```

Run that, and stash in some directory where you have easy access to it.
Maybe even put the bin directory there on your path.
Whatever.
The long story short is you'll be running the transactor from there.
Once extracted, `cd` into that directory and run `bin/transactor path/to/your/dev-transactor.properties`.
There is a `config/example/dev-transactor.properties` file to get you going.
You can `cp` that into `config/local`.

Once there, you just have to put your License key in there (see account page to have key sent to you).
Then you can run the transactor and connect to it.


##### Alternative credential security? (not necessary)

You can also try to get your credentials secured with GPG.
There are a couple extra hoops there, and I had trouble getting it to work at first.
Part of that was I think a GPG bug; the other part may have been me having a bad Datomic version number in there.
In any caes, if you want to try...

Put that snippet from the Datomic website in a file `~/.lein/credentials.clj`.
Then run

```
gpg --default-recipient-self -e ~/.lein/credentials.clj  > ~/.lein/credentials.clj.gpg
```

Note that you'll need a gpg key set up for this, which you can accomplish with `gpg --gen-key`.
Make sure to move you're mouse around for... err... entropy...
You may have to specify something like `-r "<your@email.com>"` if it's having trouble finding you (include the `"` and `<` in there).
If you have set everything up you shouldn't get any errors when you run `lein deps`.

As pointed out on the Leiningen page, you may have to decrypt the gpg after running `gpg --decrypt` on the file.
I'm actually still having trouble with this part possibly, so maybe let me know if you figure it out (or PR
these docs)

<https://github.com/technomancy/leiningen/blob/master/doc/DEPLOY.md#authentication>


### Figwheel

Next get figwheel up and running for hot cljs reloading:

```
lein figwheel
```

Wait for figwheel to finish and notify browser of changed files!


### Server

Then in another terminal tab (tmux pane, whatevs...):

```
lein repl
```

Wait for the prompt, then type

```
(ns user)
(run)
```

This will initialize a `system` var in the `user` ns, bind the new system instance to it, and then start that system (see Stuart Sierra's Component for more information about systems and server level components here...).
If you need to reset the system, call `reset`.

You can also call something like `(run {:server {:port 8882}})` to specify config overrides.
The schema for this is in `catalysis.config`.
Unfortunately, not sure yet how to get the `reset` function to also accept the `config-overrides` option.

And maybe also helpers for running multiple instances at once to test different things...
But one step at a time :-)


### Open Browser

Next, point browser to:
http://localhost:8080 (or whatever you set $PORT to)

Open up your browser console, which will log the data returned from the server when you click either of the 2 buttons for socket with/without callback (pull/push).


## Deploying to Heroku

To make Rente run on Heroku, you need to let Leiningen on Heroku use the "package" build task.

To do this, and point Leiningen on Heroku to the "package" target, add the following config variable to Heroku by running this command:

```
heroku config:add LEIN_BUILD_TASK=package
```

Everything is nicely wrapped in shiny purple foil if you simply click this button:

[![Deploy](https://www.herokucdn.com/deploy/button.png)](https://heroku.com/deploy)

Enjoy!


## Mobile App?

Maybe in the future we'll build a nativish mobile app out of this thing using Cordova.
The following has an example of this: [Enterlab CorDeviCLJS](https://github.com/enterlab/cordevicljs)

