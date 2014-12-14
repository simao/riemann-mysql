(ns riemann-mysql.core
  (:require [clojure.java.jdbc :as j]
            [clojure.string :as string]
            [riemann.client :as riemann]
            [clojure.tools.logging :as log]
            [clojure.tools.cli :refer [parse-opts]])
  (:import (java.net InetAddress))
  (:gen-class))

(defn seconds-to-duration-str [seconds]
  (if (or (nil? seconds) (= 0 seconds))
    "0 seconds"
  (let [floor (comp int #(Math/floor %))
        s (mod seconds 60)

        mf (floor (/ seconds 60))
        m (mod mf 60)

        hf (floor (/ mf 60))
        h (mod hf 24)

        d (floor (/ hf 60))]
    (->> [["day" d] ["hour" h] ["minute" m] ["second" s]]
         (drop-while (comp zero? last))
         (map (fn [[w v]] (str v " " w (if (= v 1) "" "s"))))
         (string/join ", ")))))

(defn send-riemann-alert [client event]
  "Receives an `event' hash and sends it to riemann using `client'"
  (let [e (update-in event [:tags] (fnil conj []) "riemann-mysql")]
  (riemann/send-event client e) e))

(defmacro try-alert-build [defaults exp]
  `(try
     ~exp
     (catch Throwable e#
       (assoc ~defaults :state "critical" 
              :description (str "ERROR: " (.getMessage e#))))))

(defn transform-state-with-threshold
  [event & [{ :keys [critical warning]}]]
  (if (nil? (:metric event)) (assoc event :state (or (:state event) "warning"))
      (assoc event :state (condp <= (:metric event)
                             critical "critical"
                             warning "warning"
                             "ok"))))

(defn check-slave-status
  "Returns an event representing the current mysql slave status"
  ([query-fn]
   (let [iquery-fn #(query-fn "show slave status /* riemann-mysql */")
         a {:service "mysql_slave_delay" :description nil :metric nil}]
    (try-alert-build a
               (let [result (first (iquery-fn))
                     seconds (:seconds_behind_master result)
                     state (:slave_sql_running_state result)
                     delay-str (if (nil? seconds) ""
                                   (str "delay: " (seconds-to-duration-str seconds)))
                     running_state (when (not (nil? state)) (str "running_state: " state))
                     ]
                 (assoc a :metric seconds
                         :description (string/join ", " [running_state delay-str])))))))

(defn check-conn-count
  [query-fn]
  "Get current connection status from db and return an event hash
  representing the current state"
  (let [iquery-fn #(query-fn "show processlist; /* riemann-mysql */")
        a {:service "mysql_conn_count" :description nil :metric nil}]
    (try-alert-build a (assoc a :metric (count (iquery-fn)) :state "ok"))))

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

(defn run-check-fn [f ttl query-fn thresholds]
  (merge {:ttl ttl} (transform-state-with-threshold (f query-fn) thresholds)))

(defn build-check-fns [cli-options]
  (let [slave-thresholds {:critical (:slave-critical cli-options)
                          :warning (:slave-warning cli-options)}
        conn-thresholds {:critical (:conn-count-critical cli-options)
                         :warning (:conn-count-warning cli-options)}
        ttl (* 2 (:interval cli-options))]
    (list #(run-check-fn check-conn-count ttl %1 conn-thresholds)
          #(run-check-fn check-slave-status ttl %1 slave-thresholds))))

(def cli-options
  [["-m" "--mysql-host HOST" "mysql hostname to check"
    :default "127.0.0.1"
    :parse-fn #(InetAddress/getByName %)]

   [nil "--mysql-user USER" "mysql username to use"
    :default "root"]

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

