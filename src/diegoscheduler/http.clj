(ns diegoscheduler.http
  (:require [clojure.core.async :refer [put!]]
            [clojure.tools.logging :as log]
            [clojure.string :refer [split join]]
            [cheshire.core :as cheshire]
            [org.httpkit.client :as httpkit]))

(def ^:private basic-auth-pattern
  #"//(.*):(.*)@")

(defn- basic-auth
  [url]
  (->> url
       (re-seq basic-auth-pattern)
       first
       rest))

(defn- strip-basic-auth
  [url]
  (let [parts (split url basic-auth-pattern)]
    (if (= 1 (count parts))
      url
      (join "//" ((juxt first last) parts)))))

(def ^:private success?
  (set (range 200 300)))

(defn- req
  ([method url response]
   (req method url response {}))
  ([method url response opts]
   (let [stripped-url (strip-basic-auth url)]
     (httpkit/request (merge {:url stripped-url
                              :method method
                              :as :text
                              :basic-auth (basic-auth url)}
                             opts)
                      (fn [{body :body
                            status :status}]
                        (if (and (success? status) (not (empty? body)))
                          (put! response (cheshire/parse-string body true))
                          (log/error method stripped-url
                                     "gave a" status
                                     "with" body)))))))

(defn POST [url data response]
  (req :post url response {:body (cheshire/generate-string data)}))

(defn GET [url response]
  (req :get url response))

(defn DELETE [url response]
  (req :delete url response))
