import os._

object LinkUpdater {
  def main(args: Array[String]): Unit = {
    val dataDir = os.pwd / "data"
    os.makeDir.all(dataDir)

    val jsonPath = dataDir / "restaurants.json"

    println("Updating links (fetchRemote = true)...")
    // Input is the source Excel
    // Output is data/restaurants.json
    // Reference is data/restaurants.json (to preserve existing links)

    XlsxConverter.convert(
      "rw_restaurants_2026-01-19.xlsx",
      jsonPath.toString,
      referenceJsonPath = Some(jsonPath.toString),
      fetchRemote = true
    )

    println(s"Updated links in $jsonPath")
  }
}
