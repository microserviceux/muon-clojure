(ns muon-clojure.core-test
  (:use midje.sweet)
  (:use muon-clojure.client)
  (:require [clojure.test :refer :all]
            [muon-clojure.server :refer :all]
            [muon-clojure.common :as mcc]
            [com.stuartsierra.component :as component]
            [clojure.core.async :refer [to-chan <!!]]))

(set! (. io.muoncore.channel.async.StandardAsyncChannel echoOut) true)

(defrecord TestMSImpl []
  MicroserviceStream
  (stream-mappings [this]
    [{:endpoint "stream-test" :type :hot-cold
      :fn-process (fn [params]
                    (to-chan
                     [{:val 1} {:val 2} {:val 3} {:val 4} {:val 5}]))}])
  MicroserviceRequest
  (request-mappings [this]
    [{:endpoint "post-endpoint"
      :fn-process (fn [resource]
                    {:val (inc (:val resource))})}
     {:endpoint "get-endpoint"
      :fn-process (fn [resource] {:test :ok})}]))

(let [uuid (.toString (java.util.UUID/randomUUID))
      _ (println "!!!!!!! Before muon")
      ms (component/start (micro-service "amqp://localhost" uuid
                                         ["dummy" "test"] (->TestMSImpl)))
      _ (println "!!!!!!! After muon")]
  (let [c (muon-client "amqp://localhost" (str uuid "-client")
                       "dummy" "test" "client")]
    (let [get-val
          (with-muon c (request! (str "request://" uuid "/get-endpoint")
                                 {:test :ok}))
          _ (println "After get-val")
          post-val
          (with-muon c (request! (str "request://" uuid "/post-endpoint")
                                 {:val 1}))
          _ (println "After post-val")
          stream-channel
          (with-muon c (subscribe!
                         (str "stream://" uuid "/stream-test")))
          _ (println "After stream-channel")
          stream-channel-order
          (with-muon c (subscribe!
                         (str "stream://" uuid "/stream-test")))
          _ (println "After stream-channel-order")
          not-ordered (<!! (clojure.core.async/reduce
                             (fn [prev n] (concat prev `(~n)))
                             '() stream-channel-order))
          _ (println "After not-ordered")
          post-many-vals
          (with-muon c (doall
                         (map (fn [_]
                                (request! (str "request://" uuid "/post-endpoint")
                                            {:val 1}))
                              (range 0 5))))]
      (println "!!!!!!!!!! All set")
      (fact "Query works as expected"
            get-val => {:test "ok"})
      (fact "Post works as expected"
            post-val => {:val 2.0})
      (fact "First element retrieved from stream is the first element provided by the service"
            (<!! stream-channel) => {:val 1.0})
      (fact "Stream results come ordered"
            (= not-ordered (sort-by :val not-ordered)) => true)
      (fact "Posting many times in a row works as expected"
            post-many-vals => (take 5 (repeat {:val 2.0})))
      (println not-ordered)))
  (component/stop ms))

