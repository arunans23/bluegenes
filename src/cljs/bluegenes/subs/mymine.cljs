(ns bluegenes.subs.mymine
  (:require [re-frame.core :refer [reg-sub subscribe]]
            [reagent.core :as r]
            [clojure.string :refer [split]]))

; Thanks!
; https://groups.google.com/forum/#!topic/clojure/VVVa3TS15pU
(def asc compare)
(def desc #(compare %2 %1))
(defn compare-by [& key-cmp-pairs]
  (fn [x y]
    (loop [[k cmp & more] key-cmp-pairs]
      (let [result (cmp (k x) (k y))]
        (if (and (zero? result) more)
          (recur more)
          result)))))

(defn branch?
  "Branch? fn for a tree-seq. True if this branch is open and has children."
  [m] (and (:open m) (contains? m :children)))

(defn children
  "Children fn for a tree-seq. Append the child's key to the collection of keys (trail)
  so we know the nested location of the child"
  [sort-info m]
  (map
    (fn [[key child]]
      (assoc child
        :trail (vec (conj (:trail m) :children key))))
    (sort
      (compare-by
        (comp (:key sort-info) second)
        (if (:asc? sort-info) asc desc))
      (:children m))))


(defn flatten-tree
  "Flatten the nested tree structure of MyMine data into a list (depth first)"
  [m sort-info]
  (tree-seq branch? (partial children sort-info) m))

(reg-sub
  ::sort-by
  (fn [db]
    (get-in db [:mymine :sort-by])))

(reg-sub
  ::my-tree
  (fn [db]
    (get-in db [:mymine :tree])))

(reg-sub
  ::selected
  (fn [db]
    (get-in db [:mymine :selected])))

; Fill the [:root :public] folder with public lists
(reg-sub
  ::with-public
  (fn [] [(subscribe [::my-tree])
          (subscribe [:lists/filtered-lists])])
  (fn [[my-tree filtered-lists]]
    (assoc-in my-tree [:root :children :public :children]
              (into {} (map
                         (fn [l]
                           {(:id l) {:type :list
                                     :label (:title l)
                                     :id (:id l)
                                     :size (:size l)
                                     :read-only? true
                                     :trail [0]}})
                         filtered-lists)))))

; MyMine data is a nested tree structure, however it's easier to display and sort when it's
; flattened into a list. Applying 'rest' removes the root folder
(reg-sub
  ::as-list
  (fn [] [(subscribe [::with-public]) (subscribe [::sort-by])])
  (fn [[with-public sort-info]]
    (-> with-public :root (assoc :fid :root :trail [:root]) (flatten-tree sort-info) rest)))

