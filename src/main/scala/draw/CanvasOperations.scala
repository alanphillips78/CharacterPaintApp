package draw

import scala.annotation.tailrec
import scala.collection.immutable.Queue

case class Canvas(cells: Vector[Vector[Char]]) {

  def hLineFits(lineCmd: LineCmd): Boolean =
    lineCmd.isHorizontal &&
      (lineCmd.y1 > 0 && lineCmd.y1 < cells.size - 1) &&       // line starting point on y-axis fits
      (lineCmd.x1 > 0 && lineCmd.x2 < cells.head.size - 1)     // full horizontal line fits

  def vLineFits(lineCmd: LineCmd): Boolean =
    lineCmd.isVertical &&
      (lineCmd.x1 > 0 && lineCmd.x1 < cells.head.size - 1) &&  // line starting point on x-axis fits
      (lineCmd.y1 > 0 && lineCmd.y2 < cells.size - 1)          // full vertical line fits

  def rectangleFits(hLineCmd: LineCmd, vLineCmd: LineCmd): Boolean =
    hLineFits(hLineCmd) && vLineFits(vLineCmd)

}

object CanvasOperations {

  type Row = Vector[Char]
  type Matrix = Vector[Row]

  def updateCanvas(cmd: Command, canvas: Option[Canvas]): Either[CanvasError, Canvas] = cmd match {
    case createCmd: CreateCmd         => createCanvas(createCmd)
    case lineCmd: LineCmd             => lineOnCanvas(lineCmd, canvas)
    case rectangleCmd: RectangleCmd   => rectangleOnCanvas(rectangleCmd, canvas)
    case floodFillCmd: FloodFillCmd   => floodFillCanvas(floodFillCmd, canvas)
    case _ => Right(Canvas(Vector.empty[Row]))
  }

  private def createCanvas(createCmd: CreateCmd): Either[CanvasError, Canvas] = {
    val borderRow: Row = Vector.fill(createCmd.width + 2)('-')
    val innerRow: Row = ('|' +: Vector.fill(createCmd.width)(' ')) :+ ('|')
    Right(
      Canvas(
        cells = (borderRow +: Vector.fill(createCmd.height)(innerRow)) :+ borderRow
      )
    )
  }

  private def lineOnCanvas(lineCmd: LineCmd, canvasOpt: Option[Canvas]): Either[CanvasError, Canvas] = {
    val eCanvas = canvasOpt.toRight(CanvasError(s"No Canvas presented to draw line on."))
    eCanvas.flatMap(canvas =>
      if (lineCmd.isHorizontal)
        plotHorizontalLine(lineCmd, canvas)
      else if (lineCmd.isVertical)
        plotVerticalLine(lineCmd, canvas)
      else
        Left(CanvasError(s"${lineCmd.orientation} lines not supported."))
    )
  }

  private def rectangleOnCanvas(rectangleCmd: RectangleCmd, canvasOpt: Option[Canvas]): Either[CanvasError, Canvas] = {
    val eCanvas = canvasOpt.toRight(CanvasError(s"No Canvas presented to draw line on."))

    eCanvas.flatMap { canvas =>
      val hLineTop = LineCmd(rectangleCmd.x1, rectangleCmd.y1, rectangleCmd.x2, rectangleCmd.y1)
      val hLineBottom = LineCmd(rectangleCmd.x1, rectangleCmd.y2, rectangleCmd.x2, rectangleCmd.y2)
      val vLineLeft = LineCmd(rectangleCmd.x1, rectangleCmd.y1, rectangleCmd.x1, rectangleCmd.y2)
      val vLineRight = LineCmd(rectangleCmd.x2, rectangleCmd.y1, rectangleCmd.x2, rectangleCmd.y2)

      if (canvas.rectangleFits(hLineTop, vLineLeft)) {
        for {
          canvasLineTop  <- lineOnCanvas(hLineTop, Some(canvas))
          canvasLineBot  <- lineOnCanvas(hLineBottom, Some(canvasLineTop))
          canvasLineLeft <- lineOnCanvas(vLineLeft, Some(canvasLineBot))
          canvasFullRect <- lineOnCanvas(vLineRight, Some(canvasLineLeft))
        } yield canvasFullRect
      }
      else
        Left(CanvasError(s"$rectangleCmd rectangle will not fit."))
    }
  }

