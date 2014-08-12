(ns clara.tools.ui.common
  "Common UI code"
  (:require [cemerick.austin.repls :refer (browser-connected-repl-js)] ))

;  svg { overflow: hidden; position:fixed; top:0; left:0; height:100%; width:100% }


(def style
"
html, body { margin:0; padding:0; overflow:hidden }


.node rect {
    stroke: #333;
    stroke-width: 1.5px;
    fill: #fff;
}

.edgeLabel rect {
    fill: #fff;
}

#testdraw .edgePath {
    stroke: #333;
    stroke-width: 1.5px;
    fill: none;
}
")

(defn- scripts []
  [:div [:script {:src "/js/d3.v3.min.js"}]
   [:script {:src "/js/dagre-d3.js"}]
   [:script {:src "/js/clara-tools.js"}]
   [:script (browser-connected-repl-js)]])

(defn with-layout
  "Returns a body with layout, scripts, and Javascript to be executed on page load.."
  ([title body]
     (with-layout title body nil))
  ([title body on-load]
      [:head
       [:style style]
       [:title title]
       [:body body
        (scripts)
        (if on-load
          [:script on-load]
          nil)
        ]]))
