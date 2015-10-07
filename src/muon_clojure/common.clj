(ns muon-clojure.common
  (:require [clojure.tools.logging :as log]
            [muon-clojure.utils :as mcu]
            [muon-clojure.rx :as rx])
  (:import (io.muoncore Muon MuonStreamGenerator)
           (io.muoncore.future MuonFuture ImmediateReturnFuture MuonFutures)
           (io.muoncore.transport.resource MuonResourceEvent
                                           MuonResourceEventBuilder)
           (io.muoncore.extension.amqp AmqpTransportExtension)
           (io.muoncore.extension.amqp.discovery AmqpDiscovery)
           (org.reactivestreams Publisher)
           (java.util Map)))

(defmulti decode-map #(.getContentType %))

(defmethod decode-map "application/json" [m]
  (mcu/keywordize (into {} (.getDecodedContent m))))

(defn stream-source [ms endpoint-name gen-fn]
  (.streamSource (:m ms) (str "/" endpoint-name) Map
                 (reify MuonStreamGenerator
                   (^Publisher generatePublisher [this ^Map params]
                     (log/trace "streamSource" (pr-str params))
                     (rx/publisher gen-fn params)))))

(defn on-command [ms endpoint-name res-fn]
  (.onCommand (:m ms)
              (str "/" endpoint-name)
              Map
              (reify io.muoncore.MuonService$MuonCommand
                (^MuonFuture onCommand [_ ^MuonResourceEvent resource]
                  (log/trace "onCommand" (pr-str (decode-map resource)))
                  (MuonFutures/immediately
                      (mcu/dekeywordize
                        (res-fn (decode-map resource))))))))

(defn on-query [ms endpoint-name res-fn]
  (.onQuery (:m ms)
            (str "/" endpoint-name)
            Map
            (reify io.muoncore.MuonService$MuonQuery
                (^MuonFuture onQuery [_ ^MuonResourceEvent resource]
                  (log/trace "before onQuery" (.getDecodedContent resource))
                  (log/trace "onQuery" (pr-str (decode-map resource)))
                  (let [dk (mcu/dekeywordize
                             (res-fn (decode-map resource)))]
                    (log/trace "ImmediateReturnFuture." (pr-str dk))
                    (MuonFutures/immediately dk))))))

