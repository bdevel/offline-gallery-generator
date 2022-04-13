(ns offline-gallery-generator.generator
  (:require [me.raynes.fs :as fs]
            [exif-processor.core :as ec]
            [image-resizer.format :as format]
            [image-resizer.core :refer :all]
            [image-resizer.fs :as resizer-fs]
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


(defn generate-hrefs [links]
  [:ul
   (for [x links]
     [:li [:a {:href (:file-path x)} [:img {:src (:thumb-path x)}]]])])

(defn make-thumbnails [image-path settings]
  (let [[w h] [250 250]
        thumbnail-path (str (:output-dir settings)
                            "/thumbnail/")
        destination-path (str thumbnail-path (fs/name image-path)
                              (fs/extension image-path))
        new-image-path (resizer-fs/new-filename destination-path [w h])]
    (fs/mkdirs thumbnail-path)
    (when (not (fs/exists? new-image-path))
      (format/as-file
       (resize (io/file image-path) w h)
       new-image-path
       :verbatim))
    new-image-path))



(defn build-gallery-page
  "Make index html for our gallery"
  [files settings]
  ;; spit the html in the index
  ;; create a dir named THUMBNAILS with thumbnails
  ;;(println (:output-dir settings) "\n")
  (let [thumb-files (map (fn [file]
                           (assoc file :thumb-path (make-thumbnails (:file-path file) settings)))
                         files)
        ;; Problem 1
        img-links  (generate-hrefs thumb-files)
        html (hiccup-page/html5 {:lang "en"} [:body img-links])
        index-path (str (get settings :output-dir) "/index.html")]
    (spit index-path html)
    {:photos thumb-files
     :index-path index-path
     :title (last (fs/split (get settings :output-dir)))
     :sub-pages nil}))


(defn collect-sub-pages
  "Walks :sub-pages to #nlevel deep by depth first
   And return the flatten list of all :sub-pages"
  [page]
  (loop [sub-pages (get page :sub-pages)
         all-subs (get page :sub-pages)]
    (if sub-pages
      (recur (next sub-pages)
             (concat all-subs (collect-sub-pages (first sub-pages))))
      all-subs)))


(defn sample-directory-photos
  "Provided a gallery page {:sub-pages ({:photos})}
   Take photos from sub pages picking one from each and redoing untill qty is overceeded
   Add :page to the each photo
   Sort photos by same day
   This returns a list of {:created-at :file-path :thumb-path :page {:index-path, :title}}"
  [page qty]

  (let [sub-pages (collect-sub-pages page)
        photo-pages (filter  #(not-empty (:photos %)) sub-pages)
        photo-list (loop [pages photo-pages
                          items (list)]
                     (if (or (>= (count items) qty)
                             (nil? pages))
                       items
                       (let [cur-page (first pages)
                             photo (first (:photos cur-page))
                             new-photo (assoc photo
                                              :page (select-keys cur-page [:index-path :title]))
                             newcur-page (update cur-page :photos next)
                             new-items (if photo
                                         (conj items new-photo)
                                         items)
                             new-pages (if (:photos newcur-page)
                                         (concat (next pages) [newcur-page])
                                         (next pages))]
                         (recur new-pages
                                new-items))))]
    (sort-by #(get-in % [:page :index-path]) photo-list)))

(defn build-directory-page
  "Make index html for directories"
  [sub-pages settings]
  (let [page-title (last (fs/split (get settings :output-dir)))
        sub-dir-links [:ul
                       (map (fn [p]
                              [:li [:a {:href (:index-path p)} (:title p)]
                               [:br]
                               (map (fn [f]
                                      [:a
                                       {:href (get-in f [:page :index-path])
                                        :title (get-in f [:page :title])}
                                       [:img {:src (:thumb-path f)}]])
                                    (sample-directory-photos p 10))])
                            sub-pages)]
        html (hiccup-page/html5 {:lang "en"}
                                [:head [:style {:type "text/css"} "h1 {color: green}"]]
                                [:body
                                 [:h1 page-title]
                                 sub-dir-links])
        index-path (str (get settings :output-dir) "/index.html")]
    (spit index-path html)
    {:photos nil
     :index-path index-path
     :title page-title
     :sub-pages nil}))

(defn build-walking
  "Walk through"
  [struct settings]
  (let [cur-dir (get settings :output-dir (str fs/*cwd* "/Gallery"))]
    (if (map? struct)
      (let [sub-pages (doall
                       (map
                        (fn [k]
                          (fs/mkdirs (str cur-dir "/" k))
                          (build-walking (get struct k)
                                         (assoc settings :output-dir (str cur-dir "/" k))))
                        (keys struct)))
            dir-page (build-directory-page sub-pages settings)]
        (assoc dir-page :sub-pages sub-pages))


      (build-gallery-page struct (assoc settings :index-location (str cur-dir "/index.html"))))))

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


  (def example-gallery
    (let [settings {:output-dir "/Users/kanishkkumar/Downloads/PhotoDump/Gallery"}]
      (build-gallery
       (build-gallery-struct "/Users/kanishkkumar/Downloads/PhotoDump/2016" settings) settings)))

  (sample-directory-photos example-gallery 10)
  (count (collect-sub-pages example-gallery))
  (let [settings {:output-dir "/Users/kanishkkumar/Downloads/PhotoDump/Gallery"}]
    (build-gallery-struct "/Users/kanishkkumar/Downloads/PhotoDump/2016" settings))





  (build-gallery-struct "/Users/kanishkkumar/Downloads/PhotoDump" {:output-dir "/Users/kanishkkumar/Downloads/PhotoDump/Gallery"})
  (search-files "/Users/kanishkkumar/Documents/AdonaiImages" "{*.jpg}"))


#_(fnabs [tree from-path to-path])




(comment
 ;; this from the old  build-gallery-page  function
  ;;
  (let [thumb-files (map (fn [file]
                           (assoc file :thumb-path (make-thumbnails (:file-path file) settings)))
                         files)
        ;; Problem 1
        img-links  (generate-hrefs thumb-files)
        html (hiccup-page/html5 {:lang "en"} [:body img-links])
        index-path (str (get settings :output-dir) "/index.html")]
    (spit index-path html)))
#_Searchfiles