#!/bin/bash
cp notag.mp3 v3latin1.mp3 && eyeD3 -2 --no-tagging-time-frame --to-v2.3 --set-encoding=latin1 -a Nobody -t Nothing v3latin1.mp3
cp notag.mp3 v3utf16.mp3 && eyeD3 -2 --no-tagging-time-frame --to-v2.3 --set-encoding=utf16-LE -a Nobody -t Nothing v3utf16.mp3
cp notag.mp3 v4latin1.mp3 && eyeD3 -2 --no-tagging-time-frame --to-v2.4 --set-encoding=latin1 -a Nobody -t Nothing v4latin1.mp3
cp notag.mp3 v4utf8.mp3 && eyeD3 -2 --no-tagging-time-frame --to-v2.4 --set-encoding=utf8 -a Nobody -t Nothing v4utf8.mp3
cp notag.mp3 v4utf16.mp3 && eyeD3 -2 --no-tagging-time-frame --to-v2.4 --set-encoding=utf16-LE -a Nobody -t Nothing v4utf16.mp3
cp notag.mp3 v4utf16be.mp3 && eyeD3 -2 --no-tagging-time-frame --to-v2.4 --set-encoding=utf16-BE -a Nobody -t Nothing v4utf16be.mp3
