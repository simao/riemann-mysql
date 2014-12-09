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

;; TODO:
;; threshold and ttl can be abstracted to some form of event building

(defn transform-state-for-threshold
  [event & { :keys [critical warning] :or {critical 3600 warning 1200} }]
  (if (nil? (:metric event)) (assoc event :state (or (:state event) "warning"))
      (assoc event :state (condp < (:metric event)
                             critical "critical"
                             warning "warning"
                             "ok"))))

(defn check-slave-status
  "Returns an event representing the current mysql slave status"
  ([ttl query-fn & [{ :keys [critical warning] :or {critical 3600 warning 1200} }]]
   (let [iquery-fn #(query-fn "show slave status /* riemann-mysql */")
         a {:service "mysql_slave_delay" :ttl ttl :description nil :metric nil}]
    (try-alert-build a
               (let [result (first (iquery-fn))
                     seconds (:seconds_behind_master result)
                     state (:slave_sql_running_state result)
                     delay-str (if (nil? seconds) ""
                                   (str "delay: " (seconds-to-duration-str seconds)))
                     running_state (when (not (nil? state)) (str "running_state: " state))
                     ]
                 (transform-state-for-threshold
                  (assoc a :metric seconds
                         :description (string/join ", " [running_state delay-str]))
                  :critical critical :warning warning))))))

(defn check-conn-count
  [ttl query-fn & [{ :keys [critical warning] :or {critical 1000 warning 900} }]]
  "Get current connection status from db and return an event hash
  representing the current state"
  (let [iquery-fn #(query-fn "show processlist; /* riemann-mysql */")
        a {:service "mysql_conn_count" :ttl ttl :description nil :metric nil}]
    (transform-state-for-threshold
     (try-alert-build a (assoc a :metric (count (iquery-fn)) :state "ok"))
     :critical critical :warning warning)))

;; TODO: Maybe this function could just accept `db-opts', a `interval'
;; riemann-host, and a list of already built functions that don't even
;; receive any arguments but build event hashes that can be sent to riemann
(defn -check-loop [db-opts interval riemann-host slave-thresholds]
  (let [mysql-props {:subprotocol "mysql"
                     :subname (str "//" (:host db-opts) ":3306/mysql")
                     :user (:user db-opts)
                     :password ""}
        rclient (riemann/tcp-client {:host riemann-host})]
    (j/with-db-connection [db-con mysql-props]
      (loop [query-fn #(j/query db-con %1)
            ttl (* 2 interval)]
        (try
          (log/info (send-riemann-alert rclient
                                        (check-slave-status ttl query-fn slave-thresholds)))
          (log/info (send-riemann-alert rclient
                                        (check-conn-count ttl query-fn)))
          (catch Throwable ex (log/error ex "Error: ")))
          (Thread/sleep (* interval 1000))
          (recur query-fn ttl)))))

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

   ["-r" "--riemann-host HOST" "address for a riemanns server"
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

    (-check-loop {:host (:mysql-host options) :user (:mysql-user options)}
                 (:interval options) (:riemann-host options)
                 {:critical (:slave-critical options) :warning (:slave-warning options)}
               )))

