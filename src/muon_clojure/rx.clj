(ns muon-clojure.rx
  (:use muon-clojure.utils)
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :refer [go-loop go <! >! <!! >!!
                                        chan buffer close!]])
  (:import (org.reactivestreams Publisher Subscriber Subscription)))

(defn subscription [s ch]
  (reify Subscription
    (request [this n]
      (log/trace "::::: SUBSCRIPTION" s ":: request" n)
      (loop [remaining n]
        (when-not (= 0 remaining)
          (if-let [item (<!! ch)]
            (do
              (log/trace "onNext" (dekeywordize item))
              (.onNext s (dekeywordize item))
              (recur (dec remaining)))
            (do
              (log/debug "::::::::::::: SUBSCRIBER" s "closing channel...")
              (close! ch)
              (.onComplete s))))))
    (cancel [this]
      (close! ch))))

(defn publisher [gen-fn params]
  (log/info (str "::::::::::::::::::::::::::::::: " (pr-str params)))
  (reify Publisher
    (^void subscribe [this ^Subscriber s]
     (log/info "subscribe::::::::: SUBSCRIBER" s)
     (let [ch (gen-fn (keywordize params))
           sobj (subscription s ch)]
       (.onSubscribe s sobj)))))

(def open-subs (ref {}))

(defn subscriber [ch]
  (reify Subscriber
    (^void onSubscribe [this ^Subscription s]
     (dosync (alter open-subs assoc this s))
     (log/info "onSubscribe" s)
     (.request s 1))
    (^void onNext [this ^Object obj]
     (log/debug "onNext:::::::::::: CLIENTSIDE[" (.hashCode this) "]" obj)
     (go (>! ch obj))
     (let [s (get @open-subs this)]
       (.request s 1)))
    (^void onError [this ^Throwable t]
     (go (>! ch t))
     (let [s (get @open-subs this)]
       (.cancel s))
     (dosync (alter open-subs dissoc this))
     (log/info "onError" (.getMessage t)))
    (^void onComplete [this]
     (close! ch)
     (let [s (get @open-subs this)]
       (.cancel s))
     (dosync (alter open-subs dissoc this))
     (log/info "onComplete"))))
