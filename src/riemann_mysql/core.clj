(ns riemann-mysql.core
  (:require [riemann-mysql.run_checks :as run_checks]
            [riemann-mysql.checks :as checks]
            [clojure.java.jdbc :as j]
            [clojure.string :as string]
            [riemann.client :as riemann]
            [clojure.tools.logging :as log]
            [clojure.tools.cli :refer [parse-opts]])
  (:import (java.net InetAddress))
  (:gen-class))

(defn send-riemann-alert [client event]
  "Receives an `event' hash and sends it to riemann using `client'"
  (let [e (update-in event [:tags] (fnil conj []) "riemann-mysql")]
    (riemann/send-event client e) e))

(defn start-check-loop [db-opts riemann-host interval check-fns]
  (let [mysql-props {:subprotocol "mysql"
                     :subname (str "//" (:host db-opts) ":3306/mysql")
                     :user (:user db-opts)
                     :password ""}
        rclient (riemann/tcp-client {:host riemann-host})]
    (j/with-db-connection [db-con mysql-props]
      (loop [query-fn #(j/query db-con %1)]
        (try
          (doseq [f check-fns]
            (log/info (send-riemann-alert rclient (f query-fn))))
          (catch Throwable ex (log/error ex "Error: ")))
        (Thread/sleep (* interval 1000))
        (recur query-fn)))))

(defn build-check-fns [cli-options]
  (let [slave-thresholds {:critical (:slave-critical cli-options)
                          :warning (:slave-warning cli-options)}
        conn-thresholds {:critical (:conn-count-critical cli-options)
                         :warning (:conn-count-warning cli-options)}
        tags (:tags cli-options)
        ttl (* 2 (:interval cli-options))]
    (list #(run_checks/run-check-fn checks/check-conn-count ttl %1 conn-thresholds tags)
          #(run_checks/run-check-fn checks/check-slave-status ttl %1 slave-thresholds tags))))

(def cli-options
  [["-m" "--mysql-host HOST" "mysql hostname to check"
    :default "127.0.0.1"
    :parse-fn #(InetAddress/getByName %)]

   [nil "--mysql-user USER" "mysql username to use"
    :default "root"]

   ["-t" "--tag TAG" "Tags to add to events sent to riemann. Can be specified multiple times"
    :id :tags
    :default []
    :assoc-fn (fn [opt id tag] (update-in opt [id] (fnil conj []) tag))
    ]

   ["-i" "--interval INTERVAL" "interval to pause between checks (seconds)"
    :default 5
    :parse-fn #(Integer/parseInt %)]

   ["-c" "--slave-critical N" "critical threshold fo mysql slave (seconds)"
    :default 3600
    :parse-fn #(Integer/parseInt %)]

   ["-w" "--slave-warning N" "warning threshold fo mysql slave (seconds)"
    :default 1200
    :parse-fn #(Integer/parseInt %)]

   [nil "--conn-count-critical N" "critical threshold fo mysql connection count"
    :default 1000
    :parse-fn #(Integer/parseInt %)]

   [nil "--conn-count-warning N" "warning threshold fo mysql connection count"
    :default 800
    :parse-fn #(Integer/parseInt %)]

   ["-r" "--riemann-host HOST" "address for a riemann server"
    :default "localhost"]

   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Checks a mysql server and sends metrics to riemann."
        ""
        "Usage: riemann-mysql [options]"
        ""
        "Options:"
        options-summary]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (log/info msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
     (:help options) (exit 0 (usage summary))
     errors (exit 1 (error-msg errors)))

    (log/info "Starting riemann-mysql on" (:mysql-host options))

    (start-check-loop {:host (:mysql-host options) :user (:mysql-user options)}
                      (:riemann-host options)
                      (:interval options)
                      (build-check-fns options))))
