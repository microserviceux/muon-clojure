(ns muon-clojure.server
  (:require [clojure.tools.logging :as log]
            [muon-clojure.common :as mcc]
            [clojure.core.async :refer [go-loop go <! >! chan buffer close!]]
            [muon-clojure.rx :as rx]
            [com.stuartsierra.component :as component]
            [muon-clojure.utils :as mcu])
  (:import (io.muoncore Muon MuonStreamGenerator)
           (io.muoncore.future MuonFuture ImmediateReturnFuture)
           (com.google.common.eventbus EventBus)
           (java.util.function Predicate)
           (io.muoncore.extension.amqp.discovery AmqpDiscovery)
           (org.reactivestreams Publisher)
           (java.util Map)))

(defprotocol MicroserviceStream (stream-mappings [this]))
(defprotocol MicroserviceRequest (request-mappings [this]))
(defprotocol ClientConnection
  (wiretap [this])
  (request [this service-url params])
  (subscribe [this service-url params]))

(defn expose-streams! [muon mappings]
  (dorun (map #(mcc/stream-source
                muon (:endpoint %) (:stream-type %) (:fn-process %))
              mappings)))

(defn expose-requests! [muon mappings]
  (dorun (map #(mcc/on-request muon (:endpoint %) (:fn-process %))
              mappings)))

(defn impl-request [muon service-url params]
  (log/trace (pr-str params))
  (let [response (.request muon service-url params Map)]
    (log/trace "Response:" (pr-str response))
    (let [got-response (.get response)
          payload (.getPayload got-response)]
      (log/trace "Response payload:" (pr-str payload))
      payload)))

(defn params->uri [service-url params]
  (let [query-string (map #(str (name (key %)) "=" (val %)) params)
        url-string (str service-url "?"
                        (clojure.string/join "&" query-string))
        uri (java.net.URI. url-string)]
    (log/trace "Query string:" url-string)
    uri))

(defn impl-subscribe [muon service-url params]
  (let [uri (params->uri service-url params)
        ch (chan)]
    (go
      (let [failsafe-ch (chan)]
        (.subscribe muon uri Map (rx/subscriber failsafe-ch))
        (log/trace "Starting processing loop...")
        (loop [ev (<! failsafe-ch) timeout 1]
          (if (nil? ev)
            (do
              (log/info ":::::: Stream closed")
              (close! ch))
            (let [thrown? (instance? Throwable ev)]
              (log/trace "Client received" (pr-str ev))
              (if thrown?
                (do
                  (log/info (str "::::::::::::: Stream failed, resubscribing after "
                                 timeout "ms..."))
                  (Thread/sleep timeout)
                  (.subscribe muon uri Map (rx/subscriber failsafe-ch)))
                (>! ch (mcu/keywordize ev)))
              (recur (<! failsafe-ch) (if thrown? (* 2 timeout) 1)))))
        (log/trace "Subscription ended")))
    ch))

(defrecord Microservice [options]
  ClientConnection
  (wiretap [this] (:wiretap this))
  (request [this service-url params]
    (impl-request (:muon this) service-url params))
  (subscribe [this service-url params]
    (impl-subscribe (:muon this) service-url params))
  component/Lifecycle
  (start [component]
    (if (nil? (:muon component))
      (let [{:keys [rabbit-url service-identifier tags implementation]} options
            muon-instance (mcc/muon-instance rabbit-url service-identifier tags)
            muon (:muon muon-instance)
            tc (.getTransportControl muon)
            taps (if (:debug options)
                   (let [tap (.tap tc
                                   (reify Predicate (test [_ _] true)))
                         ch (chan)]
                     (.subscribe tap (rx/subscriber ch))
                     {:wiretap ch :tap tap})
                   {})]
        (set! (. io.muoncore.channel.async.StandardAsyncChannel echoOut)
              (true? (:debug options)))
        (when-not (nil? implementation)
          (if (satisfies? MicroserviceStream implementation)
            (expose-streams! muon (stream-mappings implementation)))
          (if (satisfies? MicroserviceRequest implementation)
            (expose-requests! muon (request-mappings implementation))))
        (merge component (merge muon-instance taps)))
      component))
  (stop [{:keys [muon discovery transport] :as component}]
    (if (nil? (:muon component))
      component
      (do
        (try
          (if-let [wiretap (:wiretap component)]
            (close! wiretap))
          (if-let [tap (:tap component)]
            (.shutdown tap))
          ;; TODO: Re-check if transport and discovery
          ;;       have to be shut down
          (.shutdown muon))
        (merge component {:muon nil :discovery nil :transport nil
                          :wiretap nil :tap nil})))))

(defn micro-service [options]
  (map->Microservice {:options options}))
