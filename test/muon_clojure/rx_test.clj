(ns muon-clojure.rx-test
  (:require [muon-clojure.rx :as rx]
            [clojure.core.async :refer [chan to-chan <!! go-loop <! >!]]
            [midje.sweet :as midje]))

(defn plug-subscriber [p]
  (let [res (chan 1)
        ch (chan)
        s (rx/subscriber ch)]
    (.subscribe p s)
    (go-loop [elem (<! ch)]
      (if (= elem 1000)
        (>! res true)
        (do
          (println (.hashCode s) elem)
          (recur (<! ch)))))
    (when (<!! res)
      (println (.hashCode s) "has finished"))))

(let [p (rx/publisher (fn [_] (to-chan (range))) nil)]
  (dorun (map #(do
                 (println "!!!!!!!!!!!! TESTING" %)
                 (dorun (map (fn [_] (plug-subscriber p)) (range %))))
              (range))))
