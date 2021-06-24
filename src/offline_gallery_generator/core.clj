(ns offline-gallery-generator.core
  (:require [me.raynes.fs :as fs]
            [exif-processor.core :as ec]
            [image-resizer.format :as format]
            [image-resizer.core :refer :all]
            [clojure.java.io :as io]
            [java-time :as time]))


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
;;
(comment
  (->> (search-files "/Users/kanishkkumar/Documents/AdonaiImages" "{*.jpg}")
       ;;second
       ;;(print "\n")
       (map (fn [file]
              (->> file
                   extract-meta-data
                   :file-path
                   make-thumbnails))))
  (clojure-version)
  (fs/name "/Users/kanishkkumar/Documents/AdonaiImages/DSC_0005_250x166_250x166.JPG")
  (make-thumbnails "/Users/kanishkkumar/Documents/AdonaiImages/DSC_0005.JPG")
  (io/make-parents "/Users/kanishkkumar/Documents/AdonaiImages/abc/asdas/asdasasd/ffff/rrr/rr/s/s/ss/df/df/d")
  (.mkdir (java.io.File. "/Users/kanishkkumar/Documents/AdonaiImages/abc"))
  (time/local-date "yyyy:MM:dd HH:mm:ss" "999:01:01 00:00:01")
  (time/local-date "MM/yyyy/dd" "09/2015/28"))


(defn -main [& args]
  (println args))


#_Searchfiles







