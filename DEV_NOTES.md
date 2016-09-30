# Notes for development in this project

As this project matures this may need more structure/organization.


## Starting a ClojureScript REPL

After starting a normal Leiningen REPL, follow the following steps:

````
user=> (use 'figwheel-sidecar.repl-api)
user=> (start-figwheel!)
user=> (cljs-repl)
Launching ClojureScript REPL for build: REPL
;; The compiled clara-tools files should be available.
cljs.user=> clara.tools.client.bootstrap/panel
#object[reagent.impl.template.NativeWrapper]
````

Then open the file "clara-tools/resources/public/index.html" in your browser.
