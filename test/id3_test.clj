(ns id3-test
	(:import [java.io ByteArrayInputStream ByteArrayOutputStream])
	(:require
		[clojure.test :refer :all]
		[id3 :refer :all]))

(defn write-tag-to-bytes [tag & opts]
	(let [buf (ByteArrayOutputStream.)]
		(apply write-tag buf tag opts)
		(.toByteArray buf)))

(deftest test-formats
	(let [formats {
			:simple {:artist ["Nobody"], :title ["Nothing"]}
			:normal {"TPE1" ["Nobody"], "TIT2" ["Nothing"]}
			:full {:magic-number "ID3", :version {:major 4, :minor 0}, :flags #{}, :size 1059, :frames [
				{:encoding "UTF-8", :content ["Nobody"], :size 7, :id "TPE1", :flags #{}}
				{:encoding "UTF-8", :content ["Nothing"], :size 8, :id "TIT2", :flags #{}}]}}]
		(testing "Reading tags"
			(doseq [[fmt tag] formats]
				(is (= tag (with-mp3 [mp3 "test/resources/v4utf8.mp3" :format fmt] (:tag mp3))))))))

(deftest test-encodings
	(let [t {:artist ["Nobody"], :title ["Nothing"]}]
		(testing "Reading tags"
			(doseq [file ["v3latin1" "v3utf16" "v4latin1" "v4utf8" "v4utf16" "v4utf16be"]]
				(is (= t (with-mp3 [mp3 (format "test/resources/%s.mp3" file)] (:tag mp3))))))))

(deftest test-round-trip
	(let [t {:artist ["Nobody"], :title ["Nothing"]}]
		(testing "Round-tripping tags"
			(doseq [opts [[:version 4] [:version 3] [:encoding "ISO-8859-1"] [:encoding "UTF-16BE"] [:padding 0]]]
				(is (= t (read-tag (ByteArrayInputStream. (apply write-tag-to-bytes t opts)))))))))
