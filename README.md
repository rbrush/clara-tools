# clara-tools

Experimental tooling for exploring and working with Clara-based rulesets.

See the [clara-rules project](https://github.com/rbrush/clara-rules) for details on Clara.

# Usage
Users may explore the contents of Clara sessions with the _clara.tools.watch_ namespace. Rather than using ```clara.rules/mk-session```, users can create an instrumented "watched" session with ```clara.tools.watch/mk-wached-session```, which accepts the same arguments. Here is an example in use:

```clj
(require '[clara.tools.watch :as w])

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

# Known Issues
This is an experimental project and is not ready for production use. The core functionality is working, with one significant exception: viewing the content of multiple sessions in a single web application may yield inconsistent results.

## License

Distributed under the Eclipse Public License, the same as Clojure.
