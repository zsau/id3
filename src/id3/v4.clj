(ns id3.v4
	(:require
		[id3.common :refer :all]
		[clojure.set :as set]
		[clojure.string :as str]
		[org.clojars.smee.binary.core :as b]))

(def encoding-constants {
	latin1 0
	utf16 1
	utf16be 2
	utf8 3})

(def frame-ids
	(set (cons "TCMP" ; non-standard iTunes frame
		(split "AENC APIC ASPI COMM COMR ENCR EQU2 ETCO GEOB GRID LINK MCDI MLLT OWNE PRIV PCNT POPM POSS RBUF RVA2 RVRB SEEK SIGN SYLT SYTC TALB TBPM TCOM TCON TCOP TDEN TDLY TDOR TDRC TDRL TDTG TENC TEXT TFLT TIPL TIT1 TIT2 TIT3 TKEY TLAN TLEN TMCL TMED TMOO TOAL TOFN TOLY TOPE TOWN TPE1 TPE2 TPE3 TPE4 TPOS TPRO TPUB TRCK TRSN TRSO TSOA TSOP TSOT TSRC TSSE TSST TXXX UFID USER USLT WCOM WCOP WOAF WOAR WOAS WORS WPAY WPUB WXXX"))))

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
	"TSST" :disc-subtitle
	"TOLY" :author
	"TMOO" :mood
	"TBPM" :bpm
	"TDRC" :date
	"TDOR" :original-date
	"TOAL" :original-album
	"TOPE" :original-artist
	"TSOP" :artist-sort
	"TSOA" :album-sort
	"TSOT" :title-sort
	"TSO2" :album-artist-sort
	"TSOC" :composer-sort
	"TMED" :media
	"TCMP" :compilation
	"APIC" :picture
	"TXXX" :custom})

(def charset (b/enum :byte encoding-constants))

(def frame-header
	(let [verify-id (partial verify-frame-id frame-ids)]
		(b/ordered-map
			:id (b/compile-codec (b/string latin1 :length 4) verify-id verify-id)
			:size synchsafe-int
			:flags (b/bits [
				:data-length :unsynchronized :encrypted :compressed nil nil :grouped nil
				nil nil nil nil :read-only :file-alter-preserve :tag-alter-preserve nil]))))

(defn frame-header->body [{:keys [id size flags] :as header}]
	(b/compile-codec
		(condp = (frame-type id)
			:padding (b/ordered-map :content (b/repeated (b/constant :byte 0)))
			:picture (picture-content size charset)
			:user-text (user-text-frame-content size charset repeated-string)
			:text (text-frame-content size charset repeated-string)
			:url (b/ordered-map :content (b/string latin1 :length size))
			(limit size (b/ordered-map :content byte-blob)))
		#(dissoc % (keys header))
		#(merge % header)))

(defn frame-body->header [{:keys [id flags content] :as body}] {
	:id id
	:flags (set/intersection flags #{:read-only :file-alter-preserve :tag-alter-preserve})
	:size (frame-body-size body)})

(def frame (b/header frame-header frame-header->body frame-body->header))

(def extended-header
	(b/header synchsafe-int
		(fn [size]
			(b/compile-codec
				(limit size (b/ordered-map
					:flag-bytes (b/constant :byte 1)
					:flags (b/bits [nil nil nil nil :restrictions :crc :update nil])
					:content byte-blob))
				#(dissoc % :size)
				#(assoc % :size size)))
		nil))

(defn header->body [{:keys [flags size] :as header}]
	(b/compile-codec
		(concat
			[(limit size (apply b/ordered-map (concat
				(when (:extended-header flags) [:extended-header extended-header])
				[:frames (b/repeated frame)])))]
			(when (:footer flags) [(limit 10 byte-blob)]))
		#(vector (apply dissoc % (keys header)))
		(fn [[body footer]]
			(remove-padding
				(merge header
					(if-not footer body
						(assoc body :footer footer)))))))

(defmethod body-codec 4 [header] (header->body header))
(defmethod frame-keywords 4 [version] frame-id->keyword)
