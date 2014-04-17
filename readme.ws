;; gorilla-repl.fileformat = 1

;; **
;;; ## Usage
;;; 
;;; Most of times we need to work with images that already exist, such files in the
;;; disk. So, to load a file from the disk, we write something like
;; **

;; @@
(require '[eye-boof.core :refer :all]
         '[eye-boof.io :refer :all]
         '[eye-boof.visualize :refer [view]])

(def color-img (-> "http://png-3.vector.me/files/images/8/0/807142/realistic_vector_eye_thumb.jpg"
                   (java.net.URL.)
                   (load-image)))

;; Visualize the image:
(view color-img)
 
 color-img
;; @@
;; =>
;;; #<MultiSpectral boofcv.struct.image.MultiSpectral@117a26fe>
;; <=

;; **
;;; ![color eye](http://png-3.vector.me/files/images/8/0/807142/realistic_vector_eye_thumb.jpg)
;;; 
;;; Note that a `MultiSpectral` data structure is returned, which is the common data structure
;;; to store the pixels' intensities of color images. To know how many color channels this
;;; image holds, we can type
;; **

;; @@
(nbands color-img)
;; @@
;; =>
;;; 3
;; <=

;; **
;;; what probably means a RGB image. To take a grayscale version of the image, we can take one of the color bands, like the Red:
;; **

;; @@
(band color-img 0)
;; @@
;; =>
;;; #<ImageUInt8 boofcv.struct.image.ImageUInt8@6be86f52>
;; <=

;; **
;;; ![red channel](http://i58.tinypic.com/slgrdj.png)
;;; 
;;; Other way of doing this is taking the average of the RGB pixel values
;; **

;; @@
(average-band color-img)
;; @@
;; =>
;;; #<ImageUInt8 boofcv.struct.image.ImageUInt8@80ac97f>
;; <=

;; **
;;; To save an image into a disk file, we can use the function `save-image!` from the `eye-boof.io` namespace. The string path of the file tells the format representation of the image, like `jpg` or `png` for example. The supported formats are the same supported by `javax.imageio.ImageIO`, the underlying class to read/write image files.
;; **

;; @@
(save-image! color-img "color_eye.jpg")
;; @@
;; =>
;;; true
;; <=

;; **
;;; ### Image segmentation
;;; 
;;; [Image segmentation](http://en.wikipedia.org/wiki/Image_segmentation) is tipically used to locate objects and boundaries (lines, curves, etc.) in images. The simplest technique to achieve image segmentation is called [thresholding](thresholding), which is applied on a grayscale image and turns it into a binary image. Taking the Red band of our `color-img`, we can try to segment the pupil from the rest of the eye using the following code:
;; **

;; @@
(require '[eye-boof.segmentation :refer [threshold]])

(-> (band color-img 0) (threshold :default 100))
;; @@
;; =>
;;; #<ImageUInt8 boofcv.struct.image.ImageUInt8@2f5a73a0>
;; <=

;; **
;;; The above code results in a binary image, where most of the pupil is represented by `0` pixels and the rest of the image by `1` pixels. But if we try to visualize this output we would see a completely dark picture, since `0`s and `1`s are rendered as very dark pixels. To better visualize the results, we could multiply all the pixels' values by `255`: 
;; **

;; @@
(require '[eye-boof.math :as m])

(-> (band color-img 0) (threshold :default 100) (m/* 255))
;; @@
;; =>
;;; #<ImageUInt8 boofcv.struct.image.ImageUInt8@15a0d1a4>
;; <=

;; **
;;; ![black-white eye](http://i58.tinypic.com/105nxwk.png)
;; **

;; **
;;; But there are some cases where the luminosity changes along the image and trying to segment it just using a single threshold value can result in undesired results. Let's see an example:
;; **

;; @@
(def gray-sonnet (-> "http://homepages.inf.ed.ac.uk/rbf/HIPR2/images/son1.gif"
                     (java.net.URL.)
                     (load-image)))

(view gray-sonnet
      (-> (threshold gray-sonnet :default 100) (m/* 255))
      (-> (threshold gray-sonnet :default 150) (m/* 255)))
;; @@
;; =>
;;; #<JFrame$Tag$fd407141 seesaw.core.proxy$javax.swing.JFrame$Tag$fd407141[frame0,683,0,1195x543,invalid,layout=java.awt.BorderLayout,title=Image Viewer,resizable,normal,defaultCloseOperation=HIDE_ON_CLOSE,rootPane=javax.swing.JRootPane[,2,18,1191x523,invalid,layout=javax.swing.JRootPane$RootLayout,alignmentX=0.0,alignmentY=0.0,border=,flags=16777673,maximumSize=,minimumSize=,preferredSize=],rootPaneCheckingEnabled=true]>
;; <=

;; **
;;; <div style='display:inline-block;width:100%;'>
;;; <img alt='sonnet for Lena' src='http://homepages.inf.ed.ac.uk/rbf/HIPR2/images/son1.gif' width='200'> 
;;; <img alt='sonnet for Lena' src='http://i60.tinypic.com/35k299t.png' width='200'>
;;; <img alt='sonnet for Lena' src='http://i59.tinypic.com/27y1lyd.png' width='200'>
;;; </div>
;; **

;; **
;;; As we can see, there is not a global threshold value to achieve a good segmentation of the text. An alternative approach is to use local threshold values, like the `:adaptive-square` algorithm.
;; **

;; @@
(view gray-sonnet
      (-> (threshold gray-sonnet :adaptive-square 15 -8) (m/* 255)))
;; @@
;; =>
;;; #<JFrame$Tag$fd407141 seesaw.core.proxy$javax.swing.JFrame$Tag$fd407141[frame0,683,0,683x749,invalid,layout=java.awt.BorderLayout,title=Image Viewer,resizable,normal,defaultCloseOperation=HIDE_ON_CLOSE,rootPane=javax.swing.JRootPane[,2,18,794x523,invalid,layout=javax.swing.JRootPane$RootLayout,alignmentX=0.0,alignmentY=0.0,border=,flags=16777673,maximumSize=,minimumSize=,preferredSize=],rootPaneCheckingEnabled=true]>
;; <=

;; **
;;; <div style='display:inline-block;width:100%;'>
;;; <img alt='sonnet for Lena' src='http://homepages.inf.ed.ac.uk/rbf/HIPR2/images/son1.gif' width='200'> 
;;; <img alt='sonnet for Lena' src='http://i57.tinypic.com/29c2zrc.png' width='200'>
;;; </div>
;; **

;; **
;;; In the example above, the `:adaptive-square` threshold calculated a specific threshold value for each pixel of the image. The value was the result of calculating the average intensity of `15x15` square neighborhood and adding a bias of `-8`.
;; **
