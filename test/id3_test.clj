(ns id3-test
	(:import [java.io ByteArrayInputStream ByteArrayOutputStream])
	(:require
		[clojure.spec.test.alpha :as stest]
		[clojure.test :refer :all]
		[id3 :refer :all]
		[id3.common :refer :all]
		[id3.spec]
		[medley.core :as m]))

(stest/instrument)

(defn write-tag-to-bytes [tag opts]
	(let [buf (ByteArrayOutputStream.)]
		(m/mapply write-tag buf tag opts)
		(.toByteArray buf)))

(defn encoding-short-name [enc]
	(condp = enc
		latin1 "latin1"
		utf8 "utf8"
		utf16 "utf16"
		utf16be "utf16-be"))

(defn encoded-string-size [enc s]
	(condp = enc
		latin1 (inc (count s)) ; extra byte for the encoding marker
		utf8 (inc (count s))
		utf16 (+ 3 (* 2 (count s))) ; 2 bytes per char, plus encoding marker and extra 2 bytes for the BOM
		utf16be (inc (* 2 (count s)))))

(defn tag-size [enc text-frame-values]
	;; our test files have 256 bytes of padding,
	;; plus 10 bytes for each frame header
	(apply + 256 (map #(+ 10 (encoded-string-size enc %)) text-frame-values)))

(deftest formats
	(let [[artist title] ["Nobody" "Nothing"]]
		(doseq [
				ver [3 4]
				enc (encodings-for-version ver)
				[fmt tag] {
					:simple
						#:id3.frame.name{:artist [artist], :title [title]}
					:normal
						{"TPE1" [artist], "TIT2" [title]}
					:full
						#:id3{:magic-number "ID3", :version ver, :revision 0, :flags #{}, :size (tag-size enc [artist title]), :frames [
							#:id3.frame{:id "TIT2", :flags #{}, :encoding enc, :content [title], :size (encoded-string-size enc title)}
							#:id3.frame{:id "TPE1", :flags #{}, :encoding enc, :content [artist], :size (encoded-string-size enc artist)}]}}]
			(testing (format "version: %s, encoding: %s" ver enc)
				(let [file (format "test/resources/basic-v2.%d-%s.mp3" ver (encoding-short-name enc))]
					(is (= tag (with-mp3 [mp3 file :format fmt] (:id3/tag mp3)))))))))

(deftest non-latin
	(let [tag #:id3.frame.name{:artist ["Mötley Crüe"], :title ["白い夏と緑の自転車 赤い髪と黒いギター"]}]
		(doseq [ver [3 4], enc (remove #{latin1} (encodings-for-version ver))]
			(let [file (format "test/resources/non-latin-v2.%s-%s.mp3" ver (encoding-short-name enc))]
				(testing (str "Reading " file)
					(is (= tag (with-mp3 [mp3 file] (:id3/tag mp3)))))))))

(deftest round-trip
	(let [tag #:id3.frame.name{:artist ["Nobody"], :title ["Nothing"]}]
		(doseq [opts [[:version 4] [:version 3] [:encoding latin1] [:encoding utf16] [:padding 0]]]
			(testing (str "Round-tripping with " opts)
				(is (= tag (read-tag (ByteArrayInputStream. (apply write-tag-to-bytes tag opts)))))))))
