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

(let [m (muon "amqp://localhost" "dummy-test-service" ["dummy" "test"])
      ms (->TestMicroservice m)]
  (start-server! ms)
  (let [c (muon-client "amqp://localhost" "dummy-test-client"
                       "dummy" "test" "client")]
    (let [get-val
          (with-muon c (query-event "muon://dummy-test-service/get-endpoint"
                                    {:test :ok}))
          post-val
          (with-muon c (post-event "muon://dummy-test-service/post-endpoint"
                                   {:val 1}))
          stream-channel
          (with-muon c (stream-subscription
                         "muon://dummy-test-service/stream-test"))]
      (fact "Query works as expected"
            get-val => {:test "ok"})
      (fact "Post works as expected"
            post-val => {:val 2.0})
      (fact "Stream works as expected"
            (<!! stream-channel) => {:val 1.0}))))

