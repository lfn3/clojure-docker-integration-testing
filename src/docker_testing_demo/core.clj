(ns docker-testing-demo.core
  (:require [clojure.test :as t]
            [org.httpkit.server :as srv]
            [clojure.edn :as edn]
            [org.httpkit.client :as c])
  (:gen-class)
  (:import (java.util UUID)))

(defn server-handler [req]
  (case (:uri req)
    "/users" {:body (-> @(c/get "http://database/users")
                        :body
                        (.bytes)
                        (slurp))}
    "/create-user" (do (c/post "http://database/users" {:body (-> @(c/get "http://database/users")
                                                               :body
                                                               (.bytes)
                                                               (slurp)
                                                               (edn/read-string)
                                                               (assoc (UUID/randomUUID) {:name "HAI!" :password "A password"})
                                                               (pr-str))})
                       {:body (-> @(c/get "http://database/users")
                                  :body
                                  (.bytes)
                                  (slurp))})))

(defn server []
  (prn "Starting server on 80")
  (srv/run-server server-handler {:port 80}))

(defonce db (atom {}))

(defn database-handler [req]
  (case (:request-method req)
    :post (swap! db assoc (:uri req) (slurp (.bytes (:body req))))
    :get {:body (get @db (:uri req))}))

(defn start-database []
  (prn "Starting 'database' on 80")
  (srv/run-server database-handler {:port 80}))

(defn test []
  (print "Waiting for server to wake up...")
  (loop [res @(c/get "http://server/users")]
    (when (not (= 200 (:status res)))
      (print "...")
      (recur (c/get "http://server/users"))))
  (println "\nRunning tests")
  (->> (t/run-tests 'docker-testing-demo.core)
       (#(select-keys %1 [:error :fail]))
       (vals)
       (reduce +)                                           ;Generate the exit code from the number of fails
       (System/exit)))

(defn -main
  "Run a server, database, or tests, depending on the "
  [& args]
  (let [[type] args]
    (case type
      "server" (server)
      "database" (start-database)
      (test))))

(t/use-fixtures :each (fn [f]
                        @(c/post "http://database/users" {:body "{}"})
                        (f)))

(t/deftest users-starts-empty
  (t/is (= {} (edn/read-string (slurp (.bytes (:body @(c/get "http://server/users"))))))))


(t/deftest adding-a-user-works
  @(c/get "http://server/create-user")
  (t/is (= 1 (count (edn/read-string (slurp (.bytes (:body @(c/get "http://server/users")))))))))