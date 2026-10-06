(ns clara.tools.client.bootstrap
  "Bootstrap-styled components usable in Clara tools."
  (:require [reagent.core :as reagent]
            ["react-bootstrap" :as rb]))

(def button (reagent/adapt-react-class rb/Button))

(def grid (reagent/adapt-react-class rb/Container))
(def row (reagent/adapt-react-class rb/Row))
(def col (reagent/adapt-react-class rb/Col))

(def table (reagent/adapt-react-class rb/Table))

(def navbar (reagent/adapt-react-class rb/Navbar))
(def nav (reagent/adapt-react-class rb/Nav))
(def nav-item (reagent/adapt-react-class (.-Link rb/Nav)))
(def nav-dropdown (reagent/adapt-react-class rb/NavDropdown))
(def menu-item (reagent/adapt-react-class (.-Item rb/NavDropdown)))
