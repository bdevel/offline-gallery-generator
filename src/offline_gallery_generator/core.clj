(ns offline-gallery-generator.core
  (:require [me.raynes.fs :as fs]
            [exif-processor.core :as ec]
            [image-resizer.format :as format]
            [image-resizer.core :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as clj-str]
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

(defn build-photo-from-path [file-path]
  (let [ex (ec/exif-for-filename file-path)

        exif-date (get ex "Date/Time Digitized")

        created-at (if exif-date
                     (time/local-date-time "yyyy:MM:dd HH:mm:ss" exif-date)
                     (time/local-date-time (time/instant->sql-timestamp (time/to-java-date (fs/mod-time file-path)))))]

    {:created-at created-at
     :file-path (str file-path)}))

(defn photo-gallery-destination [photo]
  ;;["2021" "08" "31"]
  (time/as (:created-at photo) :year :month-of-year :day-of-month))


(comment
  (time/as (time/local-date-time "yyyy:MM:dd HH:mm:ss" "1970:01:01 00:00:01") :year :month-of-year :day-of-month))


(defn add-photo-to-gallery [photo gallery]
  (let [dest (photo-gallery-destination photo)]
    (update-in gallery (into [:gallery-structure] dest) conj photo)))



(defn build-gallery-struct
  "Create gallery hashmap structure"
  ;; TODO Settings for searching a file
  [source-path settings]
  (reduce (fn [gallery photo-path]
            (add-photo-to-gallery (build-photo-from-path photo-path)
                                  gallery))
          {}
          (search-files source-path (get settings :file-regex "{*.jpg}"))))


(defn make-gallery-page
  "Make index html for our gallery"
  [files settings]
  ;; spit the html in the index
  ;; create a dir named THUMBNAILS with thumbnails 
  )



(defn build-walking
  "Walk through"
  [struct settings]
  (let [cur-dir (get settings :output-dir (str fs/*cwd* "/Gallery"))]
    (if (map? struct)
      (map
       (fn [k]
         (fs/mkdirs (str cur-dir "/" k))

         (build-walking (get struct k) (assoc settings :output-dir (str cur-dir "/" k))))
       (keys struct))

      (spit (str cur-dir "/index.html") (str struct)))))

(defn build-gallery
  "Create Folder and files"
  ;; 
  [gallery settings]
  (build-walking (:gallery-structure gallery) settings)
  #_(let [;;struct gallery-struct
          cur-dir (get settings :output-dir (str fs/*cwd* "/Gallery"))]
      (if (map? gallery-struct)
        (map
         (fn [folder-name]
           (fs/mkdir folder-name))
         (keys gallery-struct)))))

(comment
  (let [settings {:output-dir "/Users/kanishkkumar/Downloads/PhotoDump/Gallery"}]
    (build-gallery
     (build-gallery-struct "/Users/kanishkkumar/Downloads/PhotoDump" settings) settings))

  (search-files "/Users/kanishkkumar/Documents/AdonaiImages" "{*.jpg}"))





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



(comment
  (def example-gallery {:settings {:copy true}
                        :gallery-structure {"2021(year)" {"08(month)" {"31(day)" [{:original-path "string" :created-at "MM/DD/YYYY" :exif-data "exif Data Object"}]}}}})

  (defn photo-gallery-destination [photo]

    ["2021" "08" "31"])

  (defn add-photo-to-gallery [photo gallery]
    (let [dest (photo-gallery-destination photo)]
      (update-in gallery (into [:gallery-structure] dest) conj photo)))

  (add-photo-to-gallery {:created-at "123/123/123"} example-gallery))



(defn -main [& args]
  (println args))


#_Searchfiles







