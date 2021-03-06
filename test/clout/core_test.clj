(ns clout.core-test
  (:import [clojure.lang ExceptionInfo]
           [java.util.regex PatternSyntaxException])
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer [request]]
            [clout.core :refer :all]))

(deftest fixed-path
  (are [path] (route-matches path path)
    "/"
    "/foo"
    "/foo/bar"
    "/foo/bar.html"))

(deftest keyword-paths
  (are [path uri params] (= (route-matches path uri) params)
    "/:x"       "/foo"     {:x "foo"}
    "/foo/:x"   "/foo/bar" {:x "bar"}
    "/a/b/:c"   "/a/b/c"   {:c "c"}
    "/:a/b/:c"  "/a/b/c"   {:a "a", :c "c"}))

(deftest keywords-match-extensions
  (are [path uri params] (= (route-matches path uri) params)
    "/foo.:ext" "/foo.txt" {:ext "txt"}
    "/:x.:y"    "/foo.txt" {:x "foo", :y "txt"}))

(deftest hyphen-keywords
  (are [path uri params] (= (route-matches path uri) params)
    "/:foo-bar" "/baz" {:foo-bar "baz"}
    "/:foo-"    "/baz" {:foo- "baz"}))

(deftest underscore-keywords
  (are [path uri params] (= (route-matches path uri) params)
    "/:foo_bar" "/baz" {:foo_bar "baz"}
    "/:_foo"    "/baz" {:_foo "baz"}))

(deftest urlencoded-keywords
  (are [path uri params] (= (route-matches path  uri) params)
    "/:x" "/foo%20bar" {:x "foo%20bar"}
    "/:x" "/foo+bar"   {:x "foo+bar"}
    "/:x" "/foo%5Cbar" {:x "foo%5Cbar"}))

(deftest same-keyword-many-times
  (are [path uri params] (= (route-matches path uri) params)
    "/:x/:x/:x" "/a/b/c" {:x ["a" "b" "c"]}
    "/:x/b/:x"  "/a/b/c" {:x ["a" "c"]}))

(deftest non-ascii-keywords
  (are [path uri params] (= (route-matches path uri) params)
    "/:äñßOÔ"   "/abc"     {:äñßOÔ "abc"}
    "/:ÁäñßOÔ"  "/abc"     {:ÁäñßOÔ "abc"}
    "/:ä/:ش"    "/foo/bar" {:ä "foo" :ش "bar"}
    "/:ä/:ä"    "/foo/bar" {:ä ["foo" "bar"]}
    "/:Ä-ü"     "/baz"     {:Ä-ü "baz"}
    "/:Ä_ü"     "/baz"     {:Ä_ü "baz"}))

(deftest wildcard-paths
  (are [path uri params] (= (route-matches path uri) params)
    "/*"     "/foo"         {:* "foo"}
    "/*"     "/foo.txt"     {:* "foo.txt"}
    "/*"     "/foo/bar"     {:* "foo/bar"}
    "/foo/*" "/foo/bar/baz" {:* "bar/baz"}
    "/a/*/d" "/a/b/c/d"     {:* "b/c"}))

(deftest escaped-chars
  (are [path uri params] (= (route-matches path  uri) params)
    "/\\:foo" "/foo"  nil
    "/\\:foo" "/:foo" {}))

(deftest inline-regexes
  (are [path uri params] (= (route-matches path uri) params)
    "/:x{\\d+}"   "/foo" nil
    "/:x{\\d+}"   "/10"  {:x "10"}
    "/:x{\\d{2}}" "/2"   nil
    "/:x{\\d{2}}" "/20"  {:x "20"}
    "/:x{\\d}/b"  "/3/b" {:x "3"}
    "/:x{\\d}/b"  "/a/b" nil
    "/a/:x{\\d}"  "/a/4" {:x "4"}
    "/a/:x{\\d}"  "/a/b" nil))

(deftest compiled-routes
  (is (= (route-matches (route-compile "/foo/:id") "/foo/bar")
         {:id "bar"})))

(deftest unmatched-paths
  (is (nil? (route-matches "/foo" "/bar"))))

(deftest custom-matches
  (let [route (route-compile "/foo/:bar" {:bar #"\d+"})]
    (is (not (route-matches route "/foo/bar")))
    (is (not (route-matches route "/foo/1x")))
    (is (route-matches route "/foo/10"))))

(deftest unused-regex-keys
  (is (thrown? AssertionError (route-compile "/:foo" {:foa #"\d+"})))
  (is (thrown? AssertionError (route-compile "/:foo" {:foo #"\d+" :bar #".*"}))))

(deftest invalid-inline-patterns
  (is (thrown? ExceptionInfo (route-compile "/:foo{")))
  (is (thrown? ExceptionInfo (route-compile "/:foo{\\d{2}")))
  (is (thrown? PatternSyntaxException (route-compile "/:foo{[a-z}"))))
