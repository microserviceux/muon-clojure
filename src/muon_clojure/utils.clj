(ns muon-clojure.utils
  (:require [clojure.tools.logging :as log])
  (:import (java.util LinkedList)
           (com.google.common.eventbus EventBus)
           (io.muoncore SingleTransportMuon MuonStreamGenerator)
           (io.muoncore.codec.json JsonOnlyCodecs)
           (io.muoncore.config AutoConfiguration)
           (io.muoncore.memory.discovery InMemDiscovery)
           (io.muoncore.memory.transport InMemTransport)
           (io.muoncore.extension.amqp
            DefaultServiceQueue AMQPMuonTransport
            DefaultAmqpChannelFactory)
           (io.muoncore.extension.amqp.discovery
            AmqpDiscovery)
           (io.muoncore.transport ServiceCache)
           (io.muoncore.extension.amqp.rabbitmq09
            RabbitMq09ClientAmqpConnection RabbitMq09QueueListenerFactory)))

(def local-event-bus (EventBus.))
(def local-discovery (InMemDiscovery.))

(defmulti muon-instance (fn [x _ _] x))

(defmethod muon-instance :local [url service-name tags]
  (let [config (doto (AutoConfiguration.)
                 (.setServiceName service-name)
                 (.setAesEncryptionKey "abcde12345678906")
                 (.setTags (LinkedList. tags)))
        muon-transport (InMemTransport. config local-event-bus)
        muon (SingleTransportMuon. config local-discovery muon-transport)]
    {:muon muon :discovery local-discovery :transport muon-transport}))

(defmethod muon-instance :default [url service-name tags]
  (let [connection (RabbitMq09ClientAmqpConnection. url)
        queue-factory (RabbitMq09QueueListenerFactory.
                       (.getChannel connection))
        discovery (AmqpDiscovery. queue-factory connection
                                  (ServiceCache.) (JsonOnlyCodecs.))]
    (.start discovery)
    (Thread/sleep 5000)
    (let [service-queue (DefaultServiceQueue. service-name connection)
          channel-factory (DefaultAmqpChannelFactory.
                            service-name queue-factory connection)
          muon-transport (AMQPMuonTransport.
                          url service-queue channel-factory)
          config (doto (AutoConfiguration.)
                   (.setServiceName service-name)
                   (.setAesEncryptionKey "abcde12345678906")
                   (.setTags (LinkedList. tags)))
          muon (SingleTransportMuon. config discovery muon-transport)]
      {:muon muon :discovery discovery :transport muon-transport})))

(defn dekeywordize
  "Converts the keys in a map from keywords to strings."
  [m]
  (if (map? m)
    (apply merge
           (map (fn [[k v]] {(if (keyword? k)
                               (name k)
                               (str k))
                             (if (map? v)
                               (dekeywordize v)
                               (if (sequential? v)
                                 (into (empty v) (map dekeywordize v))
                                 (if (keyword? v)
                                   (name v)
                                   v)))})
                m))
    (if (sequential? m)
      (into (empty m) (map dekeywordize m))
      (if (keyword? m)
        (name m)
        m))))

(defn keywordize
  "Converts the keys in a map from strings to keywords"
  [m]
  #_(log/info "keywordize" (class m) ":" (pr-str m))
  (if (or
        (instance? java.util.HashMap m)
        (instance? com.google.gson.internal.LinkedTreeMap m))
    (keywordize (into {} m))
    (if (map? m)
      (apply merge
             (map (fn [[k v]] {(if (keyword? k)
                                 k
                                 (keyword k))
                               (if (instance? com.google.gson.internal.LinkedTreeMap v)
                                 (keywordize (into {} v))
                                 (if (map? v)
                                   (keywordize v)
                                   (if (instance? java.util.ArrayList v)
                                     (keywordize (into [] v))
                                     (if (sequential? v)
                                       (into (empty v) (map keywordize v))
                                       v))))})
                  m))
      (if (instance? java.util.ArrayList m)
        (keywordize (into [] m))
        (if (sequential? m)
          (into (empty m) (map keywordize m))
          m)))))

