(ns muon-clojure.client
  (:require [muon-clojure.utils :as mcu]
            [muon-clojure.server :as server]
            [clojure.tools.logging :as log])
  (:use clojure.java.data)
  (:import (io.muoncore.protocol.requestresponse Response)
           (org.reactivestreams Publisher)
           (java.util Map)))

(def ^:dynamic *muon-config* nil)

(defn muon-client [url service-name & tags]
  (let [muon-instance (mcu/muon-instance url service-name tags)
        client (server/map->Microservice muon-instance)]
    (Thread/sleep 2000)
    client))

(defmacro with-muon [muon & body]
  `(binding [*muon-config* ~muon]
     ~@body))

(defn subscribe!
  [service-url & {:keys [from stream-type stream-name]
                  :or {from (System/currentTimeMillis) stream-type nil
                       stream-name "events"}}]
  (let [params (mcu/dekeywordize {:from (str from) :stream-type stream-type
                                  :stream-name stream-name})]
    (log/info ":::::::: CLIENT SUBSCRIBING" service-url params)
    (server/subscribe *muon-config* service-url params)))

(defn request! [service-url params]
  (let [item-json (mcu/dekeywordize params)]
    (log/info ":::::::: CLIENT REQUESTING" service-url item-json)
    (mcu/keywordize (into {} (server/request *muon-config* service-url item-json)))))

