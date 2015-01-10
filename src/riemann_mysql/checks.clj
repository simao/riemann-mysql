(ns riemann-mysql.checks
  (:require [clojure.string :as string]
            [riemann-mysql.util :as util]))

(defmacro try-alert-build [defaults exp]
  `(try
     ~exp
     (catch Throwable e#
       (assoc ~defaults :state "critical"
              :description (str "ERROR: " (.getMessage e#))))))

(defn -global-status [varname query-fn]
  "runs a SHOW GLOBAL STATUS query and returns the value of the first result"
  (Integer/parseInt
   (:value
    (first
     (query-fn
      (format "SHOW GLOBAL STATUS LIKE '%s' /* riemann-mysql */" varname))))))

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
                                          (str "delay: " (util/seconds-to-duration-str seconds)))
                            running_state (when (not (nil? state)) (str "running_state: " state))
                            ]
                        (assoc a :metric seconds
                               :description (string/join ", " [running_state delay-str])))))))

(defn check-proc-count
  [query-fn]
  (let [iquery-fn #(query-fn "show processlist; /* riemann-mysql */")
        a {:service "mysql_proc_count" :description nil :metric nil}]
    (try-alert-build a (assoc a :metric (count (iquery-fn)) :state "ok"))))

(defn check-aborted-connects
  ([query-fn]
   (let [a {:service "mysql_aborted_connects" :description nil :metric nil}]
     (try-alert-build
      a (assoc a :metric (-global-status "aborted_connects" query-fn))))))

(defn check-max-used-connections
  ([query-fn]
   (let [a {:service "mysql_max_used_connections" :description nil :metric nil}]
     (try-alert-build
      a (assoc a :metric (-global-status "max_used_connections" query-fn))))))
  
