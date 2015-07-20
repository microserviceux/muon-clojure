(ns muon-clojure.server
  (:require [clojure.data.json :as json]
            [clojure.java.data :as j]
            [clojure.tools.logging :as log])
  (:import (io.muoncore Muon MuonStreamGenerator)
           (io.muoncore.future MuonFuture ImmediateReturnFuture)
           (io.muoncore.transport.resource MuonResourceEvent)
           (io.muoncore.extension.amqp AmqpTransportExtension)
           (io.muoncore.extension.amqp.discovery AmqpDiscovery)
           (org.reactivestreams Publisher)
           (java.util Map)))

(def amazon-url
  "amqp://localhost")

(defprotocol MicroserviceStream (expose-stream! [this]))
(defprotocol MicroserviceCommand (expose-post! [this]))
(defprotocol MicroserviceQuery (expose-get [this]))

(defn muon [rabbit-url service-identifier tags]
  (let [discovery (AmqpDiscovery. rabbit-url)
        muon (Muon. discovery)]
    (.setServiceIdentifer muon service-identifier)
    (dorun (map #(.addTag muon %) tags))
    (.extend (AmqpTransportExtension. rabbit-url) muon)
    (.start muon)
    muon))

(defn start-server! [ms]
  (if (satisfies? MicroserviceStream ms)
    (expose-stream! ms))
  (if (satisfies? MicroserviceCommand ms)
    (expose-post! ms))
  (if (satisfies? MicroserviceQuery ms)
    (expose-get ms))
  (Thread/sleep 2000)
  ms)

