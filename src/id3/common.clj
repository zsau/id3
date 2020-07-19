(ns id3.common
	(:require
		[clojure.string :as str]
		[org.clojars.smee.binary.core :as b]))

(def latin1 "ISO-8859-1")
(def utf8 "UTF-8")
(def utf16 "UTF-16")
(def utf16be "UTF-16BE")

;; We rely on the fact that all encodings supported by ID3v2 can be handled in uniform chunks for purposes of finding null characters:
;; 	- Even though UTF-8 is a multibyte encoding, the null byte can't occur in a multibyte char.
;; 	- Similarly, we can handle UTF-16 in 2-byte chunks despite the existence of surrogate pairs, because the 2-byte null char sequence can't occur as part of a surrogate pair.
(def chunk-size {
	latin1 1
	utf8 1
	utf16 2
	utf16be 2})

(def padding-id "\u0000\u0000\u0000\u0000")

(defn error [& args] (throw (Exception. (apply format args))))

(defn split [s] (str/split s #" "))

(defn frame-type [id]
	(cond
		(= padding-id id) :id3.frame.type/padding
		(= "APIC" id) :id3.frame.type/picture
		(= "TXXX" id) :id3.frame.type/user-text
		(= \T (first id)) :id3.frame.type/text
		(= "WXXX" id) :id3.frame.type/user-url
		(= \W (first id)) :id3.frame.type/url
		:else :id3.frame.type/blob))

(defn encoded-size [codec value]
	(let [out (java.io.ByteArrayOutputStream.)]
		(b/encode codec out value)
		(alength (.toByteArray out))))

(defn limit [len codec & opts]
	(apply b/padding codec :length len opts))

(def byte-blob
	(b/compile-codec (b/repeated :byte)
		identity
		byte-array))

(defn null-terminated-string [encoding]
	{:pre [(chunk-size encoding)]}
	(let [type ({1 :byte 2 :short} (chunk-size encoding))]
		(b/compile-codec (b/repeated type :separator 0)
			(fn [s]
				(b/decode (b/repeated type)
					(java.io.ByteArrayInputStream.
						(.getBytes s encoding))))
			(fn [chunks]
				(let [buf (java.io.ByteArrayOutputStream. (* (chunk-size encoding) (count chunks)))]
					(b/encode (b/repeated type) buf chunks)
					(String. (.toByteArray buf) encoding))))))

(defn repeated-string [encoding]
	(b/repeated (null-terminated-string encoding)))

(defn text-frame-content [size charset-codec charset->content-codec]
	(limit size
		(b/header charset-codec
			(fn [enc] (b/compile-codec (charset->content-codec enc)
				:id3.frame/content
				(partial hash-map :id3.frame/encoding enc :id3.frame/content)))
			:id3.frame/encoding)
		:truncate? true))

(defn user-text-frame-content [size charset-codec charset->content-codec]
	(limit size
		(b/header charset-codec
			(fn [enc] (b/compile-codec
				(b/ordered-map
					:id3.frame/description (null-terminated-string enc)
					:id3.frame/content (charset->content-codec enc))
				#(dissoc % :id3.frame/encoding)
				#(assoc % :id3.frame/encoding enc)))
			:id3.frame/encoding)
		:truncate? true))

(defn user-url-frame-content [size charset-codec charset->content-codec]
	(limit size
		(b/header charset-codec
			(fn [enc] (b/compile-codec
				(b/ordered-map
					:id3.frame/description (null-terminated-string enc)
					:id3.frame/url (charset->content-codec latin1))
				#(dissoc % :id3.frame/encoding)
				#(assoc % :id3.frame/encoding enc)))
			:id3.frame/encoding)
		:truncate? true))

(defn picture-content [size charset-codec]
	(limit size
		(b/header charset-codec
			(fn [enc] (b/compile-codec
				(b/ordered-map
					:id3.frame/mime-type (null-terminated-string latin1)
					:id3.frame/picture-type :byte
					:id3.frame/description (null-terminated-string enc)
					:id3.frame/bytes byte-blob)
				#(dissoc % :id3.frame/encoding)
				#(assoc % :id3.frame/encoding enc)))
			:id3.frame/encoding)))

;; For info about synchsafe integers, see section 6.2 of this document:
;; https://id3.org/id3v2.4.0-structure
(defn int->synchsafe [n]
	{:pre [(< -1 n (bit-shift-left 1 28))]}
	(reduce bit-or (int 0)
		(for [i (range 4)]
			(bit-shift-left
				(bit-and (bit-shift-right n (* 7 i)) 127)
				(* 8 i)))))

(defn synchsafe->int [n]
	{:pre [(< -1 n (bit-shift-left 1 32)) (zero? (bit-and n 2r10000000100000001000000010000000))]}
	(reduce bit-or (int 0)
		(for [i (range 4)]
			(bit-shift-left
				(bit-and (bit-shift-right n (* 8 i)) 255)
				(* 7 i)))))

(def synchsafe-int (b/compile-codec :int-be int->synchsafe synchsafe->int))

(defn verify-frame-id [frame-ids id]
	(when-not (or (frame-ids id) (= padding-id id) (#{\X \Y \Z} (first id)))
		(error "Unknown frame id (%s)" id))
	id)

(defn remove-padding [{:id3/keys [frames size] :as tag}]
	(update-in tag [:id3/frames]
		(partial remove (comp #{:id3.frame.type/padding} frame-type :id3.frame/id))))

(defn frame-body-size [{:id3.frame/keys [id content bytes url encoding description] :as body}]
	(let [null-size (chunk-size encoding)]
		(case (frame-type id)
			:id3.frame.type/picture (+ 1 ; encoding
				(encoded-size (null-terminated-string latin1) (:id3.frame/mime-type body)) ; mime type
				1 ; picture type
				(encoded-size (null-terminated-string encoding) description) ; description
				(alength bytes)) ; picture data
			:id3.frame.type/user-text (+ 1 ; encoding
				(- null-size) ; no trailing null
				(encoded-size (repeated-string encoding) (cons description content)))
			:id3.frame.type/text (+ 1
				(- null-size)
				(encoded-size (repeated-string encoding) content))
			:id3.frame.type/user-url (+ 1
				(- null-size)
				(encoded-size (repeated-string encoding) (cons description url)))
			:id3.frame.type/url (alength (.getBytes url latin1))
			:id3.frame.type/blob (alength bytes))))

(defmulti body-codec :id3/version)
(defmulti frame-names-for-version identity)
(defmulti frame-ids-for-version identity)
(defmulti encodings-for-version identity)
