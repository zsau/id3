(ns id3.spec
	(:require
		[id3.v3]
		[id3.v4]
		[id3.common]
		[clojure.set :as set]
		[clojure.spec.alpha :as s]))

(s/def :id3.frame/id (set/union id3.v3/frame-ids id3.v4/frame-ids))
(s/def :id3.frame/size (s/int-in 1 (Math/pow 2 28))) ; 32-bit synchsafe ints hold 28 bits
(s/def :id3.frame/flags (s/coll-of #{:id3.frame.flag/data-length :id3.frame.flag/unsynchronized :id3.frame.flag/encrypted :id3.frame.flag/compressed :id3.frame.flag/grouped :id3.frame.flag/read-only :id3.frame.flag/file-alter-preserve :id3.frame.flag/tag-alter-preserve}
	:kind set?
	:gen-max 0))
(s/def :id3.frame/encoding #{"UTF-8" "ISO-8859-1" "UTF-16" "UTF-16BE"})
(s/def :id3.frame/picture-type (s/int-in 0 20))
(s/def :id3.frame/mime-type string?)
(s/def :id3.frame/description string?)
(s/def :id3.frame/bytes bytes?)
(s/def :id3.frame/url string?)
(s/def :id3.frame/content (s/coll-of string? :min-count 1 :gen-max 3))

(defmulti frame-simple-v3 #(some-> % first id3.v3/frame-name->id id3.common/frame-type))
(defmulti frame-simple-v4 #(some-> % first id3.v4/frame-name->id id3.common/frame-type))
(defmulti frame-normal-v3 #(some-> % first id3.v3/frame-ids id3.common/frame-type))
(defmulti frame-normal-v4 #(some-> % first id3.v4/frame-ids id3.common/frame-type))

(s/def :id3.frame.format/simple-v3 (s/multi-spec frame-simple-v3 (fn [v _] v)))
(s/def :id3.frame.format/simple-v4 (s/multi-spec frame-simple-v4 (fn [v _] v)))
(s/def :id3.frame.format/normal-v3 (s/multi-spec frame-normal-v3 (fn [v _] v)))
(s/def :id3.frame.format/normal-v4 (s/multi-spec frame-normal-v4 (fn [v _] v)))

(defn frame-type-names [version frame-type]
	(map first
		(filter (fn [[name id]] (= frame-type (id3.common/frame-type id)))
			(id3.common/frame-names-for-version version))))

(defn frame-type-ids [version frame-type]
	(filter (fn [id] (= frame-type (id3.common/frame-type id)))
		(id3.common/frame-ids-for-version version)))

(doseq [
		[multimethod frame-type-keyfn] {
			frame-simple-v3 (partial frame-type-names 3)
			frame-simple-v4 (partial frame-type-names 4)
			frame-normal-v3 (partial frame-type-ids 3)
			frame-normal-v4 (partial frame-type-ids 4)}
		[frame-type val-spec] #:id3.frame.type{
			:text (s/coll-of string? :min-count 1 :gen-max 3)
			:user-text (s/every-kv string? (s/coll-of string? :min-count 1 :gen-max 3) :min-count 1 :gen-max 3)
			:url string?
			:user-url (s/every-kv string? string? :min-count 1 :gen-max 3)
			:picture (s/coll-of (s/keys :req [:id3.frame/picture-type :id3.frame/mime-type :id3.frame/bytes]) :min-count 1 :gen-max 3)
			:blob bytes?}]
	(when-let [valid-keys (seq (frame-type-keyfn frame-type))]
		(defmethod multimethod frame-type [_]
			(s/tuple (set valid-keys) val-spec))))

(defmulti frame-full-v3 #(some-> % :id3.frame/id id3.v3/frame-ids id3.common/frame-type))
(defmulti frame-full-v4 #(some-> % :id3.frame/id id3.v4/frame-ids id3.common/frame-type))
(s/def :id3.frame.format/full-v3 (s/multi-spec frame-full-v3 (fn [v _] v)))
(s/def :id3.frame.format/full-v4 (s/multi-spec frame-full-v4 (fn [v _] v)))

(doseq [
		multimethod [frame-full-v3 frame-full-v4]
		[frame-type additional-keys] #:id3.frame.type{
			:text [:id3.frame/content :id3.frame/encoding]
			:user-text [:id3.frame/content :id3.frame/encoding :id3.frame/description]
			:url [:id3.frame/url]
			:user-url [:id3.frame/url :id3.frame/encoding :id3.frame/description]
			:picture [:id3.frame/bytes :id3.frame/picture-type :id3.frame/mime-type :id3.frame/description :id3.frame/encoding]
			:blob [:id3.frame/bytes]}]
	(defmethod multimethod frame-type [_]
		(let [ks (concat additional-keys [:id3.frame/id :id3.frame/size :id3.frame/flags])]
			; s/keys is a macro and expects a literal list of keys :(
			(eval `(s/keys :req ~ks)))))

(s/def :id3/magic-number #{"ID3"})
(s/def :id3/size (s/int-in 1 (Math/pow 2 28)))
(s/def :id3/flags (s/coll-of #{:id3.flag/footer :id3.flag/experimental :id3.flag/extended-header :id3.flag/unsynchronized}
	:kind set?))
(s/def :id3/version #{3 4})
(s/def :id3/revision (s/int-in 0 256))
(s/def :id3/encoding :id3.frame/encoding)
(s/def :id3/padding :id3.frame/size)
(s/def :id3/format #{:simple :normal :full})
(s/def :id3/frames (s/or
	:v4 (s/coll-of :id3.frame.format/full-v4)
	:v3 (s/coll-of :id3.frame.format/full-v3)))

(defmulti tag-format id3/tag-format)
(s/def :id3/tag (s/multi-spec tag-format (fn [v _] v)))

(let [mm tag-format]
	(defmethod mm :simple [_]
		(s/or
			:v4 (s/coll-of :id3.frame.format/simple-v4 :kind map? :min-count 1)
			:v3 (s/coll-of :id3.frame.format/simple-v3 :kind map? :min-count 1)))
	(defmethod mm :normal [_]
		(s/or
			:v4 (s/coll-of :id3.frame.format/normal-v4 :kind map? :min-count 1)
			:v3 (s/coll-of :id3.frame.format/normal-v3 :kind map? :min-count 1)))
	(defmethod mm :full [_]
		(s/keys :req [:id3/magic-number :id3/size :id3/flags :id3/version :id3/revision :id3/frames])))

(s/def :id3/istream (partial instance? java.io.InputStream))
(s/def :id3/ostream (partial instance? java.io.OutputStream))

(s/def :id3/data :id3/istream)
(s/def :id3/mp3 (s/keys :req [:id3/tag :id3/data]))

(s/def :id3/read-opts (s/keys* :opt-un [:id3/format]))
(s/def :id3/write-opts (s/keys* :opt-un [:id3/version :id3/encoding :id3/padding]))

(s/fdef id3/read-tag
	:args (s/cat
		:istream :id3/istream
		:opts :id3/read-opts)
	:ret :id3/tag)

(s/fdef id3/read-mp3
	:args (s/cat
		:src any?
		:opts :id3/read-opts)
	:ret :id3/mp3)

(s/fdef id3/write-tag
	:args (s/cat
		:ostream :id3/ostream
		:tag :id3/tag
		:opts :id3/write-opts)
	:ret nil?)

(s/fdef id3/write-mp3
	:args (s/cat
		:dest any?
		:mp3 :id3/mp3
		:opts :id3/write-opts)
	:ret nil?)

(s/fdef id3/overwrite-tag
	:args (s/cat
		:path string?
		:tag :id3/tag
		:opts :id3/write-opts)
	:ret nil?)
