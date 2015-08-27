(ns diegoscheduler.http
  (:require [clojure.core.async :refer [put!]]
            [clojure.tools.logging :as log]
            [cheshire.core :as cheshire]
            [org.httpkit.client :as httpkit]))

(defn- basic-auth
  [url]
  (->> url
       (re-seq #"//(.*):(.*)@")
       first
       rest))

(def ^:private success?
  (set (range 200 300)))

(defn- req
  ([method url response]
   (req method url response {}))
  ([method url response opts]
   (httpkit/request (merge {:url url
                            :method method
                            :as :text
                            :basic-auth (basic-auth url)}
                           opts)
                    (fn [{body :body
                          status :status}]
                      (if (and (success? status) (not (empty? body)))
                        (put! response (cheshire/parse-string body true))
                        (log/error method url
                                   "gave a" status
                                   "with" body))))))

(defn POST [url data response]
  (req :post url response {:body (cheshire/generate-string data)}))

(defn GET [url response]
  (req :get url response))

(defn DELETE [url response]
  (req :delete url response))
