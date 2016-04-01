
# Catalysis

[![Join the chat at https://gitter.im/metasoarous/catalysis](https://badges.gitter.im/metasoarous/catalysis.svg)](https://gitter.im/metasoarous/catalysis?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

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
By reading further you agree to hold the author unaccountable if catalysis eats your cat.


## Usage

To get running, clone, and cd into the project directory (`catalysis`).
Assuming you want to use the free version of datomic to test things out, you should be able to run


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


## Customizing your app

Yay!
If you've gotten through the Usage section, you have a running system with some data loaded and ready to tinker with!
At this point, you might want to tinker around with the Todo app a little bit to feel out how things work.
But you'll surely soon want to start reshaping things into your own project.

### Schema

Because of the seamless shuttling of data back and forth between server and client, much of the customization work to do on the server is in the schema.

The schema file is located in `resources/schema.edn`.
The data in this file is a [conformity](https://github.com/rkneufeld/conformity) spec, so you can continue to use this same file for all your migrations.
If you want to see how these migrations are hooked up, take a look at the `catalysis.datomic/Datomic` component.


### Front end components

The main namespace for the client is `catalysis.client.app`.
This is where everything hooks together.
We may eventually set up Stuart Sierra's component here as well, but for now, thing of this as your system bootstrap process.

Views are in `catalysis.client.views`, and you'll note that the `main` function there is hooked up at the end of the `app` namespace.
This is where you write your view code.
This view code is written in a combination of [posh](https://github.com/mpdairy/posh) and [reagent](https://github.com/reagent-project/reagent), so you'll need to be fluent with those libs to get moving here.

The long and short of it though is that posh lets us write Reagent reactions as DataScripts queries (`q`, `pull`, `pull-many`, `filter-pull`, `filter-q`, etc.).
Thus, we obtain the re-frame "reactive materialized views" architecture with declarative query descriptions, al. a DataScript databases.


### More coming soon...


## Future

### Re-frame style organization

Ideally, we'd have a nice way for organizing subscriptions and event handlers inspired by re-frame.
Need to look further into how we should do this.
To a certain extent, there's a lot of organizational overlap somewhat automatically.
But building up more specific patterns around this organization will help things feel polished.


## Miscellany

I haven't tried any of these things, but...

### Deploying to Heroku

To make Catalysis run on Heroku, you need to let Leiningen on Heroku use the "package" build task.

To do this, and point Leiningen on Heroku to the "package" target, add the following config variable to Heroku by running this command:

```
heroku config:add LEIN_BUILD_TASK=package
```

Everything is nicely wrapped in shiny purple foil if you simply click this button:

[![Deploy](https://www.herokucdn.com/deploy/button.png)](https://heroku.com/deploy)

Enjoy!


### Mobile App?

Maybe in the future we'll build a nativish mobile app out of this thing using Cordova.
The following has an example of this: [Enterlab CorDeviCLJS](https://github.com/enterlab/cordevicljs)


## Contributions

This code was initially developed as a fork of Rente, but has diverged.
We thank the authors of Rente for their contribution.

This library is authored by Christopher T. Small, with the help of the following individuals:

Kyle Langford


See LICENSE for license.