  private def floodFillCanvas(floodFillCmd: FloodFillCmd, canvasOpt: Option[Canvas], linePixel: Char = 'X'): Either[CanvasError, Canvas] = {
    @tailrec
    def fillCanvas(cellsToFill: Queue[FloodFillCmd], canvas: Canvas): Canvas = {
      if (cellsToFill.isEmpty)
        canvas
      else {
        val (fillCmd, cellsRemaining) = cellsToFill.dequeue
        val (startIndex, endIndex) = makeIndexBoundaries(fillCmd, canvas, linePixel)
        val len = endIndex - startIndex
        val updatedRow = fillLinePortion(canvas, fillCmd, startIndex, len)                                        // Perform the fill on selected line portion
        val updatedCanvas = Canvas(canvas.cells.updated(fillCmd.y, updatedRow))                                   // Update Canvas with updated line

        // This could be refactored for sure, make re-usable
        val cellsAbove =
          if (fillCmd.y - 1 > 0) {                                                                                // Find and enqueue cells above current line that can be filled
            val cellsAboveFilledRow: Vector[(Char, Int)] = canvas.cells(fillCmd.y - 1).slice(startIndex, endIndex).zip(startIndex to endIndex)   // Zip to give tuple of (cell value, cell index) so that fillCmd can be created and added to Queue of cells to fill
            cellsAboveFilledRow.foldLeft(cellsRemaining)(                                                         // Fold over above cells, accumulating cells that need to be filled
              (accQ, next) => {                                                                                   // Fold function
                val toFillAbove = fillCmd.copy(x = next._2, y = fillCmd.y - 1)                                    // Create FloodFillCmd called toFillAbove, to be enqueued to be filled if valid
                if (next._1 != linePixel && next._1 != toFillAbove.colour && !accQ.contains(toFillAbove))         // Check that cell above is not a line pixel, is not already filled, not already enqueued
                  accQ.enqueue(toFillAbove)                                                                       // Enqueue if passes and return updated queue
                else accQ                                                                                         // if not passes, return existing queue
              }
            )
          } else cellsRemaining

        val cellsAboveAndBelow =
          if (fillCmd.y + 1 < canvas.cells.size - 1) {                                                            // Same as cellsAbove creation, but working downwards
            val cellsBelowFilledRow: Vector[(Char, Int)] = canvas.cells(fillCmd.y + 1).slice(startIndex, endIndex).zip(startIndex to endIndex)
            cellsBelowFilledRow.foldLeft(cellsAbove)(
              (accQ, next) => {
                val toFillBelow = fillCmd.copy(x = next._2, y = fillCmd.y + 1)
                if (next._1 != linePixel && next._1 != toFillBelow.colour && !accQ.contains(toFillBelow))
                  accQ.enqueue(toFillBelow)
                else accQ
              }
            )
          } else cellsAbove

        fillCanvas(cellsAboveAndBelow, updatedCanvas)
      }
    }

    val eCanvas = canvasOpt.toRight(CanvasError(s"No Canvas presented to draw line on."))
    eCanvas.flatMap { canvas =>
      if (canvas.cells(floodFillCmd.y)(floodFillCmd.x) != linePixel) {
        val filledCanvas = fillCanvas(Queue(floodFillCmd), canvas)
        Right(filledCanvas)
      } else
        Left(CanvasError(s"FloodFill starting point $floodFillCmd is on a Line."))
    }
  }

  private def makeIndexBoundaries(fillCmd: FloodFillCmd, canvas: Canvas, linePixel: Char): (Int, Int) = {
    val linePixelPre = canvas.cells(fillCmd.y).lastIndexOf(linePixel, fillCmd.x)
    val linePixelPost = canvas.cells(fillCmd.y).indexOf(linePixel, fillCmd.x + 1)
    val startIndex = if (linePixelPre != -1) linePixelPre + 1 else 1                                          // Start index of horizontal line to fill
    val endIndex = if (linePixelPost != -1) linePixelPost else canvas.cells(fillCmd.y).size - 1               // End index of horizontal line to fill
    (startIndex, endIndex)
  }

  private def fillLinePortion(canvas: Canvas, fillCmd: FloodFillCmd, startIndex: Int, len: Int) =
    canvas.cells(fillCmd.y).patch(startIndex, Vector.fill[Char](len)(fillCmd.colour), len)

  private def plotVerticalLine(lineCmd: LineCmd, canvas: Canvas, pixel: Char = 'X'): Either[CanvasError, Canvas] = {
    val lineNorm = lineCmd.normalize
    if (canvas.vLineFits(lineNorm)) {
      val len = (lineNorm.y2 - lineNorm.y1) + 1
      val updated = canvas.cells.slice(lineNorm.y1, lineNorm.y2 + 1).map(
        row => row.updated(lineNorm.x1, pixel)
      )
      Right(
        Canvas(
          cells = canvas.cells.patch(lineNorm.y1, updated, len)
        )
      )
    }
    else
      Left(CanvasError(s"${lineCmd} line will not fit."))
  }

  private def plotHorizontalLine(lineCmd: LineCmd, canvas: Canvas, pixel: Char = 'X'): Either[CanvasError, Canvas] = {
    val lineNorm = lineCmd.normalize
    if (canvas.hLineFits(lineNorm)) {
      val len = (lineNorm.x2 - lineNorm.x1) + 1
      val updatedRow = canvas.cells(lineNorm.y1).patch(lineNorm.x1, Vector.fill[Char](len)(pixel), len)
      Right(
        Canvas(
          cells = canvas.cells.updated(lineNorm.y1, updatedRow)
        )
      )
    }
    else
      Left(CanvasError(s"${lineCmd} line will not fit."))
  }

}
