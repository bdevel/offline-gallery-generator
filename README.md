# Offline Gallery Generator

The goal of this project is to create an off-line photo gallery suitable for
file archiving to encourage the disuse of centralized cloud services like Google
or Apple Photos. It will scan provided directories for photos matching your
criteria and generate an organized file structure with `index.html` files which
can be stored on a NAS or stored on archival storage medium like [M-Disc](https://en.wikipedia.org/wiki/M-DISC).

Apps like
[PhotoSync](https://www.photosync-app.com/home.html)
can be used to copy photos from your mobile device to bypass centralized servers.

**Project status:** Active development


## Planned Features

Example Usage:

``` sh
./generate-gallery
--copy --move --link # what to do with source photos

--clean # remove any existing gallery artifacts
--scrub-exif  # remove any unessisary exif data from originals. Does nothing when --link

--only=*.jpg
--exif-filter=SUB-STRING # only includes files where exif contains SUB-STRING.
--exclude=PATTERN       # exclude files matching PATTERN, ex *.png
--include=PATTERN       # don't exclude files matching PATTERN

--thumb=600 # create multiple resized photos 
--thumb=1024
--max-px=3000 # resize any photos over this size. Does nothing when --link

--split=4.7GB # split gallery into chunks for DVD burn

~/Pictures  # list any number of directories to scan
/mnt/old-backup-drive
./my-gallery #output directory is last
```

Use [tools.cli](https://github.com/clojure/tools.cli) for command line parsing.

### File Scanning
With the specified directories, the project needs to collect the files to add to
the gallery. 

Potential Clojure libraries to use:

* File name scanning, ex `(glob "*.{jpg,gif}")` https://github.com/jkk/clj-glob
* Nice to have, low priority - De-duplication, can use SHA hash or [photo finger
  printing library](https://github.com/KilianB/JImageHash)
* JPEG meta data reading (date, location, exposure, etc) [Clojure EXIF library](https://github.com/joshuamiller/exif-processor)

### Photo Processing

After finding the photos to add, thumbnails need to be generated.

* [Clojure photo resize library](https://github.com/josephwilk/image-resizer)



### Gallery Generation

Files will be grouped together by the date the photo was taken and stored in a logical
directory structure. Files may be renamed based on configuration.

Example structure:

``` sh
/my-gallery
  index.html
  /2020/
        index.html
        /01/index.html
        /01/originals/
                   2020-01-10-1345.orig.jpg
                    2020-01-11-1201.orig.jpg
        /01/thumbs/
                  2020-01-10-1345.sm.jpg
                  2020-01-11-1201.sm.jpg
```

Ideally, there should be a minimum number of files added to an `index.html`
gallery because not every day/month will have a significant number.

After all the daily/weekly/month gallery files are generated, a parent
`index.html` file will be generated which will pick sample photos from each set
to preview for a link to the sub-pages.

This will allow easy navigation to find specific photos with an estimate on
when the photos might have been taken.

Further, the pre-generated thumbnails will allow easy sharing small size files
via email. The gallery will have links to the originals as well.

