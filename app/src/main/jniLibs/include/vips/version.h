/* Macros for the header version.
 */

#ifndef VIPS_VERSION_H
#define VIPS_VERSION_H

#define VIPS_VERSION		"8.14.2"
#define VIPS_VERSION_STRING	"8.14.2"
#define VIPS_MAJOR_VERSION	(8)
#define VIPS_MINOR_VERSION	(14)
#define VIPS_MICRO_VERSION	(2)

/* The ABI version, as used for library versioning.
 */
#define VIPS_LIBRARY_CURRENT	(58)
#define VIPS_LIBRARY_REVISION	(2)
#define VIPS_LIBRARY_AGE	(16)

#define VIPS_CONFIG		"enable debug: false\nenable deprecated: true\nenable modules: false\nenable cplusplus: true\nenable RAD load/save: true\nenable Analyze7 load/save: true\nenable PPM load/save: true\nenable GIF load: true\nuse fftw for FFTs: true\naccelerate loops with ORC: false\nICC profile support with lcms: true\nzlib: true\ntext rendering with pangocairo: false\nfont file support with fontconfig: false\nEXIF metadata support with libexif: false\nJPEG load/save with libjpeg: true\nJXL load/save with libjxl: false (dynamic module: false)\nJPEG2000 load/save with OpenJPEG: false\nPNG load/save with libspng: true\nPNG load/save with libpng: false\nselected quantisation package: none\nTIFF load/save with libtiff: true\nimage pyramid save with libgsf: false\nHEIC/AVIF load/save with libheif: false (dynamic module: false)\nWebP load/save with libwebp: true\nPDF load with PDFium: false\nPDF load with poppler-glib: false (dynamic module: false)\nSVG load with librsvg: false\nEXR load with OpenEXR: false\nOpenSlide load: false (dynamic module: false)\nMatlab load with libmatio: false\nNIfTI load/save with niftiio: false\nFITS load/save with cfitsio: false\nGIF save with cgif: false\nselected Magick package: none (dynamic module: false)\nMagick API version: none\nMagick load: false\nMagick save: false"

/* Not really anything to do with versions, but this is a handy place to put
 * it.
 */
#define VIPS_ENABLE_DEPRECATED 1

#endif /*VIPS_VERSION_H*/
