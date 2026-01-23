import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileInputStream
import java.io.File
import upickle.default._

object XlsxConverter {

  // Pure function: Reads XLSX and returns List of Restaurants
  def read(xlsxPath: String): List[Restaurant] = {
    val file = new File(xlsxPath)
    if (!file.exists()) {
      println(s"File not found: $xlsxPath")
      return List.empty
    }
    val fis = new FileInputStream(file)
    val workbook = new XSSFWorkbook(fis)
    val sheet = workbook.getSheetAt(0)

    val rows = sheet.iterator()
    if (rows.hasNext) rows.next() // Skip header

    val restaurants = scala.collection.mutable.ListBuffer[Restaurant]()

    while (rows.hasNext) {
      val row = rows.next()
      // Helper to get string safely
      def getStr(idx: Int): String = {
        val cell = row.getCell(idx)
        if (cell == null) "" else cell.toString.trim
      }

      def getSafeStr(idx: Int): String = {
        val s = getStr(idx)
        if (s.isEmpty || s.equalsIgnoreCase("null")) "" else s
      }

      def getDouble(idx: Int): Double = {
        val cell = row.getCell(idx)
        if (cell == null) 0.0 else try {
          cell.getNumericCellValue
        } catch {
          case _: Exception =>
             try { cell.toString.toDouble } catch { case _: Exception => 0.0 }
        }
      }

      val r = Restaurant(
        name = getStr(0),
        pdf_url = getSafeStr(1),
        slug = getStr(2),
        url = getStr(3),
        venueAddress = getStr(4),
        latitude = getDouble(5),
        longitude = getDouble(6),
        summary = getStr(7),
        website = getSafeStr(8),
        collections = getStr(9),
        borough = getStr(10),
        neighborhood = getStr(11),
        primaryCategory = getStr(12),
        primaryLocation = getStr(13),
        restaurantInclusionWeek = getStr(14),
        image_url = getSafeStr(15),
        partnerId = ujson.Num(getDouble(16)),
        meal_type = getStr(17),
        tags = getStr(18),
        resy_url = getSafeStr(19),
        google_maps_url = getSafeStr(20),
        yelp_url = getSafeStr(21)
      )

      restaurants += r
    }

    workbook.close()
    restaurants.toList
  }
}
