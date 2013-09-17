#!/bin/bash

mkdir -p target/classes/slides-small

for slide in target/classes/markdown/*.md; do
  slidename=$(basename $slide .md)
  export frame=$(sed "s/@slidename@/$slidename/g" target/classes/frame-template.txt)
  TMP_RUN=$(mktemp)
  TMP_FOLLOW=$(mktemp)
  sed -i 's/<\!--frames-->/\${frame}\n<\!--frames-->/' target/classes/html/index-run.html
  sed -i 's/<\!--frames-->/\${frame}\n<\!--frames-->/' target/classes/html/index-follow.html
  mvn -q -Dexec.mainClass=com.nitorcreations.core.utils.Templater -Dexec.arguments=target/classes/html/index-run.html exec:java > $TMP_RUN
  mvn -q -Dexec.mainClass=com.nitorcreations.core.utils.Templater -Dexec.arguments=target/classes/html/index-follow.html exec:java > $TMP_FOLLOW
  slide_notes=" "
  export slide_notes
  mvn -q -Dexec.mainClass=com.nitorcreations.core.utils.Templater -Dexec.arguments=$TMP_FOLLOW exec:java > target/classes/html/index-follow.html
  if [ -r $slide.notes ]; then
    slide_notes=$(pandoc --from markdown --to html $slide.notes)
  fi
  export slide_notes
  mvn -q -Dexec.mainClass=com.nitorcreations.core.utils.Templater -Dexec.arguments=$TMP_RUN exec:java > target/classes/html/index-run.html
  rm -f $TMP_RUN $TMP_FOLLOW
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
