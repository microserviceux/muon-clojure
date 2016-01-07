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
            res (get params "stream-type" (:stream-type params))
            stream-type (if (or (nil? res)
                                (= "" (clojure.string/trim res)))
                          (if (nil? type) :hot-cold type)
                          res)
            final-params (dissoc
                          (assoc params "stream-type" stream-type)
                          :stream-type)]
        (log/trace "stream-source" (pr-str params))
        (log/trace "final stream-type" stream-type)
        (log/trace "final params" final-params)
        (rx/publisher gen-fn final-params))))))

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
