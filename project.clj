(defproject zsau/id3 "1.0.0"
	:description "Simple ID3v2 parser"
	:url "https://github.com/zsau/id3"
	:license {
		:name "Eclipse Public License"
		:url "http://www.eclipse.org/legal/epl-v10.html"}
	:profiles {
		:dev {:resource-paths ["test/resources"]}}
	:dependencies [
		[medley "1.3.0"]
		[org.clojure/clojure "1.10.1"]
		[smee/binary "0.5.5"]])
