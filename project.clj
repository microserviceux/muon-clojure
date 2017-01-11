(defproject io.muoncore/muon-clojure "7.1.5"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :repositories [["snapshots"
                  {:url
                   "https://simplicityitself.artifactoryonline.com/simplicityitself/muon/"
                   :creds :gpg}]
                 ["releases" "https://simplicityitself.artifactoryonline.com/simplicityitself/repo/"]]
  :aot :all
  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]
  #_#_:jvm-opts ["-agentpath:/opt/local/share/profiler/libyjpagent.jnilib"]
  :main muon-clojure.core
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [com.google.code.gson/gson "2.8.0"]
                 [org.clojure/core.async "0.2.395"]
                 [org.slf4j/slf4j-log4j12 "1.7.22"]
                 [org.reactivestreams/reactive-streams "1.0.0.final"]
                 [org.clojure/java.data "0.1.1"]
                 [midje "1.8.3"]
                 [com.stuartsierra/component "0.3.2"]
                 [io.muoncore/muon-core "7.1.5"]
                 [io.muoncore/muon-event "7.1.5"]
                 [io.muoncore/muon-transport-amqp "7.1.5"]
                 [io.muoncore/muon-discovery-amqp "7.1.5"]]
  :plugins [[lein-midje "3.2"]])
