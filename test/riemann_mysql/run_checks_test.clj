(ns riemann-mysql.run-checks-test
  (:require [clojure.test :refer :all]
            [riemann-mysql.run_checks :refer :all]
            [riemann-mysql.checks :as checks]))

(deftest transform-state-with-threshold-test
  (testing "status depends on size of delay"
    (let [t {:critical 3200 :warning 1200}]
      (is (= (:state (transform-state-with-threshold {:metric 0} t)) "ok"))
      (is (= (:state (transform-state-with-threshold {:metric 50} t)) "ok"))
      (is (= (:state (transform-state-with-threshold {:metric 1201} t)) "warning"))
      (is (= (:state (transform-state-with-threshold {:metric nil} t)) "warning"))
      (is (= (:state (transform-state-with-threshold {:state "ok" :metric nil} t)) "ok"))
      (is (= (:state (transform-state-with-threshold {:metric 3601} t)) "critical")))))

(deftest run-check-fn-critical-test
  (testing "adjusts state when connection count is too high"
    (let [result (run-check-fn checks/check-conn-count 10
                               (fn [_] (range 5000)) {:critical 20 :warning 10})]
      (is (= "critical" (:state result))))))

(deftest run-check-fn-warning-test
  (testing "adjusts state when connection count is too high"
    (let [result (run-check-fn checks/check-conn-count 10
                               (fn [_] (range 10)) {:critical 20 :warning 10})]
      (is (= "warning" (:state result))))))

(deftest run-check-fn-ok-test
  (testing "adjusts state when connection count is too high"
    (let [result (run-check-fn checks/check-conn-count 10
                               (fn [_] (range 2)) {:critical 20 :warning 10})]
      (is (= "ok" (:state result))))))

(deftest run-check-fn-ttl-test
  (testing "ttl should be passed on in the generated event"
    (let [result (run-check-fn checks/check-conn-count 22 (fn [_] (vec [{} {} {}])) {:critical 10 :warning 10})]
      (is (= 22 (:ttl result))))))

(deftest run-check-fn-tags-test
  (testing "tags should be added to the resulting event"
    (let [result (run-check-fn (fn [_] {:tags ["event-tag"]}) 22 #() {:critical 10 :warning 10} ["tag0" "tag1"])]
      (is (= ["tag0" "tag1" "event-tag"] (:tags result))))))

(deftest run-check-fn-defaults-thresholds-test
  (testing "uses default thresholds if empty hash given"
    (let [result (run-check-fn (fn [_] {:metric 1000}) 22 #() {} [])]
      (is (= "ok" (:state result))))))
