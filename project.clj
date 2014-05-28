(defproject zsau/id3 "0.1.0"
	:description "Simple ID3v2 parser"
	:url "https://github.com/zsau/id3"
	:license {
		:name "Eclipse Public License"
		:url "http://www.eclipse.org/legal/epl-v10.html"}
	:profiles {
		:dev {:resource-paths ["test/resources"]}}
	:dependencies [
		[org.clojure/clojure "1.6.0"]
		[org.clojars.smee/binary "0.3.0"]])
