(ns offline-gallery-generator.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [offline-gallery-generator.generator :as generator]))
"--copy --move --link #what to do with source photos

--clean #remove any existing gallery artifacts
--scrub-exif  #remove any unessisary exif data from originals. Does nothing when --link

--only=*.jpg
--exif-filter=SUB-STRING #only includes files where exif contains SUB-STRING.
--exclude=PATTERN       #exclude files matching PATTERN, ex *.png
--include=PATTERN       #don't exclude files matching PATTERN

--thumb=600 #create multiple resized photos
--thumb=1024
--max-px=3000 #resize any photos over this size. Does nothing when --link

--split=4.7GB #split gallery into chunks for DVD burn"
(def cli-options
  ;; [short-option line-option description ....]
  [[nil "--copy" "Copy source files to gallery folder"
    :default false]
   [nil "--move" "Move source files to gallery folder"
    :default false]
   [nil "--link" "Leaves the files in the original location"
    :default true]
   [nil "--only PATTERN" "Pick file for Pattern that matches"
    :multi true
    :default []
    :update-fn conj]

   ["-v" nil "Verbosity level"
    :id :verbosity
    :default 0
    :update-fn inc] ; Prior to 0.4.1, you would have to use:
                   ;; :assoc-fn (fn [m k _] (update-in m [k] inc))
   ;; A boolean option defaulting to nil
   ["-h" "--help"]])
(defn usage [options-summary]
  (->> ["This is my program. There are many like it, but this one is mine."
        ""
        "Usage: program-name [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  start    Start a new server"
        "  stop     Stop an existing server"
        "  status   Print a server's status"
        ""
        "Please refer to the manual page for more information."]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with an error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}
      (not= 1
            (count (filter true?
                           [(:copy options) (:link options) (:move options)])))
      {:exit-message (error-msg ["Only provide one --copy/--link/--move"])}
      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}
      ;; custom validation on arguments
      #_(and (= 1 (count arguments))
             (#{"start" "stop" "status"} (first arguments)))
      #_{:action (first arguments) :options options}
      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary)})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [action options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (clojure.pprint/pprint (parse-opts args cli-options))
      #_(case action
          "start"  (server/start! options)
          "stop"   (server/stop! options)
          "status" (server/status! options)))))