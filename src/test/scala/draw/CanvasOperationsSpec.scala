package draw

import org.scalatest.{Matchers, WordSpec}
import draw.CanvasOperations._
import CanvasOperationsSpec._

class CanvasOperationsSpec extends WordSpec with Matchers {

  "CanvasOperations" should {

    "create a Canvas" in {
      val createCmd = CreateCmd(3, 3)
      updateCanvas(createCmd, None) shouldBe Right(validCanvas)
    }

    "draw a horizontal line on blank Canvas" in {
      val horizontalLineCmd = LineCmd(1, 1, 3, 1)
      updateCanvas(horizontalLineCmd, Some(validCanvas)) shouldBe Right(validCanvasHorizontalLine)
    }

    "draw a horizontal line with reverse ordered coordinates on blank Canvas" in {
      val horizontalLineCmd = LineCmd(3, 1, 1, 1)
      updateCanvas(horizontalLineCmd, Some(validCanvas)) shouldBe Right(validCanvasHorizontalLine)
    }

    "fail to fit horizontal line on blank Canvas" in {
      val horizontalLineCmd = LineCmd(1, 1, 13, 1)
      updateCanvas(horizontalLineCmd, Some(validCanvas)) shouldBe Left(CanvasError(s"${horizontalLineCmd} line will not fit."))
    }

    "draw a vertical line on blank Canvas" in {
      val verticalLineCmd = LineCmd(2, 1, 2, 3)
      updateCanvas(verticalLineCmd, Some(validCanvas)) shouldBe Right(validCanvasVerticalLine)
    }

    "draw a vertical line with reverse ordered coordinates on blank Canvas" in {
      val verticalLineCmd = LineCmd(2, 3, 2, 1)
      updateCanvas(verticalLineCmd, Some(validCanvas)) shouldBe Right(validCanvasVerticalLine)
    }

    "fail to fit vertical line on blank Canvas" in {
      val verticalLineCmd = LineCmd(2, 1, 2, 13)
      updateCanvas(verticalLineCmd, Some(validCanvas)) shouldBe Left(CanvasError(s"${verticalLineCmd} line will not fit."))
    }

    "draw a rectangle on blank Canvas" in {
      val rectangleCmd = RectangleCmd(1, 1, 3, 3)
      updateCanvas(rectangleCmd, Some(validCanvas)) shouldBe Right(validCanvasRectangle)
    }

    "fail to fit rectangle on blank Canvas" in {
      val rectangleCmd = RectangleCmd(1, 1, 9, 9)
      updateCanvas(rectangleCmd, Some(validCanvas)) shouldBe Left(CanvasError(s"${rectangleCmd} rectangle will not fit."))
    }

    "draw a horizontal and vertical line on blank Canvas" in {
      val horizontalLineCmd = LineCmd(1, 1, 3, 1)
      val verticalLineCmd = LineCmd(2, 1, 2, 3)

      val resultCanvas = for {
        canvasHorizontal   <- updateCanvas(horizontalLineCmd, Some(validCanvas))
        canvasHorizAndVert <- updateCanvas(verticalLineCmd, Some(canvasHorizontal))
      } yield canvasHorizAndVert

      resultCanvas shouldBe Right(validCanvasHorizontalAndVertical)
    }

    "flood-fill a blank Canvas" in {
      val fillCmd = FloodFillCmd(1, 1, 'o')
      updateCanvas(fillCmd, Some(validCanvas)) shouldBe Right(validCanvasFloodFillEmpty)
    }

    "flood-fill a populated Canvas" in {
      val verticalLineCmd1 = LineCmd(1, 2, 1, 3)
      val horizontalLineCmd = LineCmd(1, 4, 2, 4)
      val verticalLineCmd2 = LineCmd(4, 3, 4, 4)
      val fillCmd = FloodFillCmd(3, 3, 'o')

      val resultCanvas = for {
        canvas1 <- updateCanvas(verticalLineCmd1, Some(validCanvasLarge))
        canvas2 <- updateCanvas(horizontalLineCmd, Some(canvas1))
        canvas3 <- updateCanvas(verticalLineCmd2, Some(canvas2))
        canvas4 <- updateCanvas(fillCmd, Some(canvas3))
      } yield canvas4

      resultCanvas shouldBe Right(validCanvasFloodFill)
    }

    "flood-fill around a rectangle on Canvas" in {
      val rectangleCmd = RectangleCmd(2, 2, 3, 3)
      val fillCmd = FloodFillCmd(1, 2, 'o')

      val resultCanvas = for {
        canvas1 <- updateCanvas(rectangleCmd, Some(validCanvasLarge))
        canvas2 <- updateCanvas(fillCmd, Some(canvas1))
      } yield canvas2

      resultCanvas shouldBe Right(validCanvasFloodFillBox)
    }

    "will not apply unknown Command" in {
      updateCanvas(UnknownCmd, None) shouldBe Right(Canvas(Vector.empty[Row]))
    }

    "fail when flood-fill start point is on a line" in {
      val rectangleCmd = RectangleCmd(2, 2, 3, 3)
      val fillCmd = FloodFillCmd(2, 2, 'o')

      val resultCanvas = for {
        canvas1 <- updateCanvas(rectangleCmd, Some(validCanvasLarge))
        canvas2 <- updateCanvas(fillCmd, Some(canvas1))
      } yield canvas2

      resultCanvas shouldBe Left(CanvasError(s"FloodFill starting point $fillCmd is on a Line."))
    }

  }

}

