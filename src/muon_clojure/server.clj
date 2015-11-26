(ns muon-clojure.server
  (:require [clojure.tools.logging :as log]
            [muon-clojure.common :as mcc]
            [muon-clojure.utils :as mcu])
  (:import (io.muoncore Muon MuonStreamGenerator)
           (io.muoncore.future MuonFuture ImmediateReturnFuture)
           (io.muoncore.extension.amqp.discovery AmqpDiscovery)
           (org.reactivestreams Publisher)
           (java.util Map)))

(defprotocol MicroserviceEngine
  (expose-stream! [this endpoint stream-type fn-process])
  (expose-request! [this endpoint fn-process]))
(defprotocol MicroserviceStream (stream-mappings [this]))
(defprotocol MicroserviceRequest (request-mappings [this]))

(defn muon [rabbit-url service-identifier tags]
  (let [muon (mcu/muon-instance rabbit-url service-identifier tags)]
    muon))

(defn expose-streams! [ms mappings]
  (dorun (map #(expose-stream!
                ms (:endpoint %) (:stream-type %) (:fn-process %))
              mappings)))

(defn expose-requests! [ms mappings]
  (dorun (map #(expose-request! ms (:endpoint %) (:fn-process %))
              mappings)))

(defn start-server! [ms]
  (if (satisfies? MicroserviceStream ms)
    (expose-streams! ms (stream-mappings ms)))
  (if (satisfies? MicroserviceRequest ms)
    (expose-requests! ms (request-mappings ms)))
  (Thread/sleep 2000)
  ms)

