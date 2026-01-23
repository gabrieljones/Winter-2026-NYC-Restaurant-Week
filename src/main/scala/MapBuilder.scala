import upickle.default.{read => uread, write => uwrite, _}
import os._
import scalatags.Text.all._

case class Restaurant(
    name: String,
    pdf_url: String,
    slug: String,
    url: String,
    venueAddress: String,
    latitude: Double,
    longitude: Double,
    summary: String,
    website: String,
    collections: String,
    borough: String,
    neighborhood: String,
    primaryCategory: String,
    primaryLocation: String,
    restaurantInclusionWeek: String,
    image_url: String,
    partnerId: ujson.Value,
    meal_type: String,
    tags: String
) derives ReadWriter

object MapBuilder {
  def main(args: Array[String]): Unit = {
    val jsonPath = os.pwd / "restaurants.json"
    if (!os.exists(jsonPath)) {
      println(s"Error: $jsonPath does not exist.")
      sys.exit(1)
    }

    val jsonContent = os.read(jsonPath)
    val restaurants = uread[List[Restaurant]](jsonContent)

    val htmlContent = generateHtml(restaurants)
    val outputPath = os.pwd / "output_map.html"
    os.write.over(outputPath, htmlContent)
    println(s"Map generated at $outputPath")
  }

  def generateHtml(restaurants: List[Restaurant]): String = {
    // 1. Analyze data for groups and tags
    // Some entries might have empty strings or nulls, be careful.
    val allMealTypes = restaurants.flatMap(r => Option(r.meal_type).getOrElse("").split(";").map(_.trim).filter(_.nonEmpty)).toSet.toList.sorted
    val allTags = restaurants.flatMap(r => Option(r.tags).getOrElse("").split(";").map(_.trim).filter(_.nonEmpty)).toSet.toList.sorted

    // Group meal types by price for the layer control
    val priceGroups = allMealTypes.groupBy { mt =>
      val pricePart = mt.split(" ").find(_.startsWith("$"))
      pricePart.getOrElse("Other")
    }

    // 2. Generate Javascript Parts
    val sb = new StringBuilder

    // Map Init
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

    // Feature Groups creation
    val groupVarNames = allMealTypes.zipWithIndex.map { case (mt, idx) =>
      mt -> s"group_$idx"
    }.toMap

    groupVarNames.foreach { case (mt, varName) =>
      sb.append(s"""var $varName = L.featureGroup({}).addTo(map);""" + "\n")
    }

    // Markers creation
    restaurants.zipWithIndex.foreach { case (r, idx) =>
      val markerVar = s"marker_$idx"
      val rTags = Option(r.tags).getOrElse("").split(";").map(_.trim).filter(_.nonEmpty).toList
      val rMealTypes = Option(r.meal_type).getOrElse("").split(";").map(_.trim).filter(_.nonEmpty).toList
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

      // Popup Content
      val imagePart: Modifier = if (r.image_url != null && r.image_url.nonEmpty) img(src := r.image_url, width := "300") else ""

      val pdfPart: Modifier = if (r.pdf_url != null && r.pdf_url.nonEmpty) span(a(href := r.pdf_url, target := "_blank", attr("style") := "font-size: 15px", "PDF"), br) else ""

      val popupHtml = div(
        id := s"html_popup_$idx",
        attr("style") := "width: 100.0%; height: 100.0%;",
        a(href := Option(r.url).getOrElse("#"), target := "_blank", attr("style") := "font-size: 20px", Option(r.name).getOrElse("Unknown")), br,
        a(href := Option(r.url).getOrElse("#"), target := "_blank", imagePart),
        a(href := Option(r.website).getOrElse("#"), target := "_blank", attr("style") := "font-size: 15px", "Website"), br,
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
           |$markerVar.bindTooltip(`<div>${Option(r.name).getOrElse("Unknown")}</div>`, {"sticky": true});
           |""".stripMargin)

      // Add to groups
      rMealTypes.foreach { mt =>
        groupVarNames.get(mt).foreach { groupVar =>
          sb.append(s"$groupVar.addLayer($markerVar);\n")
        }
      }
    }

    // Layer Control
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

    // Tag Filter Button
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


    // HTML Template
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
