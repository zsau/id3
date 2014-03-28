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
(def frame-id->keyword {
	"TIT1" :grouping
	"TIT2" :title
	"TIT3" :version
	"TPE1" :artist
	"TPE2" :album-artist
	"TPE3" :conductor
	"TPE4" :arranger
	"TEXT" :lyricist
	"TCOM" :composer
	"TENC" :encoded-by
	"TALB" :album
	"TRCK" :track-number
	"TPOS" :disc-number
	"TSRC" :isrc
	"TCOP" :copyright
	"TPUB" :organization
	"TOLY" :author
	"TBPM" :bpm
	"TYER" :date
	"TOAL" :original-album
	"TOPE" :original-artist
	"TMED" :media
	"TCMP" :compilation})

(def charset (b/enum :byte encoding-constants))

;FIXME: should be able to use b/string
(defn string [charset]
	(b/compile-codec (b/repeated :byte)
		(fn [[s & excess]]
			(when excess (error "This library doesn't support multiple text frame values in ID3v2.3"))
			(.getBytes s charset))
		#(vector (String. (byte-array %) charset))))

(defn body-size [{:keys [extended-header frames]}]
	(apply + (map #(+ 10 (frame-body-size %)) frames)))

(def frame-header
	(let [verify-id (partial verify-frame-id frame-ids)]
		(b/ordered-map
			:id (b/compile-codec (b/string latin1 :length 4) verify-id verify-id)
			:size :int-be
			:flags (b/bits [
				nil nil nil nil nil :grouped :encrypted :compressed
				nil nil nil nil nil :read-only :file-alter-preserve :tag-alter-preserve]))))

(defn frame-header->body [{:keys [id size flags] :as header}]
	(b/compile-codec
		(condp = (frame-type id)
			:padding (b/ordered-map :content (b/repeated (b/constant :byte 0)))
			:picture (picture-content size)
			:user-text (user-text-frame-content size charset string)
			:text (text-frame-content size charset string)
			:url (b/ordered-map :content (b/string latin1 :length size))
			(b/ordered-map :content (b/repeated :byte :length size)))
		#(dissoc % (keys header))
		#(merge % header)))

(defn frame-body->header [{:keys [id flags content] :as body}] {
	:id id
	:flags (set/intersection flags #{:read-only :file-alter-preserve :tag-alter-preserve})
	:size (frame-body-size body)})

(def frame (b/header frame-header frame-header->body frame-body->header))

(def extended-header
	(b/header (b/ordered-map :size :int-be, :flags (b/bits [nil nil nil nil nil nil nil :crc]))
		(fn [h] (apply b/ordered-map (concat
			[:padding :int-be]
			(when (:crc (:flags h)) [:crc :int-be]))))
		nil))

(defn header->body [{:keys [flags size] :as header}]
	(b/compile-codec
		(apply b/ordered-map (concat
			(when (:extended-header flags) [:extended-header extended-header])
			[:frames (limit size (b/repeated frame))]))
		#(apply dissoc % (keys header))
		#(remove-padding (merge % header))))

(defmethod body-codec 3 [header] (header->body header))
(defmethod frame-keywords 3 [version] frame-id->keyword)
