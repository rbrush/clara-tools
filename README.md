# clara-tools

Experimental tooling for exploring and working with Clara-based rulesets.

See [clara-rules.org](https://www.clara-rules.org/) and the [clara-rules project](https://github.com/oracle-samples/clara-rules)
for details on Clara. This version targets clara-rules 0.24, which is still published to Clojars as
`com.cerner/clara-rules`, and Clojure 1.12.

# Usage
Users may explore the contents of Clara sessions with the _clara.tools.watch_ namespace. Rather than using ```clara.rules/mk-session```, users can create an instrumented "watched" session with ```clara.tools.watch/mk-watched-session```, which takes a session name followed by the same arguments as `mk-session`. Here is an example in use, based on the shopping example that ships with the tests (start a REPL with the `:dev` alias, or `mise run repl`, so it is on the classpath):

```clj
(require '[clara.rules :refer :all]
         '[clara.tools.watch :as w]
         '[clara.tools.examples.shopping]
         '[clara.tools.examples.shopping.records :refer :all])

;; Create a watched session and insert some facts.
(def my-session (-> (w/mk-watched-session "My Watched Session."
                                           'clara.tools.examples.shopping
                                           :cache false)
                    (insert (->Customer :vip)
                            (->Order 2013 :august 20)
                            (->Purchase 20 :gizmo)
                            (->Purchase 120 :widget)
                            (->Purchase 90 :widget))
                    (fire-rules)))

;; Look at it in the browser!
(w/browse!)
```

This will open a web browser, allowing the user to run queries, list facts, and view a rendering of the logic used.

With clara-rules 0.24, `insert` and `retract` are queued until `fire-rules`, so the browser only reflects
changes to a session after its rules have been fired.

# Development setup

The JVM side is built with `deps.edn` and [tools.build](https://github.com/clojure/tools.build);
the browser UI with [shadow-cljs](https://github.com/thheller/shadow-cljs), using React 18, Bootstrap 5 and
[dagre-d3-es](https://github.com/tbo47/dagre-es) from npm.

Prerequisites are Java 21, Node 22, the Clojure CLI and, optionally, [clj-kondo](https://github.com/clj-kondo/clj-kondo).
The versions are pinned in `mise.toml`, so with [mise](https://mise.jdx.dev) installed, `mise install` sets them up
and the tasks below are available. Each task is a thin wrapper over the plain command next to it:

| mise task       | Plain command                                   | What it does                                          |
|-----------------|-------------------------------------------------|-------------------------------------------------------|
| `mise run deps` | `clojure -P -M:dev:test:cljs:ui-dev:nrepl && npm ci` | Download Clojure and npm dependencies            |
| `mise run test` | `clojure -M:test`                               | Run the Clojure tests                                 |
| `mise run lint` | `clj-kondo --fail-level error --lint src/main`  | Lint Clojure and ClojureScript sources                |
| `mise run repl` | `npx shadow-cljs release app && clojure -M:dev:ui:nrepl` | nREPL serving the release UI            |
| `mise run dev`  | `clojure -M:dev:cljs:ui-dev:nrepl`              | nREPL + shadow-cljs, with a hot-reloading UI          |
| `mise run build`| `clojure -T:build jar`                          | Library jar in `target/`, including the compiled UI   |

`mise run repl` and `mise run dev` also copy Bootstrap's CSS out of `node_modules`; `clojure -T:build jar` does
that on its own. See [DEV_NOTES.md](DEV_NOTES.md) for more on the two REPLs and for a ClojureScript REPL.

# Known Issues
This is an experimental project and is not ready for production use. The core functionality is working, with one significant exception: viewing the content of multiple sessions in a single web application may yield inconsistent results.

The browser UI is compiled into the jar built by `clojure -T:build jar`. Depending on this project as a
`deps.edn` git dependency gives you the Clojure sources only, without the compiled UI.

## License

Distributed under the Eclipse Public License, the same as Clojure.
