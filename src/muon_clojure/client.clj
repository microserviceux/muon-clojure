(ns muon-clojure.client
  (:require [muon-clojure.rx :as rx]
            [muon-clojure.utils :as mcu]
            [clojure.core.async :refer [go-loop go <! >! chan buffer close!]]
            [clojure.tools.logging :as log])
  (:use clojure.java.data)
  (:import (io.muoncore Muon MuonStreamGenerator)
           (io.muoncore.transport.resource MuonResourceEvent MuonResourceEventBuilder)
           (io.muoncore.extension.amqp AmqpTransportExtension)
           (io.muoncore.extension.amqp discovery.AmqpDiscovery)
           (org.reactivestreams Publisher)
           (java.util Map)))

(def ^:dynamic *muon-config* nil)

(defprotocol ClientConnection
  (query [this service-url params])
  (command [this service-url item])
  (subscribe [this service-url params]))

(defrecord MuonService [muon]
  ClientConnection
  (query [this service-url params]
    (let [evb (MuonResourceEventBuilder/event params)]
      (.withUri evb service-url)
      (log/trace (pr-str (from-java (.build evb))))
      (->> (.query muon (.build evb) Map)
           .get .getResponseEvent .getDecodedContent)))
  (subscribe [this service-url params]
    (let [ch (chan)]
      (go
        (let [failsafe-ch (chan)]
          (.subscribe muon service-url Map params (rx/subscriber failsafe-ch))
          (loop [ev (<! failsafe-ch) timeout 1]
            (if (nil? ev)
              (do
                (log/info ":::::: Stream closed")
                (close! ch))
              (let [thrown? (instance? Throwable ev)]
                (if thrown?
                  (do
                    (log/info (str "::::::::::::: Stream failed, resubscribing after "
                                   timeout "ms..."))
                    (Thread/sleep timeout)
                    (.subscribe muon service-url Map params (rx/subscriber failsafe-ch)))
                  (>! ch (mcu/keywordize ev)))
                (recur (<! failsafe-ch) (if thrown? (* 2 timeout) 1)))))))
      ch))
  (command [this service-url item]
    (let [evb (MuonResourceEventBuilder/event item)]
      (.withUri evb service-url)
      (->> (.command muon service-url (.build evb) Map)
           .get .getResponseEvent .getDecodedContent))))

(defmulti muon-client (fn [url _ & _] (class url)))

(defmethod muon-client String [url service-name & tags]
  (let [discovery (AmqpDiscovery. url)
        muon (Muon. discovery)]
    (.setServiceIdentifer muon service-name)
    (dorun (map #(.addTag muon %) tags))
    (.extend (AmqpTransportExtension. url) muon)
    (.start muon)
    (Thread/sleep 2000)
    (->MuonService muon)))

(defmacro with-muon [muon & body]
  `(binding [*muon-config* ~muon]
     ~@body))

(defn stream-subscription
  [service-url & {:keys [from stream-type stream-name]
                  :or {from (System/currentTimeMillis) stream-type :hot
                       stream-name "events"}}]
  (let [params (mcu/dekeywordize {:from (str from) :stream-type stream-type
                                  :stream-name stream-name})]
    (log/info ":::::::: CLIENT SUBSCRIBING" service-url params)
    (subscribe *muon-config* service-url params)))

(defn query-event [service-url params]
  (let [item-json (mcu/dekeywordize params)]
    (log/info ":::::::: CLIENT QUERYING" service-url item-json)
    (mcu/keywordize (into {} (query *muon-config* service-url item-json)))))

(defn post-event [service-url item]
  (let [item-json (mcu/dekeywordize item)]
    (mcu/keywordize (into {} (command *muon-config* service-url item-json)))))

