(ns riemann-mysql.checks-test
  (:require [clojure.test :refer :all]
            [riemann-mysql.checks :refer :all]))
  
(deftest check-slave-error-status-test
  (testing "returns an event representing an error if an error occurs"
    (let [result (check-slave-status (fn [_] (/ 1 0)))]
      (is (nil? (:metric result)))
      (is (= "critical" (:state result)))
      (is (re-find #"Divide by zero" (:description result)))
      (is (re-find #"ERROR:" (:description result))))))

(deftest check-slave-success-status-test
  (testing "returns an event representing the current slave delay"
    (let [result (check-slave-status (fn [_] [{:seconds_behind_master 0 :slave_sql_running_state "updating"}]))]
      (is (re-find #"running_state: " (:description result)))
      (is (= 0 (:metric result)))
      )))

(deftest check-slave-status-custom-threshold-test
  (testing "returns an event representing the current slave delay"
    (let [result (check-slave-status (fn [_] [{:seconds_behind_master 100 :slave_sql_running_state "updating"}]))]
      )))

(deftest check-slave-success-includes-duration-test
  (testing "returns an event representing the current slave delay"
    (let [result (check-slave-status (fn [_] [{:seconds_behind_master 60 :slave_sql_running_state "updating"}]))]
      (is (re-find #"1 minute" (:description result)))
      )))

(deftest check-proc-count-error-test
  (testing "returns an error event if an error occurs"
    (let [result (check-proc-count nil)]
      (is (nil? (:metric result))))))

(deftest check-proc-count-success-test
  (testing "returns number of connections as metric"
    (let [result (check-proc-count (fn [_] (vec [{} {} {}])))]
      (is (= 3 (:metric result))))))

(deftest check-aborted-connects-test
  (testing "returns number of aborted connections as metric"
    (let [result (check-aborted-connects (fn [_] (vec [{:value "20"}])))]
      (is (= "mysql_aborted_connects" (:service result)))
      (is (= 20 (:metric result))))))

(deftest check-aborted-connects-nan-test
  (testing "returns a critical metric when :value is NaN"
    (let [result (check-aborted-connects (fn [_] (vec [{:value "NaN"}])))]
      (is (= "critical" (:state result)))
      (is (= nil (:metric result))))))

(deftest check-aborted-connects-fails-test
  (testing "returns a critical metric when could not get :value"
    (let [result (check-aborted-connects (fn [_] (vec [{}])))]
      (is (= "critical" (:state result)))
      (is (= nil (:metric result))))))

(deftest check-max-used-connections-test
  (testing "returns number of used connections as metric"
    (let [result (check-max-used-connections (fn [_] (vec [{:value "20"}])))]
      (is (= "mysql_max_used_connections" (:service result)))
      (is (= 20 (:metric result))))))
