import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileInputStream
import java.io.File
import upickle.default.{write => uwrite, read => uread, _}
import os._

object XlsxConverter {
  def convert(xlsxPath: String, jsonPath: String, referenceJsonPath: Option[String] = None, fetchRemote: Boolean = false): Unit = {
    // Load reference data if available
    val referenceMap: Map[String, Restaurant] = referenceJsonPath match {
      case Some(path) if os.exists(os.Path(path, os.pwd)) =>
        try {
          val json = os.read(os.Path(path, os.pwd))
          val list = uread[List[Restaurant]](json)
          // Key by name and address (slug might be safer, but name+address is what we fetch with)
          list.map(r => (r.name + "|" + r.venueAddress) -> r).toMap
        } catch {
          case e: Exception =>
            println(s"Warning: Failed to load reference JSON from $path: ${e.getMessage}")
            Map.empty
        }
      case _ => Map.empty
    }

    val file = new File(xlsxPath)
    if (!file.exists()) {
      println(s"File not found: $xlsxPath")
      return
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

      // Logic:
      // 1. If XLSX has a link, use it.
      // 2. If XLSX empty, check reference Map.
      // 3. If reference empty AND fetchRemote is true, fetch.

      val key = r.name + "|" + r.venueAddress
      val referenceR = referenceMap.get(key)

      val googleLink = if (r.google_maps_url.nonEmpty) r.google_maps_url
      else referenceR.map(_.google_maps_url).find(_.nonEmpty).getOrElse {
        if (fetchRemote) LinkFetcher.fetchGoogleLink(r.name, r.venueAddress).getOrElse("") else ""
      }

      val yelpLink = if (r.yelp_url.nonEmpty) r.yelp_url
      else referenceR.map(_.yelp_url).find(_.nonEmpty).getOrElse {
        if (fetchRemote) LinkFetcher.fetchYelpLink(r.name, r.venueAddress).getOrElse("") else ""
      }

      val finalR = r.copy(
        google_maps_url = googleLink,
        yelp_url = yelpLink
      )

      restaurants += finalR
    }

    workbook.close()

    val json = uwrite(restaurants.toList, indent = 2)
    val out = os.Path(jsonPath, os.pwd)
    os.write.over(out, json, createFolders = true)
    println(s"Converted ${restaurants.size} records to $jsonPath")
  }
}
