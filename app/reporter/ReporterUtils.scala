package reporter

import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import scala.collection.mutable.ArrayBuffer

object ReporterUtils {

  def convertToReviewMatrix(reports: Map[String, SummaryReport]): (String, String, String) = {
    val matrix = reports.keys.foldRight(List[List[Int]]())( (rowName, existing)
                  => generateRow(reports.keys.toList, reports.get(rowName)) :: existing)
    val normalizedMatrix = generateNormalized(matrix)
    (compact(render(reports.keys)),
      compact(render(matrix)),
      compact(render(normalizedMatrix)))
  }

  private def generateNormalized(matrix: List[List[Int]]): List[List[Int]] = {
    val newMatrix = ArrayBuffer[List[Int]]()
    for (rowIndex <- 0.to(matrix.size-1)) {
      val newRow = ArrayBuffer[Int]()
      for (columnIndex <- 0.to(matrix.size-1)) {
        // y(i, j) and y(j, i) = x(i, j) + x(j, i)
        val newValue = matrix(rowIndex)(columnIndex) + matrix(columnIndex)(rowIndex)
        newRow += newValue
      }
     newMatrix += newRow.toList
    }
    newMatrix.toList
  }

  private def generateRow(columnNames: List[String], report: Option[SummaryReport]): List[Int] = {
    columnNames.foldRight(List[Int]())( (columnName, existing) => generateElement(columnName, report) :: existing)
  }

  private def generateElement(columnName: String, report: Option[SummaryReport]): Int = {
    if(report.isEmpty) 0 else report.get.sentAllReviewsPerUsername.getOrElse(columnName, 0)
  }
}
