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

(deftest check-conn-count-error-test
  (testing "returns an error event if an error occurs"
    (let [result (check-conn-count nil)]
      (is (nil? (:metric result))))))

(deftest check-conn-count-success-test
  (testing "returns number of connections as metric"
    (let [result (check-conn-count (fn [_] (vec [{} {} {}])))]
      (is (= 3 (:metric result))))))
