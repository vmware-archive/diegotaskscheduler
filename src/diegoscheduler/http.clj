(ns diegoscheduler.http
  (:require [clojure.core.async :refer [put! close!]]
            [clojure.tools.logging :as log]
            [cheshire.core :as cheshire]
            [clj-http.client :as client]
            [org.httpkit.client :as httpkit]
            [slingshot.slingshot :refer [try+]]))

(defn- wrap [f]
  (try+
   (f)
   (catch java.net.UnknownHostException _
     ["Unknown Host" nil])
   (catch java.net.ConnectException _
     ["Connection Refused" nil])
   (catch [:status 400] {:keys [body]}
     ["400" nil])
   (catch [:status 404] {:keys [body]}
     ["Not Found" nil])))

(defn- basic-auth
  [url]
  (->> url
       (re-seq #"//(.*):(.*)@")
       first
       rest))

(defn POST [url data]
  (wrap (fn [] (let [result ((client/post url {:body (client/json-encode data) :as :json})
                            :body)]
                [nil result]))))

(defn- req
  [method url response]
  (httpkit/request {:url url
                    :method method
                    :as :text
                    :basic-auth (basic-auth url)}
                   (fn [{body :body
                         status :status}]
                     (if (= 200 status)
                       (put! response (cheshire/parse-string body true))
                       (log/error method
                                  url
                                  "gave a"
                                  status)))))

(defn GET [url response]
  (req :get url response))

(defn DELETE [url response]
  (req :delete url response))
