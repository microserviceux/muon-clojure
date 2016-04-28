(ns muon-clojure.core
  (:require [muon-clojure.utils :as mcu]
            [muon-clojure.common :as mcc]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [go-loop go <! >! chan buffer close!]]
            [muon-clojure.rx :as rx]
            [com.stuartsierra.component :as component])
  (:use clojure.java.data)
  (:import (io.muoncore.protocol.requestresponse Response)
           (io.muoncore.exception MuonException)
           (org.reactivestreams Publisher)
           (io.muoncore.protocol.event.client DefaultEventClient)
           (io.muoncore.protocol.event ClientEvent)
           (io.muoncore Muon MuonStreamGenerator)
           (io.muoncore.api MuonFuture ImmediateReturnFuture)
           (io.muoncore.channel ChannelConnection
                                ChannelConnection$ChannelFunction)
           (io.muoncore.protocol.event.server EventServerProtocolStack)
           (com.google.common.eventbus EventBus)
           (java.util.function Predicate)
           (java.util Map)))

(def ^:dynamic *muon-config* nil)

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
  (let [response (.request muon service-url params)]
    (log/trace "Response:" (pr-str response))
    (let [got-response (.get response)
          payload (.getPayload got-response Map)]
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

(declare muon-client)

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
            options (if (and (not (nil? implementation))
                             (satisfies? MicroserviceEvent implementation))
                      (update options :tags conj "eventstore")
                      options)
            muon (apply muon-client
                        (:url options) (:service-name options) (:tags options))
            tc (.getTransportControl (:muon muon))
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
            (expose-streams! (:muon muon) (stream-mappings implementation)))
          (if (satisfies? MicroserviceRequest implementation)
            (expose-requests! (:muon muon) (request-mappings implementation)))
          (if (satisfies? MicroserviceEvent implementation)
            (let [handler (channel-function implementation)
                  event-stack (EventServerProtocolStack.
                               handler (.getCodecs (:muon muon))
                               (.getDiscovery (:muon muon)))]
              (.registerServerProtocol
               (.getProtocolStacks (:muon muon)) event-stack))))
        (merge component (merge taps muon)))
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
        (merge component {:muon nil :event-client nil
                          :wiretap nil :tap nil})))))

(defn micro-service [options]
  (map->Microservice {:options (assoc options :debug false)}))

(defn muon-client [url service-name & tags]
  (let [muon-instance (mcc/muon-instance url service-name tags)
        ec (try
             ;; TODO: Make event client handling smarter
             (DefaultEventClient. muon-instance)
             (catch MuonException e
               (log/info (str "Eventstore not found, "
                              "event functionality not available!"))
               nil))
        client (map->Microservice
                {:muon muon-instance :event-client ec})]
    #_(Thread/sleep 2000)
    client))

(defmacro with-muon [muon & body]
  `(binding [*muon-config* ~muon]
     ~@body))

(defn event! [{:keys [event-type stream-name schema caused-by
                      caused-by-relation #_service-id payload]
               :as event}]
  ;; TODO: Make event client handling smarter
  (if-let [ec (:event-client *muon-config*)]
    (let [ev (ClientEvent. event-type stream-name schema caused-by
                           caused-by-relation #_service-id
                           (mcu/dekeywordize payload))
          res (.event ec ev)]
      (merge event {:order-id (.getOrderId res)
                    :event-time (.getEventTime res)}))
    (throw (UnsupportedOperationException. "Eventstore not available"))))

(defn subscribe!
  [service-url & {:keys [from stream-type stream-name]
                  :or {from (System/currentTimeMillis) stream-type nil
                       stream-name "events"}}]
  (let [params (mcu/dekeywordize {:from (str from) :stream-type stream-type
                                  :stream-name stream-name})]
    (log/info ":::::::: CLIENT SUBSCRIBING" service-url params)
    (subscribe *muon-config* service-url params)))

(defn request! [service-url params]
  (let [item-json (mcu/dekeywordize params)
        payload (mcu/keywordize
                 (into {} (request *muon-config* service-url item-json)))
        payload (if (contains? payload :_muon_wrapped_value)
                  (:_muon_wrapped_value payload) payload)]
    (log/info ":::::::: CLIENT REQUESTING" service-url item-json)
    payload))
