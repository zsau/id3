(ns id3-test
	(:import [java.io ByteArrayInputStream ByteArrayOutputStream])
	(:require
		[clojure.test :refer :all]
		[id3 :refer :all]))

(defn write-id3-to-bytes [tag & opts]
	(let [buf (ByteArrayOutputStream.)]
		(apply write-id3 buf tag opts)
		(.toByteArray buf)))

(deftest test-read
	(let [t {:artist "Nobody", :title "Nothing"}]
		(testing "Reading tags"
			(is (apply = t (map #(with-mp3 [mp3 (format "test/resources/%s.mp3" %)] (:tag mp3))
				["v3latin1" "v3utf16" "v4latin1" "v4utf8" "v4utf16" "v4utf16be"]))))))

(deftest test-round-trip
	(let [t {:artist "Nobody", :title "Nothing"}]
		(testing "Round-tripping tags"
			(is (apply = t (map #(read-id3 (ByteArrayInputStream. (apply write-id3-to-bytes t %)))
				[[:version 4] [:version 3] [:encoding "ISO-8859-1"] [:encoding "UTF-16BE"] [:padding 0]]))))))
