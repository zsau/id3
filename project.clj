(defproject zsau/id3 "0.2.0"
	:description "Simple ID3v2 parser"
	:url "https://github.com/zsau/id3"
	:license {
		:name "Eclipse Public License"
		:url "http://www.eclipse.org/legal/epl-v10.html"}
	:profiles {
		:dev {:resource-paths ["test/resources"]}}
	:dependencies [
		[org.clojure/clojure "1.9.0"]
		[smee/binary "0.5.3"]])
