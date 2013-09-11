#!/bin/bash

mkdir -p target/classes/slides-small

for slide in target/classes/markdown/*.md; do
  slidename=$(basename $slide .md)
  sed -i "s/<\!--frames-->/\<iframe src=\"$slidename.html\" width=\"100%\" height=\"100%\" frameborder=\"0\"\><\/iframe>\n<\!--frames-->/" target/classes/html/index.html
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
