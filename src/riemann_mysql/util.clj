(ns riemann-mysql.util
  (:require [clojure.string :as string]))

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
           (string/join ", ")))))
