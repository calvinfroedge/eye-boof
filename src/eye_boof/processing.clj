(ns eye-boof.processing 
  (:require 
    [eye-boof.core :as c]
    [eye-boof.helpers :as h]
    [incanter.core :as ic])
  (:import
    [boofcv.struct.image ImageBase ImageUInt8 ImageSInt16 ImageFloat32 MultiSpectral]
    [boofcv.alg.filter.blur BlurImageOps]
    [boofcv.alg.filter.derivative GradientSobel]
    [boofcv.alg.filter.binary ThresholdImageOps]
    [boofcv.factory.feature.detect.edge FactoryEdgeDetectors]
    [boofcv.alg.feature.detect.edge CannyEdge]
    [java.awt.geom AffineTransform]
    [java.awt.image BufferedImage AffineTransformOp]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* true)

(defn rgb-to-gray
  "Returns a new Image whose color space is the grayscale. Only ARGB or RGB images 
  are accepted.
  Reference:
  http://en.wikipedia.org/wiki/Grayscale"
  [img]
  {:pre [(contains? #{:rgb :argb} (:type img))]}
  (let [res (c/new-image (c/nrows img) (c/ncols img) :gray)
        gray (c/get-channel res) 
        chs-vec (c/get-channel img)
        [rch gch bch] (if (= :argb (:type img)) (subvec chs-vec 1) chs-vec)]
    (c/for-idx [idx img]
      (->> (* 0.2126 (c/get-pixel rch idx))
           (+ (* 0.7152 (c/get-pixel gch idx)))
           (+ (* 0.0722 (c/get-pixel bch idx)))
           (c/set-pixel! gray idx)))
    res))

(def to-gray rgb-to-gray)

(defn gray-to-rgb
  "Repeats the only grayscale channel for each color channel and returns a new RGB
  Image."
  [img]
  {:pre [(= :gray (:type img))]}
  (let [nr (c/nrows img)
        nc (c/ncols img)
        gray (c/get-channel img 0)
        res (c/new-image nr nc :rgb)
        [rch gch bch] (c/get-channel res)]
    (c/for-idx [idx img]
      (let [p (c/get-pixel gray idx)]
        (c/set-pixel! rch idx p)
        (c/set-pixel! gch idx p)
        (c/set-pixel! bch idx p)))
    res))

(defn binarize
  "Returns a new Image where each pixel value is set to 0 or 255. If the original
  pixel value is below the threshold, the value is set to 0; otherwise, the value is
  set to 255."
  [img th]
  (let [th (long th)
        nr (c/nrows img)
        nc (c/ncols img)
        res (c/new-image nr nc (:type img))
        threshold (fn [^long n] (if (> n th) 255 0))]
    (dotimes [ch (c/dimension img)]
      (let [img-m (c/get-channel img ch)
            res-m (c/get-channel res ch)]
        (c/for-idx [idx img]
          (->> (c/get-pixel img-m idx)
               threshold 
               (c/set-pixel! res-m idx)))))
    res))

(defn threshold
  [img threshold & {:keys [down]}]
  (let [result (c/new-image (c/nrows img) (c/ncols img) :bw)
        res-chan (c/get-channel result 1)]
    
    (ThresholdImageOps/threshold (:mat img) res-chan
                                 threshold (boolean down))
    result))

(defn convolve
  "Applies a convolution mask over an image.
  mask is an one dimensional collection or array with (* mask-size mask-size)
  elements."
  ;; todo: Write a macro for the loops over the mask elements. 
  [img mask ^long mask-size]
  (let [mask (if (c/doubles? mask) mask (into-array Double/TYPE mask))
        nc (c/ncols img)
        nr (c/nrows img)
        res (c/new-image nr nc (:type img))
        offset (long (/ mask-size 2))]
    (dotimes [ch (c/dimension img)]
      (let [res-m (c/get-channel res ch)
            img-m (c/get-channel img ch)]
        (c/for-xy 
          [x y img]
          (loop [xn (long 0), kv (double 0.0)]
            ;; Loop over the x values of the mask.
            (if (< xn mask-size)
              (recur 
                (inc xn)
                (+ kv
                   (loop [yn (long 0), kyv (double 0.0)]
                     ;; Loop over the y values of the mask.
                     (if (< yn mask-size)
                       (recur (inc yn)
                              (->> (c/get-pixel
                                     img-m 
                                     ;; Ensures that the coordinates are out of the
                                     ;; image's bounds.
                                     (-> (+ xn (- x offset))
                                         (max 0)
                                         (min (dec nc)))
                                     (-> (+ yn (- y offset))
                                         (max 0)
                                         (min (dec nr))))
                                   (* (c/mult-aget doubles mask (+ xn (* yn mask-size))))
                                   (+ kyv)))
                       kyv))))
              (c/set-pixel! res-m
                            (+ x (* y nc))
                            (-> (max 0.0 kv) (min 255.0))))))))
    res))

(defn mean-blur
  "Returns a new image resulting from the application of a mean filter with a given
  radius."
  [img ^long rad]
  (let [res (c/new-image (c/nrows img) (c/ncols img) (:type img))]
    (dotimes [ch (c/dimension img)]
      (let [img-m (c/get-channel img ch)
            res-m (c/get-channel res ch)]
        (BlurImageOps/mean img-m res-m rad nil)))
    res))

(defn gaussian-blur
  "Returns a new image as an output of a gaussian blur.
  http://boofcv.org/javadoc/boofcv/alg/filter/blur/BlurImageOps.html#gaussian(boofcv.struct.image.ImageUInt8, boofcv.struct.image.ImageUInt8, double, int, boofcv.struct.image.ImageUInt8)"
  [img ^double sigma ^long radius]
  (let [res (c/new-image (c/nrows img) (c/ncols img) (:type img))]
    (dotimes [ch (c/dimension img)]
      (let [img-m (c/get-channel img ch)
            res-m (c/get-channel res ch)]
        (BlurImageOps/gaussian img-m res-m sigma radius nil)))
    res))

(defn canny-edge
  "Returns a binary image where edges are represented as 1 and the rest of pixels
  are zeros.
  http://boofcv.org/index.php?title=Example_Canny_Edge"
  [img blur-int thr-low thr-high]
  (let [img (if (> (c/dimension img) 1) (to-gray img) img)
        nr (c/nrows img)
        nc (c/ncols img)
        res (c/new-image nr nc :bw)
        canny (FactoryEdgeDetectors/canny blur-int true true ImageUInt8 ImageSInt16)
        img-m (c/get-channel img 0) 
        res-m (c/get-channel res 0)
        _ (.process canny img-m thr-low thr-high res-m)]
    res))

(defn scale
  "Returns a new image as a scaled version of the input image."
  ([img factor] (scale img factor factor))
  ([img xfactor yfactor]
   (let [buff (h/to-buffered-image img)
         out-buff (h/create-buffered-image (* (c/ncols img) xfactor)
                                           (* (c/nrows img) yfactor))]
     (-> (AffineTransformOp. (doto (AffineTransform.) (.scale xfactor yfactor))
                             AffineTransformOp/TYPE_BICUBIC)
         (.filter buff out-buff)
         (h/to-img)))))

; (defn erode
;   "Erodes a Image, a basic operation in the area of the mathematical morphology.
;    http://homepages.inf.ed.ac.uk/rbf/HIPR2/erode.htm
;    The corner and edge values of the mask can be specified. The default values are 0.2."
;    ([img] (erode img 0.2 0.2))
;    ([img corner edge]
;     {:pre [(c/gray-type? img)]}
;     (let [mask [corner  edge    corner
;                 edge    1.0     edge
;                 corner  edge    corner]]
;       (convolve img mask 3))))
; 
; (defn small-blur 
;   "Blurs an image by taking the average of 4 neighbour pixels. The default mask is
;   [0    0.2   0
;    0.2  0.2   0.2
;    0    0.2   0]
;   http://lodev.org/cgtutor/filtering.html"
;   ([img] (small-blur img 0.2))
;   ([img factor]
;    (let [mask [0 factor 0
;                factor factor factor
;                0 factor 0]]
;      (convolve img mask 3))))
; 
; (defn big-blur 
;   "Like small-blur, but with a 5x5 mask.
;   http://lodev.org/cgtutor/filtering.html"
;   ([img] (big-blur img (/ 1 13)))
;   ([img factor]
;    (let [factor (double factor)
;          mask [0 0 factor 0 0
;                0 factor factor factor 0
;                factor factor factor factor factor
;                0 factor factor factor 0
;                0 0 factor 0 0]]
;      (convolve img mask 5))))
; 
; ;
; ;(defn smoothing
; ;  "Returns a new Image resulting of the application of a edge preserving smoothing on
; ;  the given Image.
; ;  -----------
; ;  Reference: 
; ;  Nikolaou, N., & Papamarkos, N. (2009). Color reduction for complex document images.
; ;  International Journal of Imaging Systems and Technology, 19(1), 14–26.
; ;  doi:10.1002/ima.20174"
; ;  [img]
; ;  {:pre [(c/color-type? img)]}
; ;  (letfn [;; Calculates the coefficient value for a pixel.
; ;          (coef [c1 c2] 
; ;               (-> (rgb/abs-distance c1 c2)
; ;                   (/ (* 3 255))
; ;                   (#(- 1 %))
; ;                   (ic/pow 10)))
; ;          ;; Calculates the new value of the pixel [x, y] by applying a convolution.
; ;          (pix-val [x y]
; ;            (->> (get-neighbour-pixels img x y)
; ;                 (map #(coef (c/get-xy img x y) %))
; ;                 ((fn [cs] (map #(/ % (ic/sum cs)) cs)))
; ;                 (apply-kernel img x y)))]
; ;    (->> (reduce (fn [m y]
; ;                   (->> (reduce #(conj! %1 (pix-val %2 y))
; ;                                (transient [])
; ;                                (range (c/ncols img)))
; ;                        persistent!
; ;                        (conj! m))) 
; ;                 (transient [])
; ;                 (range (c/nrows img)))
; ;      persistent!
; ;;      (grid-apply pix-val
; ;;                  0 (c/ncols img)
; ;;                  0 (c/nrows img))
; ;;      (partition (c/ncols img))
; ;;      (mapv vec)
; ;      (#(c/make-image % (:type img)))
; ;      )))
