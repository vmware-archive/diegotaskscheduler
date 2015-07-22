(ns diegoscheduler.http
  (:require [clj-http.client :as client]
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

(defn POST [url data]
  (wrap (fn [] (let [result ((client/post url {:body (client/json-encode data) :as :json})
                            :body)]
                [nil result]))))

(defn GET [url]
  (wrap (fn []
          (let [result ((client/get url {:as :json})
                        :body)]
            [nil result]))))

(defn DELETE [url]
  (wrap (fn []
          (let [result ((client/delete url {:as :json})
                        :body)]
            [nil result]))))
