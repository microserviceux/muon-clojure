(ns muon-clojure.server
  (:require [clojure.tools.logging :as log]
            [muon-clojure.common :as mcc]
            [clojure.core.async :refer [go-loop go <! >! chan buffer close!]]
            [muon-clojure.rx :as rx]
            [com.stuartsierra.component :as component]
            [muon-clojure.utils :as mcu])
  (:import (io.muoncore Muon MuonStreamGenerator)
           (io.muoncore.exception MuonException)
           (io.muoncore.protocol.event.client DefaultEventClient)
           (io.muoncore.api MuonFuture ImmediateReturnFuture)
           (io.muoncore.channel ChannelConnection
                                ChannelConnection$ChannelFunction)
           (io.muoncore.protocol.event.server EventServerProtocolStack)
           (com.google.common.eventbus EventBus)
           (java.util.function Predicate)
           (org.reactivestreams Publisher)
           (java.util Map)))

(defprotocol MicroserviceStream (stream-mappings [this]))
(defprotocol MicroserviceRequest (request-mappings [this]))
(defprotocol MicroserviceEvent (handle-event [this event]))
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
        ch (chan 1024)] ;; TODO: Increase this number?
    (go
      (let [failsafe-ch (chan)]
        (.subscribe muon uri Map (rx/subscriber failsafe-ch))
        (log/trace "Starting processing loop for" (.hashCode failsafe-ch))
        (loop [ev (<! failsafe-ch) timeout 1]
          (log/trace "Arrived" ev "for" (.hashCode failsafe-ch))
          (if (nil? ev)
            (do
              (log/info ":::::: Stream closed")
              (close! failsafe-ch)
              (close! ch))
            (let [thrown? (instance? Throwable ev)]
              (>! ch (mcu/keywordize ev))
              (log/trace "Client received" (pr-str ev))
              (if thrown?
                (if (and (instance? MuonException ev)
                         (= (.getMessage ev) "Stream does not exist"))
                  (do
                    (log/info "Stream does not exist, shutting down")
                    (close! failsafe-ch)
                    (close! ch))
                  (do
                    (log/info (str "::::::::::::: Stream failed, resubscribing after "
                                   timeout "ms..."))
                    (Thread/sleep timeout)
                    (.subscribe muon uri Map (rx/subscriber failsafe-ch))
                    (recur (<! failsafe-ch) (* 2 timeout))))
                (recur (<! failsafe-ch) 1)))))
        (log/trace "Subscription ended")))
    ch))

(defn channel-function [implementation]
  (reify ChannelConnection$ChannelFunction
    (apply [_ event-wrapper]
      (let [event-raw (.getEvent event-wrapper)
            event {:event-type (.getEventType event-raw)
                   :stream-name (.getStreamName event-raw)
                   :schema (.getSchema event-raw)
                   :caused-by (.getCausedById event-raw)
                   :caused-by-relation (.getCausedByRelation event-raw)
                   :service-id (.getService event-raw)
                   :order-id (.getOrderId event-raw)
                   :event-time (.getEventTime event-raw)
                   :payload (mcu/keywordize
                             (into {} (.getPayload event-raw)))}
            {:keys [order-id event-time]} (handle-event implementation event)]
        (.persisted event-wrapper order-id event-time)))))

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
      (let [implementation (:implementation options)
            muon (mcc/muon-instance options)
            tc (.getTransportControl muon)
            debug? (true? (:debug options))
            taps (if debug?
                   (let [tap (.tap tc
                                   (reify Predicate (test [_ _] true)))
                         ch (chan 1024)]
                     (.subscribe tap (rx/subscriber ch))
                     {:wiretap ch :tap tap})
                   {})]
        (set! (. io.muoncore.channel.async.StandardAsyncChannel echoOut)
              debug?)
        (when-not (nil? implementation)
          (if (satisfies? MicroserviceStream implementation)
            (expose-streams! muon (stream-mappings implementation)))
          (if (satisfies? MicroserviceRequest implementation)
            (expose-requests! muon (request-mappings implementation)))
          (if (satisfies? MicroserviceEvent implementation)
            (let [handler (channel-function implementation)
                  event-stack (EventServerProtocolStack.
                               handler (.getCodecs muon))]
              (.registerServerProtocol (.getProtocolStacks muon) event-stack))))
        (merge component (merge taps {:muon muon})))
      component))
  (stop [{:keys [muon] :as component}]
    (if (nil? (:muon component))
      component
      (do
        (try
          (if-let [wiretap (:wiretap component)]
            (close! wiretap))
          #_(if-let [tap (:tap component)]
            (.shutdown tap))
          ;; TODO: Re-check if transport and discovery
          ;;       have to be shut down
          (.shutdown muon))
        (merge component {:muon nil :wiretap nil :tap nil})))))

(defn micro-service [options]
  (map->Microservice {:options (assoc options :debug false)}))
