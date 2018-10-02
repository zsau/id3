(ns id3.v3
	(:require
		[id3.common :refer :all]
		[clojure.set :as set]
		[org.clojars.smee.binary.core :as b]))

(def encoding-constants {
	latin1 0
	utf16 1})

(def frame-ids
	(set (cons "TCMP" ; non-standard iTunes frame
		(split "AENC APIC COMM COMR ENCR EQUA ETCO GEOB GRID IPLS LINK MCDI MLLT OWNE PRIV PCNT POPM POSS RBUF RVAD RVRB SYLT SYTC TALB TBPM TCOM TCON TCOP TDAT TDLY TENC TEXT TFLT TIME TIT1 TIT2 TIT3 TKEY TLAN TLEN TMED TOAL TOFN TOLY TOPE TORY TOWN TPE1 TPE2 TPE3 TPE4 TPOS TPUB TRCK TRDA TRSN TRSO TSIZ TSRC TSSE TYER TXXX UFID USER USLT WCOM WCOP WOAF WOAR WOAS WORS WPAY WPUB WXXX"))))

; mostly borrowed from quodlibet
(def frame-name->id #:id3.frame.name{
	:grouping "TIT1"
	:title "TIT2"
	:version "TIT3"
	:artist "TPE1"
	:album-artist "TPE2"
	:conductor "TPE3"
	:arranger "TPE4"
	:lyricist "TEXT"
	:composer "TCOM"
	:encoded-by "TENC"
	:album "TALB"
	:track-number "TRCK"
	:disc-number "TPOS"
	:isrc "TSRC"
	:copyright "TCOP"
	:organization "TPUB"
	:author "TOLY"
	:bpm "TBPM"
	:date "TYER"
	:original-date "TORY"
	:original-album "TOAL"
	:original-artist "TOPE"
	:media "TMED"
	:compilation "TCMP"
	:picture "APIC"
	:custom "TXXX"})

(def frame-id->name (set/map-invert frame-name->id))
(def frame-names (set (keys frame-name->id)))

(def charset (b/enum :byte encoding-constants))

;FIXME: should be able to use b/string
(defn string [charset]
	(b/compile-codec (b/repeated :byte)
		(fn [[s & excess]]
			(when excess (error "This library doesn't support multiple text frame values in ID3v2.3"))
			(.getBytes s charset))
		#(vector (String. (byte-array %) charset))))

(defn body-size [{:id3/keys [extended-header frames]}]
	(apply + (map #(+ 10 (frame-body-size %)) frames)))

(def frame-header
	(let [verify-id (partial verify-frame-id frame-ids)]
		(b/ordered-map
			:id3.frame/id (b/compile-codec (b/string latin1 :length 4) verify-id verify-id)
			:id3.frame/size :int-be
			:id3.frame/flags (b/bits [
				nil nil nil nil nil :id3.frame.flag/grouped :id3.frame.flag/encrypted :id3.frame.flag/compressed
				nil nil nil nil nil :id3.frame.flag/read-only :id3.frame.flag/file-alter-preserve :id3.frame.flag/tag-alter-preserve]))))

(defn frame-header->body [{:id3.frame/keys [id size flags] :as header}]
	(b/compile-codec
		(condp = (frame-type id)
			:id3.frame.type/padding (b/ordered-map :id3.frame/content (b/repeated (b/constant :byte 0)))
			:id3.frame.type/picture (picture-content size charset)
			:id3.frame.type/user-text (user-text-frame-content size charset string)
			:id3.frame.type/text (text-frame-content size charset string)
			:id3.frame.type/user-url (user-url-frame-content size charset string)
			:id3.frame.type/url (b/ordered-map :id3.frame/url (b/string latin1 :length size))
			:id3.frame.type/blob (limit size (b/ordered-map :id3.frame/bytes byte-blob)))
		#(dissoc % (keys header))
		#(merge % header)))

(defn frame-body->header [{:id3.frame/keys [id flags] :as body}] #:id3.frame{
	:id id
	:flags (set/intersection flags #{:id3.frame.flag/read-only :id3.frame.flag/file-alter-preserve :id3.frame.flag/tag-alter-preserve})
	:size (frame-body-size body)})

(def frame (b/header frame-header frame-header->body frame-body->header))

(def extended-header
	(b/header
		(b/ordered-map
			:id3.extended-header/size :int-be
			:id3.extended-header/flags (b/bits [nil nil nil nil nil nil nil :id3.extended-header.flag/crc]))
		(fn [h] (apply b/ordered-map (concat
			[:id3.extended-header/padding :int-be]
			(when (:id3.extended-header.flag/crc (:id3.extended-header/flags h)) [:id3.extended-header.flag/crc :int-be]))))
		nil))

(defn header->body [{:id3/keys [flags size] :as header}]
	(b/compile-codec
		(apply b/ordered-map (concat
			(when (:id3/extended-header flags) [:id3/extended-header extended-header])
			[:id3/frames (limit size (b/repeated frame))]))
		#(apply dissoc % (keys header))
		#(remove-padding (merge % header))))

(defmethod body-codec 3 [header] (header->body header))
(defmethod frame-names-for-version 3 [_] frame-name->id)
(defmethod frame-ids-for-version 3 [_] frame-ids)
(defmethod encodings-for-version 3 [_] (keys encoding-constants))
