import upickle.default.{read => uread, write => uwrite, _}
import os._
import scalatags.Text.all._
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object MapBuilder {
  def main(args: Array[String]): Unit = {
    val (jsonInput, htmlOutput) = if (args.length >= 2) (args(0), args(1)) else ("build/restaurants.json", "build/output_map.html")

    if (jsonInput.startsWith("build/")) os.makeDir.all(os.pwd / "build")

    if (!os.exists(os.Path(jsonInput, os.pwd))) {
      println(s"JSON file not found: $jsonInput")
      sys.exit(1)
    }

    val jsonContent = os.read(os.Path(jsonInput, os.pwd))
    val restaurants = uread[List[Restaurant]](jsonContent)

    val htmlContent = generateHtml(restaurants)
    os.write.over(os.Path(htmlOutput, os.pwd), htmlContent, createFolders = true)
    println(s"Map generated at $htmlOutput")
  }

  def generateHtml(restaurants: List[Restaurant]): String = {
    val allMealTypes = restaurants.flatMap(r => r.meal_type.split(";").map(_.trim).filter(_.nonEmpty)).toSet.toList.sorted
    val allTags = restaurants.flatMap(r => r.tags.split(";").map(_.trim).filter(_.nonEmpty)).toSet.toList.sorted

    val priceGroups = allMealTypes.groupBy { mt =>
      val pricePart = mt.split(" ").find(_.startsWith("$"))
      pricePart.getOrElse("Other")
    }

    val sb = new StringBuilder

    sb.append(
      """
        |var map = L.map("map", {
        |    center: [40.7396, -73.9739],
        |    crs: L.CRS.EPSG3857,
        |    zoom: 12,
        |    zoomControl: true,
        |    preferCanvas: false,
        |});
        |
        |L.tileLayer(
        |    "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        |    {
        |        "minZoom": 0,
        |        "maxZoom": 19,
        |        "maxNativeZoom": 19,
        |        "noWrap": false,
        |        "attribution": "&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors",
        |        "subdomains": "abc",
        |        "detectRetina": false,
        |        "tms": false,
        |        "opacity": 1,
        |    }
        |).addTo(map);
        |""".stripMargin)

    val groupVarNames = allMealTypes.zipWithIndex.map { case (mt, idx) =>
      mt -> s"group_$idx"
    }.toMap

    groupVarNames.foreach { case (mt, varName) =>
      sb.append(s"""var $varName = L.featureGroup({}).addTo(map);""" + "\n")
    }

    restaurants.zipWithIndex.foreach { case (r, idx) =>
      val markerVar = s"marker_$idx"
      val rTags = r.tags.split(";").map(_.trim).filter(_.nonEmpty).toList
      val rMealTypes = r.meal_type.split(";").map(_.trim).filter(_.nonEmpty).toList
      val tagsJson = uwrite(rTags)

      sb.append(
        s"""
           |var $markerVar = L.marker(
           |    [${r.latitude}, ${r.longitude}],
           |    {
           |        "maxWidth": 350,
           |        "lazy": true,
           |        "tags": $tagsJson,
           |    }
           |);
           |""".stripMargin)

      def encode(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8.toString)

      val googleLink = s"https://www.google.com/maps/search/?api=1&query=${encode(r.name + " " + r.venueAddress)}"
      val yelpLink = s"https://www.yelp.com/search?find_desc=${encode(r.name)}&find_loc=${encode(r.venueAddress)}"
      val resyLink = s"https://resy.com/cities/ny?query=${encode(r.name)}"

      val imagePart: Modifier = if (r.image_url.nonEmpty) img(src := r.image_url, width := "300") else ""

      val pdfPart: Modifier = if (r.pdf_url.nonEmpty) span(a(href := r.pdf_url, target := "_blank", attr("style") := "font-size: 15px", "PDF"), br) else ""

      val websiteUrl = if (r.website.nonEmpty) r.website else "#"

      val popupHtml = div(
        id := s"html_popup_$idx",
        attr("style") := "width: 100.0%; height: 100.0%;",
        a(href := r.url, target := "_blank", attr("style") := "font-size: 20px", r.name), br,
        a(href := r.url, target := "_blank", imagePart),
        div(
            a(href := websiteUrl, target := "_blank", attr("style") := "font-size: 15px", "Website"), " | ",
            a(href := resyLink, target := "_blank", attr("style") := "font-size: 15px", "Rezy"), " | ",
            a(href := googleLink, target := "_blank", attr("style") := "font-size: 15px", "Google Maps"), " | ",
            a(href := yelpLink, target := "_blank", attr("style") := "font-size: 15px", "Yelp")
        ),
        br,
        pdfPart,
        div(
          attr("style") := "display: flex;",
          div(
            attr("style") := "padding-right: 4em",
            ul(attr("style") := "list-style: none; padding: 0;", rTags.map(t => li(t)))
          ),
          div(
            ul(attr("style") := "list-style: none; padding: 0;", rMealTypes.map(m => li(m)))
          )
        )
      ).render

      val popupJsString = uwrite(popupHtml)

      sb.append(
        s"""
           |$markerVar.bindPopup($popupJsString, {"maxWidth": "100%"});
           |$markerVar.bindTooltip(`<div>${r.name}</div>`, {"sticky": true});
           |""".stripMargin)

      rMealTypes.foreach { mt =>
        groupVarNames.get(mt).foreach { groupVar =>
          sb.append(s"$groupVar.addLayer($markerVar);\n")
        }
      }
    }

    val layerTreeChildren = priceGroups.toList.sortBy(_._1).map { case (price, types) =>
      val typeChildren = types.sorted.map { mt =>
        val varName = groupVarNames(mt)
        s"""{ "label": "$mt", "layer": $varName }"""
      }.mkString(",\n")

      s"""
         |{
         |  "label": " $price",
         |  "selectAllCheckbox": true,
         |  "children": [
         |    $typeChildren
         |  ]
         |}
         |""".stripMargin
    }.mkString(",\n")

    sb.append(
      s"""
         |L.control.layers.tree(
         |    null,
         |    {
         |        "label": " Select All",
         |        "selectAllCheckbox": "Un/select all",
         |        "children": [
         |            $layerTreeChildren
         |        ]
         |    },
         |    {
         |        "collapsed": false,
         |        "closedSymbol": "+",
         |        "openedSymbol": "-",
         |        "spaceSymbol": "&nbsp;",
         |        "selectorBack": false,
         |        "namedToggle": false,
         |        "collapseAll": "",
         |        "expandAll": "",
         |        "labelIsSelector": "both",
         |    }
         |).addTo(map);
         |""".stripMargin)

    val allTagsJson = uwrite(allTags)
    sb.append(
      s"""
         |L.control.tagFilterButton({
         |    "data": $allTagsJson,
         |    "icon": "fa-filter",
         |    "clearText": "clear",
         |    "filterOnEveryClick": true,
         |    "openPopupOnHover": true,
         |    "collapsed": false,
         |}).addTo(map);
         |""".stripMargin)

    s"""<!DOCTYPE html>
       |<html>
       |<head>
       |    <meta http-equiv="content-type" content="text/html; charset=UTF-8" />
       |    <script>
       |        L_NO_TOUCH = false;
       |        L_DISABLE_3D = false;
       |    </script>
       |    <style>html, body {width: 100%;height: 100%;margin: 0;padding: 0;}</style>
       |    <style>#map {position:absolute;top:0;bottom:0;right:0;left:0;}</style>
       |    <script src="https://cdn.jsdelivr.net/npm/leaflet@1.9.3/dist/leaflet.js"></script>
       |    <script src="https://code.jquery.com/jquery-3.7.1.min.js"></script>
       |    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.2.2/dist/js/bootstrap.bundle.min.js"></script>
       |    <script src="https://cdnjs.cloudflare.com/ajax/libs/Leaflet.awesome-markers/2.0.2/leaflet.awesome-markers.js"></script>
       |    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/leaflet@1.9.3/dist/leaflet.css"/>
       |    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.2.2/dist/css/bootstrap.min.css"/>
       |    <link rel="stylesheet" href="https://netdna.bootstrapcdn.com/bootstrap/3.0.0/css/bootstrap-glyphicons.css"/>
       |    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/@fortawesome/fontawesome-free@6.2.0/css/all.min.css"/>
       |    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/Leaflet.awesome-markers/2.0.2/leaflet.awesome-markers.css"/>
       |    <link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/python-visualization/folium/folium/templates/leaflet.awesome.rotate.min.css"/>
       |    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
       |
       |    <script src="https://cdn.jsdelivr.net/npm/leaflet.control.layers.tree@1.1.0/L.Control.Layers.Tree.min.js"></script>
       |    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/leaflet.control.layers.tree@1.1.0/L.Control.Layers.Tree.min.css"/>
       |    <script src="https://cdn.jsdelivr.net/npm/leaflet-tag-filter-button/src/leaflet-tag-filter-button.js"></script>
       |    <script src="https://cdn.jsdelivr.net/npm/leaflet-easybutton@2/src/easy-button.js"></script>
       |    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/leaflet-tag-filter-button/src/leaflet-tag-filter-button.css"/>
       |    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/leaflet-easybutton@2/src/easy-button.css"/>
       |    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/css-ripple-effect@1.0.5/dist/ripple.min.css"/>
       |
       |    <style>
       |        .easy-button-button { display: block !important; }
       |        .tag-filter-tags-container { left: 30px; }
       |        .leaflet-bar .tag-filter-tags-container ul { width: 180px; max-height: 350px; }
       |    </style>
       |</head>
       |<body>
       |    <div class="folium-map" id="map"></div>
       |</body>
       |<script>
       |${sb.toString()}
       |</script>
       |</html>
       |""".stripMargin
  }
}
