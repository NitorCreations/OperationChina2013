#!/bin/bash

mkdir -p target/classes/slides-small

for slide in target/classes/markdown/*.md; do
  slidename=$(basename $slide .md)
  pandoc --from markdown --to html --standalone --css=nitor.css $slide --output target/classes/html/$slidename.html
  phantomjs render.js target/classes/html/$slidename.html target/classes/slides/$slidename.png
  VIDEO=$(phantomjs videoposition.js target/classes/html/$slidename.html)
  if [ -n "$VIDEO" ]; then
cat > target/classes/slides/$slidename.video << MARKER
$VIDEO
MARKER
  cp target/classes/slides/$slidename.video target/classes/slides-small/$slidename.video
  fi
  convert target/classes/slides/$slidename.png -resize 960 target/classes/slides-small/$slidename.png
  optipng target/classes/slides/$slidename.png
  optipng target/classes/slides-small/$slidename.png
done
