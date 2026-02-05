import os._

object Main {
  def main(args: Array[String]): Unit = {
    // Determine output directory. Default to build/dist in the root project.

    val pwd = os.pwd
    // If running from server dir, go up. If running from root, stay.
    // Gradle run usually runs with working dir as project dir (server), but we can configure it.
    // Assuming root is one level up if we are in server.

    val rootDir = if (os.exists(pwd / "server")) pwd else pwd / os.up

    val outDir = rootDir / "build" / "dist"
    os.makeDir.all(outDir)

    val jsonPath = outDir / "restaurants.json"

    // The input XLSX file is expected to be in the root of the repo
    val xlsxPath = rootDir / "rw_restaurants_2026-01-19.xlsx"

    if (!os.exists(xlsxPath)) {
        println(s"Error: Input file not found at $xlsxPath")
        sys.exit(1)
    }

    println(s"Converting XLSX from $xlsxPath to JSON at $jsonPath...")
    XlsxConverter.convert(xlsxPath.toString, jsonPath.toString)

    println("Conversion complete.")
  }
}
