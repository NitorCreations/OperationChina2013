# JavaOne Shanghai 2013 javafx presentation #

So I won a Raspberry Pi from [this session](https://oraclecn.activeevents.com/connect/sessionDetail.ww?SESSION_ID=2231)
at JavaOne and as [Stephen Chin](http://steveonjava.com/) gave it to me, he asked me to let him know if I did 
anything cool with it.

Also, [my employer](http://nitorcreations.com) encourages us to summarise any sessions we've attended and this work
is now how I thought I could combine (hopefully) something cool and a very short summary of the conference for my 
colleagues.

## Features ##

All of the source of the slides in the presentation are markdown. If you run out of markdown features, you can just
put in html as with the [video](src/main/resources/markdown/36_oracle_event.md).

The presentation can be controlled with either a keyboard or a Nintendo Wii controller:

* Up key, Wiimote direction button up or the gesture "up": Fast transition forward
    * Just rotate to the rotation of the target slide and move it into place
    * First transtion is always slow - slides dropping and spreading out
    * Transition to a slide with video is also always the longer one
* Right key, Wiimote direction button right or the gesture "right": Slow transition forward
    * Zoom out and rotate to original orientation, move to on top of the next slide and then zoom in and rotate for the target slide
* Down key, Wiimote direction button down or the gesture "down": Fast transition backward
* Left key, Wiimote direction button left or the gesture "left": Slow transtion backward
* Key A, Wiimote button '-' or the gesture "clockwise circle": Slow transition to the first slide
* Key E, Wiimote button '2' or the gesture "counter clockwise circle": Slow transtion to the last slide
* Wiimote button 'A': draw highlight where the wiimote Infrared mouse is pointing
* Wiimote button 'B': try to recongnize gesture

The video integration is a bit tricky since Java 8 javafx that is running on the raspberry pi can't
run it out of the box. What happens is that the build script records the position of the video placeholder image
and at runtime the presentation extracts the video into a temporary file. Then when the slide is traversed it zooms in on the
placeholder and runs ```videoplayer {temporaryfile}```. On my Raspberry Pi this script is in ```/usr/bin/videoplayer```:


```
#!/bin/bash

exec /usr/bin/omxplayer "$@"
```

And on my Ubuntu desktop this is ```/usr/bin/videoplayer```:

```
#!/bin/bash

exec /usr/bin/mplayer -noidle -fs "$@"
```

Unfortunately on the Raspberry Pi there is currently no way of controlling the playback.

## Running ##

On the raspberry Pi this requires using the smaller images to fit into memory:

```
java -Dslides=slides-small -jar ffx-presentation-shanghai-1.0-jfx.jar
```

If you don't want to use the wiimote:

```
java -Dslides=slides-small -Dnowiimote=true -jar ffx-presentation-shanghai-1.0-jfx.jar
```


## Caveats ##

The build works on my Ubuntu workstation and my Ubuntu jenkins server and I really have very little interest in making
it work on any other platform. Mainly it requires the following external tools (in addition to maven - naturally):

 * [pandoc](http://johnmacfarlane.net/pandoc/) for converting from markdown to html
 * [phantomjs](http://phantomjs.org/) for converting from html (+css etc.) to png images
 * [optipng](http://optipng.sourceforge.net/) for optimizing the resulting images
 * [convert from imagemagick](http://www.imagemagick.org/) for scaling down for smaller slides on the raspberrypi

These are run as a part of the compile phase in maven through [render.sh](render.sh). 

For the javafx tooling in this it uses [javafx-maven-plugin](http://zenjava.com/javafx/maven/)
so building the jar is just:

```
mvn clean com.zenjava:javafx-maven-plugin:jar
```

The resulting javafx binaries will be under ```target/jfx/app```. Dependencies will go into the lib subdirectory -
all of them need to be included when running.

## Acknowledgements ##

The Wiimote integration is thanks to [Wiigee project](http://www.wiigee.org/) and underneath that I rely on the java
bluetooth stack by [Bluecove](http://bluecove.org/). Wiigee isn't in Maven central and bluecove 2.1.0 at least doesn't
include the JNI bits for arm, so I published both of those in a [github maven repository](https://github.com/NitorCreations/maven-repository).

## Future ##

I'm planning on pushing this as an archetype to the [github maven repository](https://github.com/NitorCreations/maven-repository)
and central if I get the Wiigee and Bluecove I need in central.

