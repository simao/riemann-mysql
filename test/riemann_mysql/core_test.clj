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

(deftest delay-state-test
  (testing "state depends on size of delay"
    (is (= (delay-state 0) "ok"))
    (is (= (delay-state 50) "ok"))
    (is (= (delay-state 1201) "warning"))
    (is (= (delay-state 3601) "critical"))))

(deftest check-slave-error-status-test
  (testing "returns an event representing an error if an error occurs"
    (let [result (check-slave-status nil 10)]
      (is (nil? (:metric result)))
      (is (= "critical" (:state result)))
      (is (re-find #"db-spec null is missing" (:description result)))
      (is (re-find #"ERROR:" (:description result))))))

(deftest check-slave-success-status-test
  (testing "returns an event representing the current slave delay"
    (let [result (check-slave-status nil 10
                  (fn [] [{:seconds_behind_master 0 :slave_sql_running_state "updating"}]))]
      (is (re-find #"running_state: " (:description result)))
      (is (= "ok" (:state result))))))

(deftest check-slave-success-includes-duration-test
    (testing "returns an event representing the current slave delay"
    (let [result (check-slave-status nil 10
                  (fn [] [{:seconds_behind_master 60 :slave_sql_running_state "updating"}]))]
      (is (re-find #"1 minute" (:description result)))
      (is (= "ok" (:state result))))))

(deftest check-conn-count-error-test
  (testing "returns an error event if an error occurs"
    (let [result (check-conn-count nil 10)]
      (is (nil? (:metric result)))
      (is (= "critical" (:state result))))))

(deftest check-conn-count-success-test
  (testing "returns a success event if an error occurs"
    (let [result (check-conn-count nil 10 #(vec [{} {} {}]))]
      (is (= "ok" (:state result)))
      (is (= 3 (:metric result))))))

(deftest default-cli-arguments
  (testing "uses default command line arguments"
    (let [result (parse-opts [] cli-options)
          options (:options result)]
      (is (= 5 (:interval options)))
      (is (= "127.0.0.1" (:mysql-host options))))))

(deftest accepts-cli-arguments
  (testing "accepts known arguments in cli"
    (let [result (parse-opts ["-m" "riemann.com" "-i" "30"] cli-options)
          options (:options result)]
      (is (= 30 (:interval options)))
      (is (= "riemann.com" (:mysql-host options))))))
