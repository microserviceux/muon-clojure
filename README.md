# muon-clojure

A Clojure client/server library for interacting with Muon microservices.

## Installation

`muon-clojure` is deployed in clojars.org:

`[muon-clojure "5.3.3"]`

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

#### Sending a command

```clojure
(with-muon m (post-event "muon://target-service/endpoint" "target-stream" {:foo "bar"}))
```

#### Querying

```clojure
(with-muon m (query-event "muon://target-service/endpoint" {:param-1 "baz"}))
```

#### Subscribe to a stream

```clojure
(require [clojure.core.async :as async :refer [go <!]])

(let [ch (with-muon m (stream-subscription "muon://target-service/endpoint"
                        :from 0
                        :stream-type "hot-cold"
                        :stream-name "my-stream"))]
  (go (loop [elem (<! ch)] (println (pr-str elem)) (recur (<! ch)))))
```

## License

Copyright © 2015 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
