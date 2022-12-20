(defproject offline-gallery-generator "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [me.raynes/fs "1.4.6"]
                 [io.joshmiller/exif-processor "0.2.0"]
                 [image-resizer "0.1.10"]
                 [clojure.java-time "0.3.2"]
                 [hiccup "1.0.5"]
                 [org.clojure/tools.cli "1.0.206"]]
  :main ^:skip-aot offline-gallery-generator.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
