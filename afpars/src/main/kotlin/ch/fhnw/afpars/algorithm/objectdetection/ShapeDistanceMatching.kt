package ch.fhnw.afpars.algorithm.objectdetection

import ch.fhnw.afpars.model.AFImage
import ch.fhnw.afpars.util.approxPolyDP
import ch.fhnw.afpars.util.zeros
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.util.*


/**
 * Created by cansik on 22.12.16.
 */
class ShapeDistanceMatching : IObjectDetectionAlgorithm {
    override val name: String
        get() = "Shape Distance Matching"

    override fun run(image: AFImage, history: MutableList<AFImage>): AFImage {
        val areas = MatOfRect(image.attributes[CascadeClassifierDetector.CASCADE_ATTRIBUT])

        // load default door contour
        val doorTemplate = Imgcodecs.imread("data/door.png")
        val thresholdDoor = doorTemplate.zeros()

        Imgproc.cvtColor(doorTemplate, thresholdDoor, Imgproc.COLOR_BGR2GRAY)
        Imgproc.threshold(thresholdDoor, thresholdDoor, 128.0, 255.0, Imgproc.THRESH_BINARY_INV)

        val doorContours = ArrayList<MatOfPoint>()
        Imgproc.findContours(thresholdDoor, doorContours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        // approx contour
        val doorContour = doorContours[0].approxPolyDP()
        val doorArea = Imgproc.contourArea(doorContour)

        Imgproc.drawContours(doorTemplate, listOf(doorContour), -1, Scalar(0.0, 0.0, 255.0))
        history.add(AFImage(doorTemplate, "Door Template"))

        val doors = mutableListOf<Pair<Double, Mat>>()

        // process all segments
        loop@ for (rect in areas.toArray().sortedByDescending(Rect::area).take(100)) {
            val part = Mat(image.image, rect)
            val thresholdImage = part.zeros()

            Imgproc.cvtColor(part, thresholdImage, Imgproc.COLOR_BGR2GRAY)
            Imgproc.threshold(thresholdImage, thresholdImage, 128.0, 255.0, Imgproc.THRESH_BINARY_INV)

            val allContours = ArrayList<MatOfPoint>()
            Imgproc.findContours(thresholdImage, allContours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            // filter all small contours
            val contours = allContours.filter { Imgproc.contourArea(it) > 0.2 * doorArea }.toList()

            // skip all parts without contours
            if (contours.size == 0)
                continue@loop

            // skip all which have more then one contour (not recognisable)
            if (contours.size > 1)
                continue@loop

            // go through every contour and select the best matching
            var bestCont = contours[0]
            var bestMatch = Double.MAX_VALUE

            for (cont in contours) {
                val approx = cont.approxPolyDP()
                val match = Imgproc.matchShapes(doorContour, approx, Imgproc.CV_CONTOURS_MATCH_I1, 0.0)

                if (match < bestMatch) {
                    bestCont = approx
                    bestMatch = match
                }
            }

            Imgproc.drawContours(part, contours, -1, Scalar(0.0, 0.0, 255.0))

            // draw best matching contour blue
            Imgproc.drawContours(part, listOf(bestCont), -1, Scalar(255.0, 0.0, 0.0))
            doors.add(Pair(bestMatch, part))
        }

        doors.sortByDescending { it.first }

        doors.mapTo(history) { AFImage(it.second, "Match: ${it.first}") }

        return image
    }
}