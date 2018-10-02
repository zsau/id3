#!/bin/bash
set -euo pipefail

for enc in latin1 utf8 utf16 utf16-be; do
	BASIC="basic-v2.4-$enc.mp3"
	cp notag.mp3 "$BASIC" && eyeD3 -Q -2 --to-v2.4 --encoding=$enc -a Nobody -t Nothing "$BASIC"
	NON_LATIN="non-latin-v2.4-$enc.mp3"
	if [ $enc != latin1 ]; then
		cp notag.mp3 "$NON_LATIN" && eyeD3 -Q -2 --to-v2.4 --encoding=$enc -a 'Mötley Crüe' -t '白い夏と緑の自転車 赤い髪と黒いギター' "$NON_LATIN"
	fi
done

for enc in latin1 utf16; do
	BASIC="basic-v2.3-$enc.mp3"
	cp notag.mp3 "$BASIC" && eyeD3 -Q -2 --to-v2.3 --encoding=$enc -a Nobody -t Nothing "$BASIC"
	NON_LATIN="non-latin-v2.3-$enc.mp3"
	if [ $enc != latin1 ]; then
		cp notag.mp3 "$NON_LATIN" && eyeD3 -Q -2 --to-v2.3 --encoding=$enc -a 'Mötley Crüe' -t '白い夏と緑の自転車 赤い髪と黒いギター' "$NON_LATIN"
	fi
done
