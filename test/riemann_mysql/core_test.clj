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
      (is (= [] (:tags options)))
      (is (= "localhost/127.0.0.1" (str (:mysql-host options)))))))

(deftest accepts-multiple-tags-test
  (testing "cli accepts tags"
      (let [result (parse-opts ["-t" "tag0" "--tag" "tag1"] cli-options)
            options (:options result)]
        (is (= ["tag0", "tag1"] (:tags options))))))



