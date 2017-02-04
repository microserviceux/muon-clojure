(ns muon-clojure.rx
  (:use muon-clojure.utils)
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :refer [go-loop go <! >! <!! >!!
                                        chan buffer close!]])
  (:import (org.reactivestreams Publisher Subscriber Subscription)))

(defn close-subscription [s ch]
  (log/debug "::::::::::::: SUBSCRIBER" s "closing channel...")
  (close! ch)
  (.onComplete s))

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
              (close-subscription s ch))))))
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
  (let [active-s (ref nil)
        counter (ref 1024)]
    (reify Subscriber
      (^void onSubscribe [this ^Subscription s]
       (log/info "onSubscribe" s)
       (.request s 1024)
       (dosync (alter active-s (fn [_] s))))
      (^void onNext [this ^Object obj]
       (when-not (nil? @active-s)
         (log/debug "onNext:::::::::::: CLIENTSIDE[-> " (.hashCode ch)
                    "][" (.hashCode this) "]" obj)
         (let [res (>!! ch obj)]
           (log/trace "Push:" res)
           (dosync
            (if res
              (if (= @counter 512)
                (.request @active-s 1024)
                (alter counter #(+ % 1024)))
              (do
                (.cancel @active-s)
                (alter active-s (fn [_] nil))))))))
      (^void onError [this ^Throwable t]
       (log/info "onError" (.getMessage t))
       (>!! ch t))
      (^void onComplete [this]
       (when-not (nil? @active-s)
         (log/info "onComplete")
         (close! ch)
         (dosync
          (.cancel @active-s)
          (alter active-s (fn [_] nil))))))))
