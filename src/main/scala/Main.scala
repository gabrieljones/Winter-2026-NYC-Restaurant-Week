import os._
import upickle.default.{write => uwrite}

object Main {
  def main(args: Array[String]): Unit = {
    val outDir = os.pwd / "build" / "dist"
    os.makeDir.all(outDir)

    val jsonPath = outDir / "restaurants.json"
    val htmlPath = outDir / "map.html"
    val cachePath = (os.pwd / "data" / "restaurants.json").toString
    val sourceXlsx = "rw_restaurants_2026-01-19.xlsx"

    println("Reading source data...")
    val rawRestaurants = XlsxConverter.read(sourceXlsx)

    println("Loading link cache (OFFLINE MODE)...")
    val cachedRestaurants = LinkCache.load(cachePath)

    println("Merging cache...")
    val finalRestaurants = LinkCache.apply(rawRestaurants, cachedRestaurants)

    println(s"Writing JSON to $jsonPath...")
    val jsonContent = uwrite(finalRestaurants, indent = 2)
    os.write.over(jsonPath, jsonContent, createFolders = true)

    println("Building Map...")
    // MapBuilder expects paths as strings, reads the JSON file we just wrote
    MapBuilder.main(Array(jsonPath.toString, htmlPath.toString))

    // Copy to index.html for convenience
    os.copy.over(htmlPath, outDir / "index.html")
    println(s"Copied map.html to index.html in $outDir")
  }
}
