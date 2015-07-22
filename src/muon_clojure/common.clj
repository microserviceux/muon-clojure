(ns muon-clojure.common
  (:require [clojure.tools.logging :as log]
            [muon-clojure.rx :as rx])
  (:import (io.muoncore Muon MuonStreamGenerator)
           (io.muoncore.future MuonFuture ImmediateReturnFuture)
           (io.muoncore.transport.resource MuonResourceEvent)
           (io.muoncore.extension.amqp AmqpTransportExtension)
           (io.muoncore.extension.amqp.discovery AmqpDiscovery)
           (org.reactivestreams Publisher)
           (java.util Map)))

(defn dekeywordize
  "Converts the keys in a map from keywords to strings."
  [m]
  (if (map? m)
    (apply merge
           (map (fn [[k v]] {(if (keyword? k)
                               (name k)
                               (str k))
                             (if (map? v)
                               (dekeywordize v)
                               (if (sequential? v)
                                 (into (empty v) (map dekeywordize v))
                                 (if (keyword? v)
                                   (name v)
                                   v)))})
                m))
    (if (sequential? m)
      (into (empty m) (map dekeywordize m))
      (if (keyword? m)
        (name m)
        m))))

(defn keywordize
  "Converts the keys in a map from strings to keywords"
  [m]
  (log/info "keywordize" (class m) ":" (pr-str m))
  (if (instance? com.google.gson.internal.LinkedTreeMap m)
    (keywordize (into {} m))
    (if (map? m)
      (apply merge
             (map (fn [[k v]] {(if (keyword? k)
                                 k
                                 (keyword k))
                               (if (instance? com.google.gson.internal.LinkedTreeMap v)
                                 (keywordize (into {} v))
                                 (if (map? v)
                                   (keywordize v)
                                   (if (instance? java.util.ArrayList v)
                                     (keywordize (into [] v))
                                     (if (sequential? v)
                                       (into (empty v) (map keywordize v))
                                       v))))})
                  m))
      (if (instance? java.util.ArrayList m)
        (keywordize (into [] m))
        (if (sequential? m)
          (into (empty m) (map keywordize m))
          m)))))

(defmulti decode-map #(.getContentType %))

(defmethod decode-map "application/json" [m]
  (keywordize (into {} (.getDecodedContent m))))

(defn stream-source [ms endpoint-name gen-fn]
  (.streamSource (:m ms) (str "/" endpoint-name) Map
                 (reify MuonStreamGenerator
                   (^Publisher generatePublisher [this ^Map params]
                     (log/info "streamSource" (pr-str params))
                     (rx/publisher gen-fn params)))))

(defn on-command [ms endpoint-name res-fn]
  (.onCommand (:m ms)
              (str "/" endpoint-name)
              Map
              (reify io.muoncore.MuonService$MuonCommand
                (^MuonFuture onCommand [_ ^MuonResourceEvent resource]
                  (log/info "onCommand" (pr-str (decode-map resource)))
                  (ImmediateReturnFuture. (dekeywordize
                                            (res-fn (decode-map resource))))))))

(defn on-query [ms endpoint-name res-fn]
  (.onQuery (:m ms)
            (str "/" endpoint-name)
            Map
            (reify io.muoncore.MuonService$MuonQuery
                (^MuonFuture onQuery [_ ^MuonResourceEvent resource]
                  (log/info "before onQuery" (.getDecodedContent resource))
                  (log/info "onQuery" (pr-str (decode-map resource)))
                  (let [dk (dekeywordize
                             (res-fn (decode-map resource)))]
                    (log/info "ImmediateReturnFuture." (pr-str dk))
                    (ImmediateReturnFuture. dk))))))

