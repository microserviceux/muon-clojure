(ns muon-clojure.rx
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :refer [go-loop go <! >! chan buffer close!]])
  (:import (org.reactivestreams Publisher Subscriber Subscription)))

(defn publisher [gen-fn params]
  (log/info (str "::::::::::::::::::::::::::::::: " (pr-str params)))
  (reify Publisher
    (^void subscribe [this ^Subscriber s]
      (log/info "subscribe::::::::: SUBSCRIBER" s)
      (let [ch (gen-fn params)]
        (go
          (loop [item (<! ch)]
            (if (nil? item)
              (do
                (log/debug "::::::::::::: SUBSCRIBER" s "closing channel...")
                (.onComplete s)
                (close! ch))
              (do
                (.onNext s item)
                (recur (<! ch))))))))))

(defn subscriber [ch]
  (reify Subscriber
    (^void onSubscribe [this ^Subscription s]
      (log/info "onSubscribe" s))
    (^void onNext [this ^Object obj]
      (log/debug "onNext:::::::::::: CLIENTSIDE[" (.hashCode this) "]" obj)
      (go (>! ch obj)))
    (^void onError [this ^Throwable t]
      (log/info "onError" (.getMessage t))
      (go (>! ch t)))
    (^void onComplete [this]
      (close! ch)
      (log/info "onComplete"))))

