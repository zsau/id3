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
			:id3.format/simple #:id3.frame.name{:artist ["Nobody"], :title ["Nothing"]}
			:id3.format/normal {"TPE1" ["Nobody"], "TIT2" ["Nothing"]}
			:id3.format/full #:id3{:magic-number "ID3", :version 4, :revision 0, :flags #{}, :size 1059, :frames [
				#:id3.frame{:encoding "UTF-8", :content ["Nobody"], :size 7, :id "TPE1", :flags #{}}
				#:id3.frame{:encoding "UTF-8", :content ["Nothing"], :size 8, :id "TIT2", :flags #{}}]}}]
		(testing "Reading tags"
			(doseq [[fmt tag] formats]
				(is (= tag (with-mp3 [mp3 "test/resources/v4utf8.mp3" :id3/format fmt] (:id3/tag mp3))))))))

(deftest test-encodings
	(let [t #:id3.frame.name{:artist ["Nobody"], :title ["Nothing"]}]
		(testing "Reading tags"
			(doseq [file ["v3latin1" "v3utf16" "v4latin1" "v4utf8" "v4utf16" "v4utf16be"]]
				(is (= t (with-mp3 [mp3 (format "test/resources/%s.mp3" file)] (:id3/tag mp3))))))))

(deftest test-round-trip
	(let [t #:id3.frame.name{:artist ["Nobody"], :title ["Nothing"]}]
		(testing "Round-tripping tags"
			(doseq [opts [[:id3/version 4] [:id3/version 3] [:id3/encoding "ISO-8859-1"] [:id3/encoding "UTF-16BE"] [:id3/padding 0]]]
				(is (= t (read-tag (ByteArrayInputStream. (apply write-tag-to-bytes t opts)))))))))
