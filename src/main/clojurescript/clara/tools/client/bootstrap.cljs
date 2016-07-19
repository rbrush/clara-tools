(ns clara.tools.client.bootstrap
  "Bootstrap-styled components usable in Clara tools."
  (:require [reagent.core :as reagent :refer [atom]]
            [cljsjs.react-bootstrap]))

(def accordion (reagent/adapt-react-class (aget js/ReactBootstrap "Accordion")))
(def panel (reagent/adapt-react-class (aget js/ReactBootstrap "Panel")))
(def button-toolbar (reagent/adapt-react-class (aget js/ReactBootstrap "ButtonToolbar")))
(def button-group (reagent/adapt-react-class (aget js/ReactBootstrap "ButtonGroup")))
(def button (reagent/adapt-react-class (.. js/ReactBootstrap -Button) ;;(aget js/ReactBootstrap "Button")
             ))
(def modal (reagent/adapt-react-class (aget js/ReactBootstrap "Modal")))
(def modal-header (reagent/adapt-react-class
                  (aget (aget js/ReactBootstrap "Modal") "Header")))
(def modal-body (reagent/adapt-react-class
                (aget (aget js/ReactBootstrap "Modal") "Body")))
(def modal-footer (reagent/adapt-react-class
                  (aget (aget js/ReactBootstrap "Modal") "Footer")))

(def popover (reagent/adapt-react-class (aget js/ReactBootstrap "Popover")))
(def tooltip (reagent/adapt-react-class (.. js/ReactBootstrap -Tooltip)))

(def grid (reagent/adapt-react-class (aget js/ReactBootstrap "Grid")))
(def row (reagent/adapt-react-class (aget js/ReactBootstrap "Row")))
(def col (reagent/adapt-react-class (aget js/ReactBootstrap "Col")))

(def table (reagent/adapt-react-class (aget js/ReactBootstrap "Table")))

(def tabs (reagent/adapt-react-class (aget js/ReactBootstrap "Tabs")))
(def tab (reagent/adapt-react-class (aget js/ReactBootstrap "Tab")))

(def navbar (reagent/adapt-react-class (aget js/ReactBootstrap "Navbar")))
(def nav (reagent/adapt-react-class (aget js/ReactBootstrap "Nav")))
(def nav-item (reagent/adapt-react-class (aget js/ReactBootstrap "NavItem")))
(def nav-dropdown (reagent/adapt-react-class (aget js/ReactBootstrap "NavDropdown")))
(def menu-item (reagent/adapt-react-class (aget js/ReactBootstrap "MenuItem")))

(def glyphicon (reagent/adapt-react-class (aget js/ReactBootstrap "Glyphicon")))

(def input (reagent/adapt-react-class (aget js/ReactBootstrap "Input")))
