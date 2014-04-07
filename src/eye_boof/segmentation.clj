(ns eye-boof.segmentation
 "Functions to locate objects and boundaries (lines, curves, etc.) on the image." 
  (:require
    [eye-boof.segmentation.otsu :as otsu]
    [eye-boof
     [core :refer [width height new-image pixel* set-pixel!*]]
     [image-statistics :refer [histogram]]])
  (:import 
    [boofcv.struct.image ImageUInt8]
    [boofcv.alg.filter.binary ThresholdImageOps]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* true)

(defmulti threshold
  "Converts a grayscale Image into a binary one by setting the pixels' values to
  1 or 0 accordingly a threshold value.
  Available threshold algorithms: 
  
  :default th-val
  Sets to 0 all pixels below th-val and to 1 all pixels above.
  
  :adative-square radius bias
  Thresholds the image using an adaptive threshold that is computed using a local
  square region centered on each pixel. The threshold value is equal to the average
  value of the surrounding pixels plus the bias.  
  
  :global-otsu 
  Computes a global threshold value using Otsu's method and applies the :default 
  operation on the image."
  (fn 
    ([img alg] alg)
    ([img alg v1] alg)
    ([img alg v1 v2] alg)
    ([img alg v1 v2 & more] alg)))

(defmethod threshold :default simple
  [^ImageUInt8 img _ ^long th-val]
  (let [^ImageUInt8 output (new-image (width img) (height img))]
    (ThresholdImageOps/threshold img output th-val false)))

(defmethod threshold :adaptive-square adaptive-square 
  [^ImageUInt8 img _ ^long radius ^long bias]
  (ThresholdImageOps/adaptiveSquare img nil radius bias false nil nil))

(defmethod threshold :global-otsu global-otsu 
  [^ImageUInt8 img _]
  (->> (otsu/compute-threshold (histogram img))
       (threshold img :default)))

(defmethod threshold :local-otsu local-otsu 
  [^ImageUInt8 img _ ^long radius]
  (let [^ImageUInt8 out (new-image (width img) (height img))
        hist (int-array 256 0)]
    (dotimes [j (- (height img) (* 2 radius))]
      (dotimes [i (- (width img) (* 2 radius))]
        (let [x (+ i radius)
              y (+ j radius)
              xy-val (.unsafe_get img x y)]
          ;; Local histogram calculation.
          (dotimes [cj (inc radius)]
            (dotimes [ci (inc radius)]
              (let [wx (- (+ x ci) radius)
                    wy (- (+ y cj) radius)
                    gray-val (pixel* img wx wy)]
                (->> (aget hist gray-val)
                     inc
                     (aset hist gray-val)))))
          (set-pixel!* out x y
           (if (< xy-val 100 #_(otsu/compute-threshold (into [] hist))) 0 1)))))
    out))