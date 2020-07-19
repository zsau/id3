(ns id3.v4
"ID3v2.4 codecs.
- https://id3.org/id3v2.4.0-structure
- https://id3.org/id3v2.4.0-frames"
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

;; mostly borrowed from quodlibet
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
	:disc-subtitle "TSST"
	:author "TOLY"
	:mood "TMOO"
	:bpm "TBPM"
	:date "TDRC"
	:original-date "TDOR"
	:original-album "TOAL"
	:original-artist "TOPE"
	:artist-sort "TSOP"
	:album-sort "TSOA"
	:title-sort "TSOT"
	:album-artist-sort "TSO2"
	:composer-sort "TSOC"
	:media "TMED"
	:compilation "TCMP"
	:picture "APIC"
	:custom "TXXX"})

(def frame-id->name (set/map-invert frame-name->id))
(def frame-names (set (keys frame-name->id)))

(def charset (b/enum :byte encoding-constants))

(def frame-header
	(let [verify-id (partial verify-frame-id frame-ids)]
		(b/ordered-map
			:id3.frame/id (b/compile-codec (b/string latin1 :length 4) verify-id verify-id)
			:id3.frame/size synchsafe-int
			:id3.frame/flags (b/bits [
				:id3.frame.flag/data-length :id3.frame.flag/unsynchronized :id3.frame.flag/encrypted :id3.frame.flag/compressed nil nil :id3.frame.flag/grouped nil
				nil nil nil nil :id3.frame.flag/read-only :id3.frame.flag/file-alter-preserve :id3.frame.flag/tag-alter-preserve nil]))))

(defn frame-header->body [{:id3.frame/keys [id size flags] :as header}]
	(b/compile-codec
		(case (frame-type id)
			:id3.frame.type/padding (b/ordered-map :id3.frame/content (b/repeated (b/constant :byte 0)))
			:id3.frame.type/picture (picture-content size charset)
			:id3.frame.type/user-text (user-text-frame-content size charset repeated-string)
			:id3.frame.type/text (text-frame-content size charset repeated-string)
			:id3.frame.type/user-url (user-url-frame-content size charset null-terminated-string)
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
	(b/header synchsafe-int
		(fn [size]
			(b/compile-codec
				(limit size (b/ordered-map
					:id3.extended-header/flag-bytes (b/constant :byte 1)
					:id3.extended-header/flags (b/bits [nil nil nil nil :id3.extended-header.flag/restrictions :id3.extended-header.flag/crc :id3.extended-header.flag/update nil])
					:id3.extended-header/content byte-blob))
				#(dissoc % :id3/size)
				#(assoc % :id3/size size)))
		nil))

(defn header->body [{:id3/keys [flags size] :as header}]
	(b/compile-codec
		(concat
			[(limit size (apply b/ordered-map (concat
				(when (:id3/extended-header flags) [:id3/extended-header extended-header])
				[:id3/frames (b/repeated frame)])))]
			(when (:id3/footer flags) [(limit 10 byte-blob)]))
		#(vector (apply dissoc % (keys header)))
		(fn [[body footer]]
			(remove-padding
				(merge header
					(if-not footer body
						(assoc body :id3/footer footer)))))))

(defmethod body-codec 4 [header] (header->body header))
(defmethod frame-names-for-version 4 [_] frame-name->id)
(defmethod frame-ids-for-version 4 [_] frame-ids)
(defmethod encodings-for-version 4 [_] (keys encoding-constants))
