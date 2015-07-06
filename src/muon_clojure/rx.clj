(ns muon-clojure.rx
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :refer [go-loop go <! >! chan buffer close!]])
  (:import (org.reactivestreams Publisher Subscriber Subscription)))

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

