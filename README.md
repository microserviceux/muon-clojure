# muon-clojure

A Clojure client/server library for interacting with Muon microservices.

## Installation

`muon-clojure` is deployed in clojars.org:

`[io.muoncore/muon-clojure "6.4-20160412105345"]`

## Usage

### Client API

To import the library:

```clojure
(use 'muon-clojure.client)
```

#### Creating a muon client

```clojure
(def m (muon-client "amqp://mq-url" "service-name" "tag1" "tag2" "tag3"))
```

#### Sending a query or command

```clojure
(with-muon m (request! "request://target-service/endpoint" {:foo "bar"}))
```

#### Subscribe to a stream

```clojure
(require '[clojure.core.async :as async :refer [go <!]])

(let [ch (with-muon m (subscribe! "stream://target-service/endpoint"
                                  :from 0
                                  :stream-type "hot-cold"
                                  :stream-name "my-stream"))]
  (go (loop [elem (<! ch)] (println (pr-str elem)) (recur (<! ch)))))
```

#### Send an event to an eventstore

NOTE: This functionality is only available if there is an eventstore available subscribed to the same AMQP server. If you see the following message when initialising the client

```
INFO  client:6 - Eventstore not found, event functionality not available!
```

then the calls to `(event)` won't be successful.

```clojure
(with-muon m (event! {:event-type "type" :stream-name "my-stream" :payload {:my :data}}))
```

For an explanation of the possible fields of the event, please refer to [https://github.com/microserviceux/documentation/blob/master/implementmuon/protocol/event/v1.adoc](https://github.com/microserviceux/documentation/blob/master/implementmuon/protocol/event/v1.adoc).

## License

Copyright © 2015 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
