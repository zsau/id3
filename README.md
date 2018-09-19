# id3

A simple ID3v2 parser, written in Clojure. Supports most common features of [ID3v2.3](http://id3.org/id3v2.3.0) and [ID3v2.4](http://id3.org/id3v2.4.0-structure).

## Artifact

Leiningen dependency:
```clojure
[zsau/id3 "1.0.0"]
```

## Usage

To parse the ID3 tag of an MP3:
```clojure
(with-mp3 [mp3 "foo.mp3"] (:tag mp3))
; {:artist ["Michael Jackson"] :title ["Smooth Criminal"]}
```

To write a new MP3 file with modified tags:
```clojure
(with-mp3 [mp3 "foo.mp3"]
  (write-mp3 "bar.mp3"
    (assoc-in mp3 [:tag :title] ["Thriller"])))
```

Or to overwrite an existing file's tags:
```clojure
(overwrite-tag "foo.mp3"
  (with-mp3 [mp3 "foo.mp3"]
    (assoc (:tag mp3) :title ["Thriller"])))
```

## API

When using this API, keep in mind the distinction between a "tag" (just the ID3 metadata; see `read-tag`) and an "mp3" (both the metadata and the audio data; see `read-mp3`).

### read-tag
```clojure
(read-tag istream & opts)
```
Reads an ID3v2 tag from `istream`, whose format depends on the `:format` argument:
- `:simple` _(default)_ A basic format that supports only common ID3 frames, with keywordized names. Values are collections, to support multiple values. For a list of supported frames and their keys, see the output of `(frame-keywords N)`, where `N` is 3 (for ID3v2.3) or 4 (for ID3v2.4).
```clojure
{:artist ["Billy Joel"], :title ["Piano Man"]}
```
- `:normal` A more comprehensive format that should support all common cases. Keys are strings, corresponding to frame names from the ID3 specs.
```clojure
{"TPE1" ["Billy Joel"], "TIT2" ["Piano Man"], "APIC" ({:content #<byte[] [B@6a331017>, :mime-type "image/png", :picture-type 3})}
```
- `:full` Everything but the kitchen sink. This is more info than most will need, but it describes nearly everything about the ID3 tag in gory detail. Frames are listed in the order in which they appear in the tag.
```clojure
{:size 2301322, :flags #{}, :version {:minor 0, :major 4}, :magic-number "ID3",
 :frames (
   {:encoding "ISO-8859-1", :content ["Billy Joel"], :size 11, :id "TPE1", :flags #{}}
   {:encoding "ISO-8859-1", :content ["Piano Man"], :size 10, :id "TIT2", :flags #{}}
   {:id "APIC", :size 696989, :flags #{}, :content #<byte[] [B@c720a30>, :description "cover", :picture-type 3, :mime-type "image/png"})}
```

### read-mp3
```clojure
(read-mp3 src & opts)
```
Parses the MP3 file at `src` (anything accepted by `clojure.java.io/input-stream`). Returns a map with these keys:
- `:tag` the parsed ID3 tag
- `:data` an *open input stream* positioned after the ID3 tag (i.e. at the start of the MPEG frames). You need to close this stream when you're done with it, but see `with-mp3`.

Options as in `read-tag`.

### with-mp3
```clojure
(with-mp3 [sym src & opts] & body)
```
Convenience macro that evaluates `body` with `sym` bound to the mp3 at `src`, then closes the mp3's input stream. Options as in `read-mp3`.

### write-tag
```clojure
(write-tag ostream tag & opts)
```
Writes an ID3v2 tag to `ostream`. Writes only the tag, not any audio data. Options:
- `:version` ID3v2.x tag version to write (3 or 4, default 4)
- `:encoding` character encoding to use for text frames, etc. ID3v2.3 supports ISO-8859-1 and UTF-16; ID3v2.4 supports those plus UTF-16BE and UTF-8.
- `:padding` bytes of padding to write (default 1024)

### write-mp3
```clojure
(write-mp3 dest tag & opts)
```
Writes an mp3 (tag and audio data) to `dest` (anything accepted by `clojure.java.io/output-stream`). Options as in `write-tag`.

### overwrite-tag
```clojure
(overwrite-tag path tag & opts)
```
Overwrites the ID3 tag of the existing MP3 file at `path`. Will avoid rewriting the file's audio data if possible. Options as in `write-tag`, but `:padding` may be ignored.

## Caveats

- ID3v2.2 is unsupported.

- Some uncommon features are unsupported, such as encryption, compression, and unsynchronisation. Notably, appended tags are unsupported because this is a stream-oriented library, and appended tags are impossible to parse from a stream without discarding or storing in memory the file's entire audio data.

- Any frames that are not text, picture, or URL frames are currently just parsed as binary blobs. Let me know if there's another frame type you'd like to see supported.

- Even though ID3v2.3 technically supports multiple values for certain text frames, this library always treats ID3v2.3 text frames as having a single value. This is because the spec uses `/` as a delimiter and makes no provision for escaping literal slashes, which makes it impossible to represent artists like `AC/DC` correctly (and in general makes round-tripping impossible). In addition, support for multiple values among other tag parsers is inconsistent. It's just too error-prone to handle at the parser level; if you want multiple values in ID3v2.3, you'll have to deal with them yourself.

## License

Copyright © 2014 zsau

Distributed under the Eclipse Public License, either version 1.0 or (at
your option) any later version.
