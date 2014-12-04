(ns riemann-mysql.core
  (:require [clojure.java.jdbc :as j]
            [clojure.string :as string]
            [riemann.client :as riemann]
            [clojure.tools.logging :as log]
            [clojure.tools.cli :refer [parse-opts]])
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
         (clojure.string/join ", ")))))

(defn send-riemann-alert [client event]
  "Receives an `event' hash and sends it to riemann using `client'"
  (let [e (update-in event [:tags] (fnil conj []) "riemann-mysql")]
  (riemann/send-event client e) e))

(defn delay-state [seconds]
  (condp < seconds
    3600 "critical"
    1200 "warning"
    "ok"))

(defmacro try-alert-build [defaults exp]
  `(try
     ~exp
     (catch Throwable e#
       (assoc ~defaults :state "critical" 
              :description (str "ERROR: " (.getMessage e#))))))

(defn check-slave-status
  "Returns an event representing the current mysql slave status"
  ([db-con] (check-slave-status db-con
                                #(j/query db-con "show slave status /* riemann-mysql */")))
  ([db-con query-fn]
  (let [a {:service "mysql_slave_delay" :ttl 10 :description nil :metric nil}]
    (try-alert-build a
               (let [result (first (query-fn))
                     seconds (:seconds_behind_master result)
                     state (:slave_sql_running_state result)
                     delay-str (if (nil? seconds) ""
                                   (str "delay: " (seconds-to-duration-str seconds)))
                     running_state (when (not (nil? state)) (str "running_state: " state))
                     ]
                 (assoc a :metric seconds
                        :state (cond (nil? seconds) "warning" :else (delay-state seconds))
                        :description (str running_state delay-str)))))))

(defn check-conn-count
  ([db-con] (check-conn-count db-con
                              #(j/query db-con "show processlist; /* riemann-mysql */")))
  ([db-con query-fn]
  "Get current connection status from db and return an event hash
  representing the current state"
  (let [a {:service "mysql_conn_count" :ttl 10 :description nil :metric nil}]
    (try-alert-build a (assoc a :metric (count (query-fn)) :state "ok")))))


(defn -check-loop [db-opts interval riemann-host]
  (let [mysql-props {:subprotocol "mysql"
                     :subname (str "//" (:host db-opts) ":3306/mysql")
                     :user (:user db-opts)
                     :password ""}
        rclient (riemann/tcp-client {:host riemann-host})]
    (j/with-db-connection [db-con mysql-props]
      (loop []
        (try
          (doseq [f [check-conn-count check-slave-status]]
            (log/info (send-riemann-alert rclient (f db-con))))
          (catch Throwable ex (log/error ex "Error: ")))
        (Thread/sleep (* interval 1000))
      (recur)))))

(def cli-options
  [["-m" "--mysql-host HOST" "mysql hostname to check"
     :default "127.0.0.1"
     ]

   [nil "--mysql-user USER" "mysql username to use"
    :default "root"
    ]

   ["-i" "--interval INTERVAL" "interval to pause between checks (seconds)"
    :default 5
    :parse-fn #(Integer/parseInt %)
    ]

   ["-r" "--riemann-host HOST" "address for a riemanns server"
    :default "localhost"
    ]

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
                  (:interval options) (:riemann-host options))))

