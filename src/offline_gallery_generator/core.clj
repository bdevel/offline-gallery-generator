(ns offline-gallery-generator.core
  (:require [me.raynes.fs :as fs]
            [exif-processor.core :as ec]
            [image-resizer.format :as format]
            [image-resizer.core :refer :all]
            [clojure.java.io :as io]))


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
  (let [ex (ec/exif-for-filename file-path)]
    ;; TODO Date Object instead of string
    {:created-at (get ex "Date/Time Digitized")
     :file-path (str file-path)}))

(defn make-thumbnails [image-path]
  (format/as-file
   (resize (io/file image-path) 250 250)
   image-path
   ;; TODO Make the directory
   #_(str (fs/parent image-path) "/thumbnail/" image-path)))
(comment
  ;; *.jpg *.jpeg *.png
  ;; (?i)(.*)\.file-ext$
  (->> (search-files "/Users/kanishkkumar/Documents/AdonaiImages" "{*.jpg}")
       (map extract-meta-data)
       first
       :file-path
       make-thumbnails))


(defn -main [& args]
  (println args))


#_Searchfiles







