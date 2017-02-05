(ns muon-clojure.rx-test
  (:require [muon-clojure.rx :as rx]
            [clojure.core.async :refer [chan to-chan <!! go-loop
                                        sliding-buffer <! >! close!]]
            [midje.sweet :as midje]))

(defn plug-subscriber [p]
  (let [res (chan 1)
        ch (chan)
        N 16
        [s ch] (rx/subscriber N)]
    (.subscribe p s)
    (go-loop [elem (<! ch)]
      (if (= elem (* N 3))
        (>! res true)
        (do
          (println (.hashCode s) elem)
          (recur (<! ch)))))
    (when (<!! res)
      (close! ch)
      (close! res)
      (println (.hashCode s) "has finished"))))

(let [p (rx/publisher (fn [_] (to-chan (range 100))) nil)]
  (dorun (map #(do
                 (println "!!!!!!!!!!!! TESTING" %)
                 (dorun (map (fn [_] (plug-subscriber p)) (range %))))
              (range 20))))
