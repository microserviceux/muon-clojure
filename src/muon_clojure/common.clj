(ns muon-clojure.common
  (:require [clojure.tools.logging :as log]
            [muon-clojure.utils :as mcu]
            [clojure.java.data :as java]
            [muon-clojure.rx :as rx])
  (:import (io.muoncore Muon MuonStreamGenerator)
           (io.muoncore.future MuonFuture ImmediateReturnFuture MuonFutures)
           (io.muoncore.extension.amqp.discovery AmqpDiscovery)
           (org.reactivestreams Publisher)
           (io.muoncore.protocol.reactivestream
            ReactiveStreamSubscriptionRequest)
           (io.muoncore.protocol.reactivestream.server
            PublisherLookup$PublisherType
            ReactiveStreamServerHandlerApi$PublisherGenerator)
           (io.muoncore.protocol.requestresponse.server
            RequestResponseServerHandlerApi$Handler
            RequestWrapper
            HandlerPredicates)
           (java.util Map)))

(def type-mappings {:hot-cold PublisherLookup$PublisherType/HOT_COLD
                    :hot PublisherLookup$PublisherType/HOT
                    :cold PublisherLookup$PublisherType/COLD})

(defn stream-source [muon endpoint-name type gen-fn]
  (.publishGeneratedSource
   muon (str "/" endpoint-name)
   (get type-mappings type (get type-mappings :hot-cold))
   (reify ReactiveStreamServerHandlerApi$PublisherGenerator
     (^Publisher generatePublisher
      [this ^ReactiveStreamSubscriptionRequest request]
      (let [params (into {} (.getArgs request))
            stream-type (if-let [res (get params "stream-type"
                                          (:stream-type params))]
                          res (if (nil? type) :hot-cold type))]
        (log/trace "stream-source" (pr-str params))
        (rx/publisher gen-fn (assoc params :stream-type stream-type)))))))

(defn on-request [muon endpoint-name res-fn]
  (.handleRequest
   muon
   (HandlerPredicates/path (str "/" endpoint-name))
   Map
   (reify RequestResponseServerHandlerApi$Handler
     (^void handle [this ^RequestWrapper query-event]
      (log/trace "handle" (pr-str query-event))
      (let [resource (mcu/keywordize
                      (into {} (-> query-event .getRequest .getPayload)))]
        (log/trace "on-request" (pr-str resource))
        (.ok query-event (mcu/dekeywordize (res-fn resource))))))))

