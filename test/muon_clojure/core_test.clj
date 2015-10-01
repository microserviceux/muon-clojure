(ns muon-clojure.core-test
  (:use midje.sweet)
  (:use muon-clojure.client)
  (:require [clojure.test :refer :all]
            [muon-clojure.server :refer :all]
            [muon-clojure.common :as mcc]
            [clojure.core.async :refer [to-chan <!!]]))

(defrecord TestMicroservice [m]
  MicroserviceStream
  (expose-stream! [this]
    ;; TODO: provide a proper channel generator function and an endpoint-name
    (mcc/stream-source this "stream-test"
                       (fn [params]
                         (to-chan
                           [{:val 1}
                            {:val 2}
                            {:val 3}
                            {:val 4}
                            {:val 5}]))))
  MicroserviceCommand
  (expose-post! [this]
    ;; TODO: provide proper POST listener functions
    (mcc/on-command this "post-endpoint" (fn [resource]
                                           {:val (inc (:val resource))})))
  MicroserviceQuery
  (expose-get [this]
    ;; TODO: provide proper GET listener functions
    (mcc/on-query this "get-endpoint" (fn [resource] {:test :ok}))))

(let [uuid (.toString (java.util.UUID/randomUUID))
      m (muon "amqp://localhost" uuid ["dummy" "test"])
      ms (->TestMicroservice m)]
  (start-server! ms)
  (let [c (muon-client "amqp://localhost" (str uuid "-client")
                       "dummy" "test" "client")]
    (let [get-val
          (with-muon c (query-event (str "muon://" uuid "/get-endpoint")
                                    {:test :ok}))
          post-val
          (with-muon c (post-event (str "muon://" uuid "/post-endpoint")
                                   {:val 1}))
          stream-channel
          (with-muon c (stream-subscription
                         (str "muon://" uuid "/stream-test")))
          stream-channel-order
          (with-muon c (stream-subscription
                         (str "muon://" uuid "/stream-test")))
          not-ordered (<!! (clojure.core.async/reduce
                             (fn [prev n] (concat prev `(~n)))
                             '() stream-channel-order))
          post-many-vals
          (with-muon c (doall
                         (map (fn [_]
                                (post-event (str "muon://" uuid "/post-endpoint")
                                            {:val 1}))
                              (range 0 5))))]
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
      (println not-ordered))))

