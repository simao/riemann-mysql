(ns riemann-mysql.core-test
  (:require [clojure.test :refer :all]
            [riemann.client :as riemann]
            [clojure.tools.cli :refer [parse-opts]]
            [riemann-mysql.core :refer :all]))

(deftest send-riemann-alert-test
  (testing "Adds riemann-mysql tag to event and sends it to riemann"
    (with-redefs [riemann/send-event (fn [_ b] b)]
      (is (= {:tags ["riemann-mysql"]} (send-riemann-alert 0 {}))))))

(deftest send-riemann-alert-tags-merge-test
  (testing "Merges existent tags before forwarding to riemann"
    (with-redefs [riemann/send-event (fn [_ b] b)]
      (is (= {:tags ["my tag" "riemann-mysql"] :metric 2}
             (send-riemann-alert 0 {:metric 2 :tags ["my tag"]}))))))

(deftest transform-state-with-threshold-test
  (testing "status depends on size of delay"
    (let [t {:critical 3200 :warning 1200}]
      (is (= (:state (transform-state-with-threshold {:metric 0} t)) "ok"))
      (is (= (:state (transform-state-with-threshold {:metric 50} t)) "ok"))
      (is (= (:state (transform-state-with-threshold {:metric 1201} t)) "warning"))
      (is (= (:state (transform-state-with-threshold {:metric nil} t)) "warning"))
      (is (= (:state (transform-state-with-threshold {:state "ok" :metric nil} t)) "ok"))
      (is (= (:state (transform-state-with-threshold {:metric 3601} t)) "critical")))))

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

(deftest default-cli-arguments
  (testing "uses default command line arguments"
    (let [result (parse-opts [] cli-options)
          options (:options result)]
      (is (= 5 (:interval options)))
      (is (= "127.0.0.1" (:mysql-host options))))))

(deftest accepts-cli-arguments
  (testing "accepts known arguments in cli"
    (let [result (parse-opts ["-m" "localhost" "-i" "30"] cli-options)
          options (:options result)]
      (is (= 30 (:interval options)))
      (is (= "localhost/127.0.0.1" (str (:mysql-host options)))))))

(deftest run-check-fn-critical-test
  (testing "adjusts state when connection count is too high"
    (let [result (run-check-fn check-conn-count 10
                               (fn [_] (range 5000)) {:critical 20 :warning 10})]
      (is (= "critical" (:state result))))))

(deftest run-check-fn-warning-test
  (testing "adjusts state when connection count is too high"
    (let [result (run-check-fn check-conn-count 10
                               (fn [_] (range 10)) {:critical 20 :warning 10})]
      (is (= "warning" (:state result))))))

(deftest run-check-fn-ok-test
  (testing "adjusts state when connection count is too high"
    (let [result (run-check-fn check-conn-count 10
                               (fn [_] (range 2)) {:critical 20 :warning 10})]
      (is (= "ok" (:state result))))))

(deftest run-check-fn-ttl-test
  (testing "ttl should be passed on in the generated event"
    (let [result (run-check-fn check-conn-count 22 (fn [_] (vec [{} {} {}])) {:critical 10 :warning 10})]
      (is (= 22 (:ttl result))))))

