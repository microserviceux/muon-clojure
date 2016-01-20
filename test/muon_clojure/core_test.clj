(ns muon-clojure.core-test
  (:use midje.sweet)
  (:use muon-clojure.client)
  (:require [clojure.test :refer :all]
            [muon-clojure.server :refer :all]
            [muon-clojure.common :as mcc]
            [com.stuartsierra.component :as component]
            [clojure.core.async :refer [to-chan <!!]])
  (:import (com.google.common.eventbus EventBus)))

(defrecord TestMSImpl []
  MicroserviceStream
  (stream-mappings [this]
    [{:endpoint "stream-test" :type :hot-cold
      :fn-process (fn [params]
                    (to-chan
                     [{:val 1} {:val 2} {:val 3} {:val 4} {:val 5}]))}
     {:endpoint "stream-test-0" :type :hot-cold
      :fn-process (fn [params]
                    (to-chan
                     []))}
     {:endpoint "stream-test-1" :type :hot-cold
      :fn-process (fn [params]
                    (to-chan
                     [{:val 1}]))}])
  MicroserviceRequest
  (request-mappings [this]
    [{:endpoint "post-endpoint"
      :fn-process (fn [resource]
                    {:val (inc (:val resource))})}
     {:endpoint "get-endpoint"
      :fn-process (fn [resource] {:test :ok})}]))

(defn ch->seq [ch]
  (<!! (clojure.core.async/reduce
        (fn [prev n] (concat prev `(~n))) '() ch)))

(defn endpoint->ch [c uuid endpoint]
  (with-muon c (subscribe!
                (str "stream://" uuid "/" endpoint))))

(def uuid (.toString (java.util.UUID/randomUUID)))
(def ms (component/start
         (micro-service {:rabbit-url #_"amqp://localhost" :local
                         :service-identifier uuid
                         :tags ["dummy" "test"]
                         :implementation (->TestMSImpl)})))
(def c (muon-client #_"amqp://localhost" :local (str uuid "-client")
                    "dummy" "test" "client"))

(defn post-val [uuid]
  (request! (str "request://" uuid "/post-endpoint") {:val 1}))

(defn post-vals [c uuid n]
  (let [s (repeatedly #(post-val uuid))]
    (with-muon c (doall (take n s)))))

(defn sample [f]
  (key (first (sort-by val (frequencies (take 10 (repeatedly f)))))))

(let [get-val
      (with-muon c (request! (str "request://" uuid "/get-endpoint")
                             {:test :ok}))
      post-val
      (with-muon c (request! (str "request://" uuid "/post-endpoint")
                             {:val 1}))]
  (fact "Query works as expected" get-val => {:test "ok"})
  (fact "Post works as expected" post-val => {:val 2.0}))

(fact "First element retrieved from stream is the first element provided by the service"
      (sample #(<!! (endpoint->ch c uuid "stream-test")))
      => {:val 1.0})
(fact "Stream results come ordered"
      (sample #(let [not-ordered (ch->seq (endpoint->ch c uuid "stream-test"))]
                 (= not-ordered (sort-by :val not-ordered))))
      => true)
(fact "There are 0 elements"
      (sample #(count (ch->seq (endpoint->ch c uuid "stream-test-0"))))
      => 0)
(fact "There is 1 element"
      (sample #(count (ch->seq (endpoint->ch c uuid "stream-test-1"))))
      => 1)
(fact "There are 5 elements"
      (sample #(count (ch->seq (endpoint->ch c uuid "stream-test"))))
      => 5)
(fact "Posting many times in a row works as expected"
      (sample #(post-vals c uuid 5)) => (take 5 (repeat {:val 2.0})))

(component/stop c)
(component/stop ms)
