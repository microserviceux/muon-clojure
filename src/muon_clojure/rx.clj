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
      (go
        (loop [remaining n]
          (when-not (= 0 remaining)
            (if-let [item (<! ch)]
              (do
                (log/trace "onNext" (dekeywordize item))
                (.onNext s (dekeywordize item))
                (recur (dec remaining)))
              (do
                (log/debug "::::::::::::: SUBSCRIBER" s "closing channel...")
                (close! ch)
                (.onComplete s)))))))
    (cancel [this]
      (close! ch))))

(defn publisher [gen-fn params]
  (log/info (str "::::::::::::::::::::::::::::::: " (pr-str params)))
  (reify Publisher
    (^void subscribe [this ^Subscriber s]
     (log/info "subscribe::::::::: SUBSCRIBER" s)
     (let [ch (gen-fn (keywordize params))
           sobj (subscription s ch)]
       (log/info "Assigned channel:" (.hashCode ch))
       (.onSubscribe s sobj)))))

(defn subscriber [ch]
  (reify Subscriber
    (^void onSubscribe [this ^Subscription s]
     (log/info "onSubscribe" s)
     (.request s Long/MAX_VALUE))
    (^void onNext [this ^Object obj]
     (log/debug "onNext:::::::::::: CLIENTSIDE[-> " (.hashCode ch)
                "][" (.hashCode this) "]" obj)
     (let [res (>!! ch obj)]
       (log/trace "Push:" res)))
    (^void onError [this ^Throwable t]
     (log/info "onError" (.getMessage t))
     (>!! ch t))
    (^void onComplete [this]
     (log/info "onComplete")
     (close! ch))))
