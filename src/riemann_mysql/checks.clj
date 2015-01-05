(ns riemann-mysql.checks
  (:require [clojure.string :as string]
            [riemann-mysql.util :as util]))

(defmacro try-alert-build [defaults exp]
  `(try
     ~exp
     (catch Throwable e#
       (assoc ~defaults :state "critical"
              :description (str "ERROR: " (.getMessage e#))))))

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

(defn check-conn-count
  [query-fn]
  "Get current connection status from db and return an event hash
  representing the current state"
  (let [iquery-fn #(query-fn "show processlist; /* riemann-mysql */")
        a {:service "mysql_conn_count" :description nil :metric nil}]
    (try-alert-build a (assoc a :metric (count (iquery-fn)) :state "ok"))))

(defn check-aborted-connects
  ([query-fn]
   (let [iquery-fn #(query-fn "SHOW GLOBAL STATUS LIKE 'aborted_connects' /* riemann-mysql */")
         a {:service "mysql_aborted_connects" :description nil :metric nil}]
     (try-alert-build a
                      (let [result (first (iquery-fn))
                            aborted_count (Integer/parseInt (:value result))
                            ]
                        (assoc a :metric aborted_count))))))
