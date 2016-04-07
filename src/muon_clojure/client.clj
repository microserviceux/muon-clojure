(ns muon-clojure.client
  (:require [muon-clojure.utils :as mcu]
            [muon-clojure.common :as mcc]
            [muon-clojure.server :as server]
            [clojure.tools.logging :as log])
  (:use clojure.java.data)
  (:import (io.muoncore.protocol.requestresponse Response)
           (io.muoncore.exception MuonException)
           (org.reactivestreams Publisher)
           (java.util Map)
           (io.muoncore.protocol.event.client DefaultEventClient)
           (io.muoncore.protocol.event Event)))

(def ^:dynamic *muon-config* nil)

(defn muon-client [url service-name & tags]
  (let [muon-instance (mcc/muon-instance url service-name tags)
        ec (try
             ;; TODO: Make event client handling smarter
             (DefaultEventClient. muon-instance)
             (catch MuonException e
               (log/info (str "Eventstore not found, "
                              "event functionality not available!"))
               nil))
        client (server/map->Microservice
                {:muon muon-instance :event-client ec})]
    #_(Thread/sleep 2000)
    client))

(defmacro with-muon [muon & body]
  `(binding [*muon-config* ~muon]
     ~@body))

(defn event! [{:keys [stream-name id parent-id service-id payload event-type]}]
  ;; TODO: Make event client handling smarter
  (if-let [ec (:event-client *muon-config*)]
    (let [ev (Event. stream-name event-type id parent-id service-id
                     (mcu/dekeywordize payload))]
      (.event ec ev))
    (throw (UnsupportedOperationException. "Eventstore not available"))))

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
