frames := $(patsubst target/classes/markdown/%.md,%,$(wildcard target/classes/markdown/*.md))

all:	target/frames-run target/frames-follow htmls pngs smallpngs pdfs presentationpdf

.PHONY:	all htmls pngs smallpngs pdfs presentationpdf

target/frame-%-base:	target/classes/frame-template.txt
	sed "s/@slidename@/"$(patsubst target/frame-%-base,%,$@)"/g" target/classes/frame-template.txt > $@.tmp
	mv $@.tmp $@

target/frame-%-follow:	target/frame-%-base
	slide_notes=" " ; export slide_notes ; mvn -q -Ptemplater -DtemplateFile=$< validate > $@.tmp
	mv $@.tmp $@

target/frame-%-run:	target/frame-%-base
	slide_notes=" " ; \
	notes="$(patsubst target/frame-%-run,target/classes/markdown/%.md.notes,$@)" ; \
	[ -r $$notes ] && slide_notes=$$(pandoc --from markdown --to html "$$notes") ; export slide_notes ; \
	mvn -q -Ptemplater -DtemplateFile=$< validate > $@.tmp
	mv $@.tmp $@

target/frames-follow:	$(patsubst %,target/frame-%-follow,$(frames))
	perl -i -pe 's!<\!--frames-->!`cat $^`!e' target/classes/html/index-follow.html
	touch $@ #marker

target/frames-run:	$(patsubst %,target/frame-%-run,$(frames))
	perl -i -pe 's!<\!--frames-->!`cat $^`!e' target/classes/html/index-run.html
	touch $@ #marker

target/classes/html/%.html:	target/classes/markdown/%.md
	pandoc --from markdown --to html --standalone --css=nitor.css $< --output $@.tmp.html
	mkdir -p target/classes/slides
	mkdir -p target/classes/slides-small
	video=$(patsubst target/classes/html/%.html,%.video,$@) ; \
	phantomjs videoposition.js $@.tmp.html > target/classes/slides/$$video && \
	if [ -s target/classes/slides/$$video ]; then \
		cp target/classes/slides/$$video target/classes/slides-small/$$video ; \
	else \
		rm target/classes/slides/$$video ; \
	fi
	mv $@.tmp.html $@

htmls:	$(patsubst %,target/classes/html/%.html,$(frames))

pngs:	$(patsubst %,target/classes/slides/%.png,$(frames))

smallpngs:	$(patsubst %,target/classes/slides-small/%.png,$(frames))

pdfs:	$(patsubst %,target/pdfs/%.pdf,$(frames))

presentationpdf:	target/classes/html/presentation.pdf

target/classes/slides/%.png:	target/classes/html/%.html
	phantomjs render.js $< $@.tmp.png
	optipng $@.tmp.png
	mv $@.tmp.png $@

target/classes/slides-small/%.png:	target/classes/slides/%.png
	mkdir -p target/classes/slides-small
	convert $< -resize 960 $@.tmp.png
	optipng $@.tmp.png
	mv $@.tmp.png $@

target/pdfs/%.pdf:	target/classes/html/%.html
	mkdir -p target/classes/pdfs
	phantomjs render.js $< $@.tmp.pdf
	mv $@.tmp.pdf $@

target/classes/html/presentation.pdf:	pdfs
	pdfunite $(patsubst %,target/pdfs/%.pdf,$(frames)) target/classes/html/presentation.tmp.pdf
	mv target/classes/html/presentation.tmp.pdf target/classes/html/presentation.pdf

