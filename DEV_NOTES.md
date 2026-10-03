# Notes for development in this project

As this project matures this may need more structure/organization.

## Running the UI during development

There are two ways to start a REPL, each serving its own UI build:

- `mise run repl` builds the release UI (`target/resources`) and serves it. Use it to just run the tools.
- `mise run dev` starts shadow-cljs inside the REPL (`dev/user.clj`) and serves the dev build
  (`target/dev`), which hot-reloads UI changes into the open page. The dev build only works while
  shadow-cljs is running, which is why it is kept apart from the release build.

From either REPL:

```clj
(require '[clara.rules :refer :all]
         '[clara.tools.watch :as w]
         '[clara.tools.examples.shopping]
         '[clara.tools.examples.shopping.records :refer :all])

(def s (-> (w/mk-watched-session "Shopping" 'clara.tools.examples.shopping :cache false)
           (insert (->Customer :vip) (->Order 2013 :august 20) (->Purchase 120 :widget))
           (fire-rules)))

(w/browse!)
```

## Starting a ClojureScript REPL

With `mise run dev` running and the UI open in a browser, connect to shadow-cljs's nREPL
(port in `.shadow-cljs/nrepl.port`) and run:

```clj
(shadow/repl :app)
```

## clara-rules 0.24 notes

- `insert` and `retract` are queued until `fire-rules`, so watched facts only update after firing.
- Rule right-hand sides are stored unqualified; the logic graph resolves them in the rule's `:ns-name`.
- clara-rules' exported clj-kondo hook misreads `defrule`/`defquery` docstrings, so `mise run lint`
  only lints `src/main`. This is fixed upstream ([oracle-samples/clara-rules#505](https://github.com/oracle-samples/clara-rules/pull/505))
  but not released yet; `src/test` can be linted again once a release includes it.
- Watched rule files are reloaded through `java.nio.file.WatchService`. On macOS the JDK implements it by
  polling, so a reload can take a few seconds to show up.
