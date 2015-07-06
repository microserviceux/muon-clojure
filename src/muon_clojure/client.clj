(ns muon-clojure.client
  (:require [muon-clojure.rx :as rx]
            [clojure.core.async :refer [go-loop go <! >! chan buffer close!]]
            [clojure.tools.logging :as log])
  (:use [somnium.congomongo.coerce :only [coerce coerce-fields coerce-index-fields]])
  (:import (io.muoncore Muon MuonStreamGenerator)
           (io.muoncore.transport.resource MuonResourceEvent MuonResourceEventBuilder)
           (io.muoncore.extension.amqp AmqpTransportExtension)
           (io.muoncore.extension.amqp discovery.AmqpDiscovery)
           (org.reactivestreams Publisher)
           (java.util Map)))

#_(def amazon-url
  "amqp://sentinel:lkjljkllj@ec2-52-28-16-238.eu-central-1.compute.amazonaws.com")
(def amazon-url
  "amqp://localhost")

(def ^:dynamic *muon-config* nil)

(defn dekeywordize
  "Converts the keys in a map from keywords to strings."
  [m]
  (apply merge
         (map (fn [[k v]] {(name k)
                           (if (map? v)
                             (dekeywordize v)
                             (if (seq? v)
                               (into (empty v) (map dekeywordize v))
                               (if (keyword? v)
                                 (name v)
                                 v)))})
              m)))

(defprotocol ClientConnection
  (post [this service-url item])
  (subscribe [this service-url params]))

(defrecord MuonService [muon]
  ClientConnection
  (subscribe [this service-url params]
    (let [ch (chan)]
      (go
        (let [failsafe-ch (chan)]
          (.subscribe muon service-url Map params (rx/subscriber failsafe-ch))
          (loop [ev (<! failsafe-ch)]
            (if (nil? ev)
              (do
                (log/info ":::::: Stream closed")
                (close! ch))
              (if (instance? Throwable ev)
                (do
                  (log/info "::::::::::::: Stream failed, resubscribing")
                  (.subscribe muon service-url Map params (rx/subscriber failsafe-ch)))
                (do
                  (>! ch ev)
                  (recur (<! failsafe-ch))))))))
      ch))
  (post [this service-url item]
    (let [evb (MuonResourceEventBuilder/event item)]
      (.withUri evb service-url)
      (.post muon service-url (.build evb) Map))))

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
  (let [params (dekeywordize {:from (str from) :stream-type stream-type
                              :stream-name stream-name})]
    (log/info ":::::::: CLIENT SUBSCRIBING" service-url params)
    (subscribe *muon-config* service-url params)))

(defn post-event [service-url stream-name item]
  (let [item-json (dekeywordize item)]
    (post *muon-config* service-url {"stream-name" stream-name
                                     "payload" item-json})))

#_(with-muon (muon-client amazon-url "asap-client" "asap" "client")
  (println (stream-subscription "muon://eventstore/stream" :stream-type :hot))
  (post-event "muon://eventstore/event" {:test :ok}))

