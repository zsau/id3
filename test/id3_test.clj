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

(defn normalize-full-tag [tag]
	(-> tag
		(dissoc :id3/size)
		(update :id3/frames (fn [frames]
			(mapv #(dissoc % :id3.frame/size :id3.frame/encoding) frames)))))

(deftest round-trip
	(let [
			full-tag #:id3{
				:magic-number "ID3"
				:version 4
				:revision 0
				:flags #{}
				:size 35
				:frames [
					#:id3.frame{
						:encoding "ISO-8859-1"
						:content ["Nothing"]
						:size 8
						:id "TIT2"
						:flags #{}}
					#:id3.frame{
						:encoding "ISO-8859-1"
						:content ["Nobody"]
						:size 7
						:id "TPE1"
						:flags #{}}]}
			normalize (fn [tag]
				(cond-> tag
					(= :full (id3/tag-format tag)) normalize-full-tag))]
		(doseq [ver [nil 3 4], enc [nil latin1 utf16], pad [nil 0 123], fmt [:simple :normal :full]]
			(let [
					tag (id3/downconvert-tag full-tag fmt)
					opts (m/remove-vals nil? {:version ver, :encoding enc, :padding pad})]
				(testing (format "Round-tripping with format: %s, opts: %s" fmt opts)
					(is (= (normalize tag)
						(-> tag
							(write-tag-to-bytes opts)
							ByteArrayInputStream.
							(read-tag :format fmt)
							normalize))))))))
