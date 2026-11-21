package es.olaverde.backend

import es.olaverde.core.osm.OsmGraphBuilder
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.File

/**
 * Uso:
 *   serve                 arranca la API (por defecto)
 *   fetch-osm salida.json descarga de Overpass las calles, semáforos y radares de Valladolid
 *
 * Variables: DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD, PORT, OSM_FILE (JSON de Overpass a cargar al arrancar).
 */
fun main(args: Array<String>) {
    when (args.firstOrNull() ?: "serve") {
        "fetch-osm" -> {
            val out = File(args.getOrNull(1) ?: "valladolid-osm.json")
            out.writeText(Osm.fetch(Osm.VALLADOLID))
            println("Guardado ${out.path} (${out.length() / 1024} KB)")
        }
        "serve" -> serve()
        else -> error("Comando desconocido: ${args.first()}")
    }
}

private fun serve() {
    val env = System.getenv()
    val db = Db(
        env["DATABASE_URL"] ?: "jdbc:h2:./data/olaverde;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        env["DATABASE_USER"],
        env["DATABASE_PASSWORD"],
    )
    val services = Services(db)
    env["OSM_FILE"]?.let { path ->
        val (nodes, ways) = Osm.parse(File(path).readText())
        val area = OsmGraphBuilder.build(nodes, ways)
        db.replaceOsm(area.signals, area.cameras)
        services.area = area
        println("OSM: ${area.signals.size} semáforos, ${area.cameras.size} radares, ${area.graph.nodes.size} nodos")
    }
    embeddedServer(Netty, port = env["PORT"]?.toIntOrNull() ?: 8080) { olaVerde(services) }.start(wait = true)
}
