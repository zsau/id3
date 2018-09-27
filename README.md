# id3

A simple ID3v2 parser, written in Clojure. Supports most common features of [ID3v2.3](http://id3.org/id3v2.3.0) and [ID3v2.4](http://id3.org/id3v2.4.0-structure).

## Artifact

Leiningen dependency:
```clojure
[zsau/id3 "1.0.0"]
```

## Usage

Terminology note: the ID3 specs use the term *tag* to refer to the entire ID3 header, and *frame* to refer to the individual chunks of information that make up the tag. This conflicts with how the word "tag" is commonly used today, but since this is an ID3 library we use ID3's terminology.

To parse the ID3 tag of an MP3:
```clojure
(with-mp3 [mp3 "foo.mp3"] (:id3/tag mp3))
#:id3.frame.name{:artist ["Led Zeppelin"], :album ["Led Zeppelin I"], :date ["1969-01-12"], :track-number ["01"], :title ["Good Times Bad Times"]}
```

Note that values are collections, because ID3v2.4 support multiple values for most frames. To write a new MP3 file with modified tags:
```clojure
(with-mp3 [mp3 "foo.mp3"]
  (write-mp3 "bar.mp3"
    (assoc-in mp3 [:id3/tag :id3.frame.name/genre] ["Rock"])))
```

Or to overwrite an existing file's tags:
```clojure
(overwrite-tag "foo.mp3"
  (with-mp3 [mp3 "foo.mp3"]
    (assoc (:id3/tag mp3) :id3.frame.name/genre ["Rock"])))
```

## API

When using this API, keep in mind the distinction between a *tag* (just the ID3 metadata; see `read-tag`) and an *mp3* (both the metadata and the audio data; see `read-mp3`).

### read-tag
```clojure
(read-tag istream & opts)
```
Reads an ID3v2 tag from `istream`, returning a map whose format depends on the `:id3/format` option:
- `:id3.format/simple` _(default)_ A basic format that supports only common ID3 frames, with keywordized names. For a list of supported frames and their keys, see the output of `(frame-keywords N)`, where `N` is 3 (for ID3v2.3) or 4 (for ID3v2.4).
```clojure
#:id3.frame.name{:artist ["Billy Joel"], :title ["Piano Man"],
  :picture (#:id3.frame{:picture-type 3, :mime-type "image/jpeg", :content #object["[B" 0x15d8429d "[B@15d8429d"]})}
```
- `:id3.format/normal` A more comprehensive format that should support all common cases. Keys are strings, corresponding to frame names from the ID3 specs.
```clojure
{"TPE1" ["Billy Joel"], "TIT2" ["Piano Man"],
  "APIC" (#:id3.frame{:picture-type 3, :mime-type "image/jpeg", :content #object["[B" 0x15d8429d "[B@15d8429d"]})}
```
- `:id3.format/full` Everything but the kitchen sink. This is more info than most will need, but it describes nearly everything about the ID3 tag in gory detail. Frames are listed in the order in which they appear in the tag.
```clojure
#:id3{:size 2301322, :flags #{}, :version 4, :revision 0, :magic-number "ID3",
  :frames (
    #:id3.frame{:id "TPE1", :encoding "ISO-8859-1", :size 11, :flags #{}, :content ["Billy Joel"]}
    #:id3.frame{:id "TIT2", :encoding "ISO-8859-1", :size 10, :flags #{}, :content ["Piano Man"]}
    #:id3.frame{:id "APIC", :encoding "ISO-8859-1", :size 89815, :flags #{}, :picture-type 3, :mime-type "image/jpeg", :description "", :content #object["[B" 0x15d8429d "[B@15d8429d"]})}
```

### read-mp3
```clojure
(read-mp3 src & opts)
```
Parses the MP3 file at `src` (anything accepted by `clojure.java.io/input-stream`). Returns a map with these keys:
- `:id3/tag` the parsed ID3 tag
- `:id3/data` an *open input stream* positioned after the ID3 tag (i.e. at the start of the MPEG frames). You need to close this stream when you're done with it, but see `with-mp3`.

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
- `:id3/version` ID3v2.x tag version to write (3 or 4, default 4)
- `:id3/encoding` character encoding to use for text frames, etc. ID3v2.3 supports ISO-8859-1 and UTF-16; ID3v2.4 supports those plus UTF-16BE and UTF-8.
- `:id3/padding` bytes of padding to write (default 1024)

### write-mp3
```clojure
(write-mp3 dest tag & opts)
```
Writes an mp3 (tag and audio data) to `dest` (anything accepted by `clojure.java.io/output-stream`). Options as in `write-tag`.

### overwrite-tag
```clojure
(overwrite-tag path tag & opts)
```
Overwrites the ID3 tag of the existing MP3 file at `path`. Will avoid rewriting the file's audio data if possible. Options as in `write-tag`, but padding may be ignored.

## Caveats

- ID3v2.2 is unsupported.

- Some uncommon features are unsupported, such as encryption, compression, and unsynchronisation. Notably, appended tags are unsupported because this is a stream-oriented library, and appended tags are impossible to parse from a stream without discarding or storing in memory the file's entire audio data.

- Any frames that are not text, picture, or URL frames are currently just parsed as binary blobs. Let me know if there's another frame type you'd like to see supported.

- Even though ID3v2.3 technically supports multiple values for certain text frames, this library always treats ID3v2.3 text frames as having a single value. This is because the spec uses `/` as a delimiter and makes no provision for escaping literal slashes, which makes it impossible to represent artists like `AC/DC` correctly (and in general makes round-tripping impossible). In addition, support for multiple values among other tag parsers is inconsistent. It's just too error-prone to handle at the parser level; if you want multiple values in ID3v2.3, you'll have to deal with them yourself.

## License

Copyright © 2014 zsau

Distributed under the Eclipse Public License, either version 1.0 or (at your option) any later version.
