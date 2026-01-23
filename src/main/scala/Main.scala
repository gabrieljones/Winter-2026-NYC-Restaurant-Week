import os._

object Main {
  def main(args: Array[String]): Unit = {
    val outDir = os.pwd / "build" / "dist"
    os.makeDir.all(outDir)

    val jsonPath = outDir / "restaurants.json"
    val htmlPath = outDir / "map.html"

    println("Converting XLSX to JSON...")
    XlsxConverter.convert("rw_restaurants_2026-01-19.xlsx", jsonPath.toString)

    println("Building Map...")
    MapBuilder.main(Array(jsonPath.toString, htmlPath.toString))

    // Copy to index.html for convenience
    os.copy.over(htmlPath, outDir / "index.html")
    println(s"Copied map.html to index.html in $outDir")
  }
}
