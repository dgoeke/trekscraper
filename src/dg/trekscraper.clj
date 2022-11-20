(ns dg.trekscraper
  (:require [net.cgrand.enlive-html :as html]
            [clojure.string :as str]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io])
  (:gen-class))

(def trek-ids ["tt0060028" "tt0092455" "tt0106145"
               "tt0112178" "tt0244365" "tt5171438"])

(defn series-url [id]
  (java.net.URL. (str "https://www.imdb.com/title/" id "/")))

(defn episodes-url [id season]
  (java.net.URL. (str "https://www.imdb.com/title/" id
                      "/episodes?season=" season)))

(def get-url (memoize (partial html/html-resource)))

(defn seasons [id]
  (let [data (-> id series-url get-url)]
    (->> (html/select data [:select#browse-episodes-season :option])
         (map (comp :value :attrs))
         (filter #(re-matches #"\d+" %))
         (map read-string))))

(defn series-name [page] (first (html/select page [:h3 :a html/content])))
(defn all-airdates [page] (map str/trim
                               (html/select page [:.airdate html/text-node])))
(defn all-epnums [page]
  (map (comp read-string :content :attrs)
       (html/select page [(html/attr= :itemprop "episodeNumber")])))
(defn all-images [page] (map (comp :src :attrs)
                             (html/select page [:div.list_item :img])))
(defn all-ids [page] (html/select page [:div.hover-over-image
                                        :div html/content]))
(defn all-names [page] (html/select page [:strong (html/attr= :itemprop "name")
                                          html/content]))
(defn all-descs [page] (map (comp str/trim html/text)
                            (html/select page [:div.item_description])))
(defn all-ratings [page]
  (map read-string (html/select page
                                [(html/right :span.ipl-rating-star__total-votes)
                                 html/text-node])))

(defn episodes [id season]
  (let [data   (get-url (episodes-url id season))
        series (series-name data)]
    (map (fn [id img epnum rating airdate name desc]
           {:series  series  :id    id    :img         img
            :season  season  :epnum epnum :rating      rating
            :airdate airdate :name  name  :description desc})
         (all-ids data)
         (all-images data)
         (all-epnums data)
         (all-ratings data)
         (all-airdates data)
         (all-names data)
         (all-descs data))))

(defn write-csv [path row-data]
  (let [columns [:series :id :season :epnum :rating
                 :name :airdate :description :img]
        headers (map name columns)
        rows    (mapv #(mapv % columns) row-data)]
    (with-open [file (io/writer path)]
      (csv/write-csv file (cons headers rows)))))

(write-csv "all-trek-episodes.csv"
           (mapcat identity
                   (for [id     trek-ids
                         season (sort (seasons id))]
                     (episodes id season))))
