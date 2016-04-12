(defproject io.muoncore/muon-clojure "6.4-20160412105348"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["snapshots"
                  {:url
                   "https://simplicityitself.artifactoryonline.com/simplicityitself/muon/"
                   :creds :gpg}]
                 ["releases" "https://simplicityitself.artifactoryonline.com/simplicityitself/repo/"]]
  :aot :all
  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [com.google.code.gson/gson "2.6.2"]
                 [org.clojure/core.async "0.2.374"]
                 [org.slf4j/slf4j-log4j12 "1.7.21"]
                 [org.reactivestreams/reactive-streams "1.0.0.final"]
                 [org.clojure/java.data "0.1.1"]
                 [midje "1.8.3"]
                 [com.stuartsierra/component "0.3.1"]
                 [io.muoncore/muon-core "6.4-20160412105345"]
                 [io.muoncore/muon-event "6.4-20160412105345"]
                 [io.muoncore/muon-transport-amqp "6.4-20160412105345"]
                 [io.muoncore/muon-discovery-amqp "6.4-20160412105345"]]
  :plugins [[lein-midje "3.1.3"]])
