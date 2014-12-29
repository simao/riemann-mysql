(ns riemann-mysql.run_checks
  (:require [clojure.string :as string]))

(defn transform-state-with-threshold
  [event & [{ :keys [critical warning]}]]
  (if (nil? (:metric event)) (assoc event :state (or (:state event) "warning"))
      (assoc event :state (condp <= (:metric event)
                            critical "critical"
                            warning "warning"
                            "ok"))))

;; TODO: tags not optional
(defn run-check-fn
  [f ttl query-fn thresholds & [tags]]
  (update-in
   (merge {:ttl ttl}
          (transform-state-with-threshold (f query-fn) thresholds))
   [:tags]
   (fnil #(vec (concat tags %1)) [])))