object CanvasOperationsSpec {
  case object UnknownCmd extends Command

  def validCanvas = Canvas(
    cells = Vector(
      Vector('-', '-', '-', '-', '-'),
      Vector('|', ' ', ' ', ' ', '|'),
      Vector('|', ' ', ' ', ' ', '|'),
      Vector('|', ' ', ' ', ' ', '|'),
      Vector('-', '-', '-', '-', '-')
    )
  )

  def validCanvasHorizontalLine = Canvas(
    cells = Vector(
      Vector('-', '-', '-', '-', '-'),
      Vector('|', 'X', 'X', 'X', '|'),
      Vector('|', ' ', ' ', ' ', '|'),
      Vector('|', ' ', ' ', ' ', '|'),
      Vector('-', '-', '-', '-', '-')
    )
  )

  def validCanvasVerticalLine = Canvas(
    cells = Vector(
      Vector('-', '-', '-', '-', '-'),
      Vector('|', ' ', 'X', ' ', '|'),
      Vector('|', ' ', 'X', ' ', '|'),
      Vector('|', ' ', 'X', ' ', '|'),
      Vector('-', '-', '-', '-', '-')
    )
  )

  def validCanvasRectangle = Canvas(
    cells = Vector(
      Vector('-', '-', '-', '-', '-'),
      Vector('|', 'X', 'X', 'X', '|'),
      Vector('|', 'X', ' ', 'X', '|'),
      Vector('|', 'X', 'X', 'X', '|'),
      Vector('-', '-', '-', '-', '-')
    )
  )

  def validCanvasHorizontalAndVertical = Canvas(
    cells = Vector(
      Vector('-', '-', '-', '-', '-'),
      Vector('|', 'X', 'X', 'X', '|'),
      Vector('|', ' ', 'X', ' ', '|'),
      Vector('|', ' ', 'X', ' ', '|'),
      Vector('-', '-', '-', '-', '-')
    )
  )

  def validCanvasFloodFillEmpty = Canvas(
    cells = Vector(
      Vector('-', '-', '-', '-', '-'),
      Vector('|', 'o', 'o', 'o', '|'),
      Vector('|', 'o', 'o', 'o', '|'),
      Vector('|', 'o', 'o', 'o', '|'),
      Vector('-', '-', '-', '-', '-')
    )
  )

  def validCanvasLarge = Canvas(
    cells = Vector(
      Vector('-', '-', '-', '-', '-', '-'),
      Vector('|', ' ', ' ', ' ', ' ', '|'),
      Vector('|', ' ', ' ', ' ', ' ', '|'),
      Vector('|', ' ', ' ', ' ', ' ', '|'),
      Vector('|', ' ', ' ', ' ', ' ', '|'),
      Vector('-', '-', '-', '-', '-', '-')
    )
  )

  def validCanvasFloodFill = Canvas(
    cells = Vector(
      Vector('-', '-', '-', '-', '-', '-'),
      Vector('|', 'o', 'o', 'o', 'o', '|'),
      Vector('|', 'X', 'o', 'o', 'o', '|'),
      Vector('|', 'X', 'o', 'o', 'X', '|'),
      Vector('|', 'X', 'X', 'o', 'X', '|'),
      Vector('-', '-', '-', '-', '-', '-')
    )
  )

  def validCanvasFloodFillBox = Canvas(
    cells = Vector(
      Vector('-', '-', '-', '-', '-', '-'),
      Vector('|', 'o', 'o', 'o', 'o', '|'),
      Vector('|', 'o', 'X', 'X', 'o', '|'),
      Vector('|', 'o', 'X', 'X', 'o', '|'),
      Vector('|', 'o', 'o', 'o', 'o', '|'),
      Vector('-', '-', '-', '-', '-', '-')
    )
  )

}