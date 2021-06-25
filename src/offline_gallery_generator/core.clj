(ns offline-gallery-generator.core
  (:require [me.raynes.fs :as fs]
            [exif-processor.core :as ec]
            [image-resizer.format :as format]
            [image-resizer.core :refer :all]
            [clojure.java.io :as io]
            [java-time :as time]
            [hiccup.core :as hiccup-core]
            [hiccup.page :as hiccup-page]))


(defn- glob->regex
  "Takes a glob-format string and returns a regex."
  [s]
  (loop [stream s
         ;; default to case insensitive
         re "(?i)"
         curly-depth 0]
    (let [[c j] stream]
      (cond
        (nil? c) (re-pattern
                    ; We add ^ and $ since we check only for file names
                  (str "^" (if (= \. (first s)) "" "(?=[^\\.])") re "$"))
        (= c \\) (recur (nnext stream) (str re c c) curly-depth)
        (= c \/) (recur (next stream) (str re (if (= \. j) c "/(?=[^\\.])"))
                        curly-depth)
        (= c \*) (recur (next stream) (str re "[^/]*") curly-depth)
        (= c \?) (recur (next stream) (str re "[^/]") curly-depth)
        (= c \{) (recur (next stream) (str re \() (inc curly-depth))
        (= c \}) (recur (next stream) (str re \)) (dec curly-depth))
        (and (= c \,) (< 0 curly-depth)) (recur (next stream) (str re \|)
                                                curly-depth)
        (#{\. \( \) \| \+ \^ \$ \@ \%} c) (recur (next stream) (str re \\ c)
                                                 curly-depth)
        :else (recur (next stream) (str re c) curly-depth)))))


(defn search-files [dir match-string]
  (let [pattern (glob->regex match-string)]
    (fs/find-files dir pattern)))

(defn extract-meta-data [file-path]
  (let [ex (ec/exif-for-filename file-path)
        file-date (get ex "Date/Time Digitized")]
    ;; TODO Date Object instead of string
    {:created-at (time/local-date-time "yyyy:MM:dd HH:mm:ss" (if (nil? file-date)
                                                               "1970:01:01 00:00:01"
                                                               file-date))
     :file-path (str file-path)}))

(defn make-thumbnails [image-path]
  (let [thumbnail-path (str (fs/parent image-path)
                            "/thumbnail/"
                            (fs/name image-path)
                            (fs/extension image-path))]
    (do
      (io/make-parents thumbnail-path)
      (format/as-file
       (resize (io/file image-path) 250 250)
       thumbnail-path))))

(defn generate-hrefs [links]
  [:ul
   (for [x links]
     [:li [:img {:src x}] [:a {:href x} x]])])
(comment
  (let [local-files (search-files "/Users/kanishkkumar/Documents/AdonaiImages" "{*.jpg}")
        thumbnails (map (fn [file]
                          (->> file
                               extract-meta-data
                               :file-path
                               make-thumbnails))
                        local-files)
        img-links  (generate-hrefs thumbnails)
        html (hiccup-page/html5 {:lang "en"} [:body img-links])
        write-into-file (do
                          (fs/create (fs/file "/Users/kanishkkumar/Documents/AdonaiImages/index.html"))
                          (spit "/Users/kanishkkumar/Documents/AdonaiImages/index.html" html))]

    write-into-file)



  (hiccup-page/html5 {:lang "en"} [:body [:ul (->> (search-files "/Users/kanishkkumar/Documents/AdonaiImages" "{*.jpg}")
                                                   (map (fn [file]
                                                          (->> file
                                                               extract-meta-data
                                                               :file-path
                                                               make-thumbnails
                                                               (fn [href] [:li [:a {:href href} href]])))))]]))


(defn -main [& args]
  (println args))


#_Searchfiles







