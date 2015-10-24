(defproject io.muoncore/muon-clojure "5.3.7"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["muoncore" "http://dl.bintray.com/muoncore/muon-java"]
                 ["reactor" "http://repo.spring.io/libs-release"]]
  :aot :all
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [com.google.code.gson/gson "2.4"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.slf4j/slf4j-log4j12 "1.7.12"]
                 [org.reactivestreams/reactive-streams "1.0.0.final"]
                 [org.clojure/java.data "0.1.1"]
                 [midje "1.7.0"]
                 [io.muoncore/muon-core "5.4.4"]
                 [io.muoncore/muon-transport-amqp "5.4.4"]
                 [io.muoncore/muon-discovery-amqp "5.4.4"]]
  :plugins [[lein-midje "3.1.3"]])
