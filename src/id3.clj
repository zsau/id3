(ns id3
	(:require
		[clojure.set :as set]
		[clojure.java.io :as io]
		[id3.common :as common :refer [->coll error latin1 utf8 utf16]]
		[id3.v3] [id3.v4]
		[medley.core :as m]
		[org.clojars.smee.binary.core :as b]))

(def default-padding 1024)

(def latin1-encoder (.newEncoder (java.nio.charset.Charset/forName latin1)))

(def id3-header
	(b/ordered-map
		::magic-number (b/constant (b/string latin1 :length 3) "ID3")
		::version (b/enum :byte {3 3 4 4})
		::revision :byte
		;; ID3v2.3 has no footer tag, but it's not worth the complexity of separate header codecs
		::flags (b/bits [nil nil nil nil :id3.flag/footer :id3.flag/experimental :id3.flag/extended-header :id3.flag/unsynchronized])
		::size common/synchsafe-int))

(def text-frame-keys
	#:id3.frame.type{
		:text #{:id3.frame/content}
		:user-text #{:id3.frame/description :id3.frame/content}
		:picture #{:id3.frame/description}})

(def id3
	(b/header id3-header
		(fn [header]
			(b/compile-codec (common/body-codec header)
				#(apply dissoc % (keys header))
				#(merge % header)))
		(fn [{::keys [version revision flags size] :as tag}] {
			::magic-number "ID3"
			::version version
			::revision revision
			::flags (set/intersection flags #{:id3.flag/experimental})
			::size (max size (common/body-size tag))})))

(defn can-encode? [value encoder]
	(every? #(.canEncode encoder %)
		(->coll value)))

(defn can-encode-latin1? [frame]
	(every? #(can-encode? (frame %) latin1-encoder)
		(text-frame-keys (common/frame-type (:id3.frame/id frame)))))

(defn safe-encoding [tag]
	(case (::version tag)
		3 utf16
		4 utf8))

(defn set-encoding [encoding tag]
	(update tag ::frames
		(partial mapv
			(fn [frame]
				(cond
					(not (text-frame-keys (common/frame-type (:id3.frame/id frame))))
						frame
					encoding
						(assoc frame :id3.frame/encoding encoding)
					(not (:id3.frame/encoding frame))
						(assoc frame :id3.frame/encoding
							(if (can-encode-latin1? frame) latin1 (safe-encoding tag)))
					:else
						frame)))))

(defn full->normal [tag]
	(->> (::frames tag)
		(group-by :id3.frame/id)
		(m/map-kv-vals (fn [id [frame & more :as frames]]
			(case (common/frame-type id)
				:id3.frame.type/text (if more (error "Multiple %s frames" id) (:id3.frame/content frame))
				:id3.frame.type/url (if more (error "Mutliple %s frames" id) (:id3.frame/url frame))
				:id3.frame.type/user-text (m/map-vals :id3.frame/content (m/index-by :id3.frame/description frames))
				:id3.frame.type/user-url (m/map-vals :id3.frame/content (m/index-by :id3.frame/description frames))
				:id3.frame.type/picture (mapv #(select-keys % [:id3.frame/picture-type :id3.frame/mime-type :id3.frame/bytes]) frames)
				:id3.frame.type/blob (mapv :id3.frame/bytes frames))))))

(defn normal->full [version tag] {
	::magic-number "ID3"
	::version version
	::revision 0
	::flags #{}
	::frames (->> tag
		(mapcat (fn [[id contents]]
			(case (common/frame-type id)
				:id3.frame.type/text [{:id3.frame/id id, :id3.frame/content contents}]
				:id3.frame.type/user-text (sort-by :id3.frame/description
					(for [[desc values] contents]
						{:id3.frame/id id, :id3.frame/description desc, :id3.frame/content values}))
				:id3.frame.type/url [{:id3.frame/id id, :id3.frame.url contents}]
				:id3.frame.type/user-url (sort-by :id3.frame/description
					(for [[desc url] contents]
						{:id3.frame/id id, :id3.frame/description desc, :id3.frame/url url}))
				:id3.frame.type/picture (mapv #(assoc % :id3.frame/id id, :id3.frame/description "") contents)
				:id3.frame.type/blob (for [content contents]
					{:id3.frame/id id, :id3.frame/bytes content}))))
		(mapv #(assoc % :id3.frame/flags #{}))
		(sort-by :id3.frame/id))})

(defn full->simple [tag]
	(->> tag
		full->normal
		(m/map-keys (set/map-invert (common/frame-names-for-version (::version tag))))
		(m/remove-keys nil?)))

(defn simple->full [version tag]
	(let [name->id (common/frame-names-for-version version)]
		(->> tag
			(m/map-keys #(or (name->id %) (error "Unknown frame key: %s" %)))
			(normal->full version))))

(defn read-header [path]
	(with-open [f (io/input-stream path)]
		(b/decode id3-header f)))

(defn tag-format [tag]
	(cond
		(::magic-number tag) :full
		(string? (key (first tag))) :normal
		:else :simple))

(defn upconvert-tag
"Detects which format `tag` is in, then converts it to full-format."
	[tag & {:keys [version encoding]}]
	(let [version (or version 4)]
		(set-encoding encoding
			(case (tag-format tag)
				:full tag
				:normal (normal->full version tag)
				:simple (simple->full version tag)))))

(defn downconvert-tag
"Converts the full-format `tag` to the format specified by `fmt`."
	[tag fmt]
	(case fmt
		:full tag
		:normal (full->normal tag)
		:simple (full->simple tag)))

(defn add-padding [padding tag]
	(assoc tag ::size
		(+ (common/body-size tag)
			(or padding default-padding))))

;;; Public API

(defn read-tag
"Reads an ID3v2 tag from `istream`.
Options:
  :format  format in which to parse tag (:full, :normal or :simple, default :simple)"
	[istream & {:keys [format]}]
	(downconvert-tag (b/decode id3 istream)
		(or format :simple)))

(defn read-mp3
"Reads an MP3 file from `src` (anything accepted by `clojure.java.io/input-stream`).
Returns a map with these keys:
  :tag   the parsed ID3 tag
  :data  an open input stream positioned after the ID3 tag (i.e. at the start of the MPEG frames)
Options as in `read-tag`."
	[src & opts]
	(let [in (io/input-stream src)] {
		::tag (apply read-tag in opts)
		::data in}))

(defmacro with-mp3
"Evaluates `body` with `sym` bound to the mp3 `src`, then closes sym's input stream.
Options as in `read-tag`."
	[[sym src & opts] & body]
	(let [temp-sym (gensym)]
		`(let [~temp-sym (read-mp3 ~src ~@opts), ~sym ~temp-sym]
			(try ~@body
				(finally (.close (::data ~temp-sym)))))))

(defn write-tag
"Writes an ID3v2 tag to `ostream`.
Options:
  :version   ID3v2.x tag version to write (3 or 4, default 4)
  :encoding  character encoding to use for text frames, etc. (default UTF-16 for v2.3, UTF-8 for v2.4)
  :padding   bytes of padding to write (default 1024)"
	[ostream tag & {:keys [version encoding padding]}]
	(b/encode id3 ostream
		(add-padding padding
			(upconvert-tag tag :version version :encoding encoding))))

(defn write-mp3
"Writes an mp3 to `dest` (anything accepted by `clojure.java.io/output-stream`).
Options as in `write-tag`."
	[dest {::keys [tag data]} & opts]
	(letfn [(f [out] (apply write-tag out tag opts) (io/copy data out))]
		(if (instance? java.io.OutputStream dest)
			(f dest)
			(with-open [out (io/output-stream dest)]
				(f out)))))

(defn overwrite-tag
"Overwrites the ID3 tag of the MP3 file at `path`. Will avoid rewriting the file's MPEG data if possible.
Options as in `write-tag`, but :padding may be ignored."
	[path tag & {:keys [version encoding padding]}]
	(let [
			tag (upconvert-tag tag :version version :encoding encoding)
			size (common/body-size tag)
			old-size (::size (read-header path))
			padding-left (- old-size size)]
		(if (neg? padding-left) ; will it fit?
			; no (must rewrite whole file)
			(let [tmp-path (format ".%s.tmp" path)]
				(with-open [in (io/input-stream path), out (io/output-stream tmp-path)]
					(write-tag out tag :padding padding)
					(.skip in (+ 10 old-size))
					(io/copy in out))
				(.renameTo (java.io.File. tmp-path) (java.io.File. path)))
			; yes (can overwrite just the tags)
			(let [byte-stream (java.io.ByteArrayOutputStream. (+ 10 size))]
				(write-tag byte-stream tag :padding padding-left)
				(with-open [out (java.io.RandomAccessFile. path "rw")]
					(.write out (.toByteArray byte-stream)))))))
