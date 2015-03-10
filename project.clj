(defproject riemann-mysql "0.1.0-SNAPSHOT"
  :description "collects mysql metrics, sends them to riemann"
  :url "https://github.com/simao/riemann-mysql"
    :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/java.jdbc "0.3.5"]
                 [mysql/mysql-connector-java "5.1.25"]
                 [riemann-clojure-client "0.2.11"]
                 [ch.qos.logback/logback-classic "1.1.1"]
                 [org.clojure/tools.cli "0.3.1"]
                 ]
  :main ^:skip-aot riemann-mysql.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all} :user {:plugins [[cider/cider-nrepl "0.8.1"]]}} )

