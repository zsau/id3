(ns id3-test
	(:import [java.io ByteArrayInputStream ByteArrayOutputStream])
	(:require
		[clojure.test :refer :all]
		[id3 :refer :all]))

(defn write-tag-to-bytes [tag & opts]
	(let [buf (ByteArrayOutputStream.)]
		(apply write-tag buf tag opts)
		(.toByteArray buf)))

(deftest test-read
	(let [t {:artist ["Nobody"], :title ["Nothing"]}]
		(testing "Reading tags"
			(doseq [file ["v3latin1" "v3utf16" "v4latin1" "v4utf8" "v4utf16" "v4utf16be"]]
				(is (= t (with-mp3 [mp3 (format "test/resources/%s.mp3" file)] (:tag mp3))))))))

(deftest test-round-trip
	(let [t {:artist ["Nobody"], :title ["Nothing"]}]
		(testing "Round-tripping tags"
			(doseq [opts [[:version 4] [:version 3] [:encoding "ISO-8859-1"] [:encoding "UTF-16BE"] [:padding 0]]]
				(is (= t (read-tag (ByteArrayInputStream. (apply write-tag-to-bytes t opts)))))))))
