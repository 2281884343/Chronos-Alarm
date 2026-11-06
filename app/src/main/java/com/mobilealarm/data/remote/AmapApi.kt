package com.mobilealarm.data.remote

import com.mobilealarm.domain.model.CommuteMode
import com.mobilealarm.domain.model.CommutePlace
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.URLEncoder

data class AmapRoute(
    val minutes: Int,
    val distanceMeters: Int,
    val trafficLights: Int = 0,
    val summary: String = "",
)

private data class RawRoute(
    val seconds: Int,
    val distanceMeters: Int,
    val trafficLights: Int = 0,
    val detail: String = "",
)

/**
 * 高德 Web 服务 API。使用用户自己的 Key，避免把任何配额/密钥打进安装包。
 * 路径规划返回的 duration 是含当前路况的预计耗时，单位秒。
 */
class AmapApi {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun searchPlaces(
        key: String,
        query: String,
        city: String?,
    ): List<CommutePlace> {
        if (key.isBlank()) throw IOException("请先在通勤页填写高德 Web 服务 Key")
        if (query.isBlank()) return emptyList()
        val url = buildString {
            append("https://restapi.amap.com/v3/place/text?key=")
            append(enc(key))
            append("&keywords=").append(enc(query))
            append("&offset=10&page=1&extensions=base")
            if (!city.isNullOrBlank()) {
                append("&city=").append(enc(city))
                append("&citylimit=true")
            }
        }
        val root = request(url)
        val pois = root["pois"] as? JsonArray ?: return emptyList()
        return pois.mapNotNull { element ->
            val poi = element as? JsonObject ?: return@mapNotNull null
            val location = poi.string("location") ?: return@mapNotNull null
            val parts = location.split(',')
            if (parts.size != 2) return@mapNotNull null
            val lon = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            val name = poi.string("name") ?: return@mapNotNull null
            val address = poi.string("address")
                ?.takeIf { it.isNotBlank() && it != "[]" }
                ?: poi.string("adname")
            CommutePlace(
                name = name,
                latitude = lat,
                longitude = lon,
                address = address,
                cityName = poi.string("cityname")?.takeIf { it != "[]" },
            )
        }
    }

    /**
     * 按通勤方式取路线。开车含实时路况，公交含换乘/步行接驳，电瓶车按骑行路线折算。
     * [city] 只用于公共交通，优先传起终点的城市名。
     */
    suspend fun route(
        key: String,
        origin: CommutePlace,
        destination: CommutePlace,
        mode: CommuteMode,
        city: String?,
    ): AmapRoute {
        if (key.isBlank()) throw IOException("请先在通勤页填写高德 Web 服务 Key")
        val raw = when (mode) {
            CommuteMode.DRIVING -> drivingRoute(key, origin, destination)
            CommuteMode.WALKING -> walkingRoute(key, origin, destination)
            CommuteMode.BICYCLING, CommuteMode.EBIKE -> bicyclingRoute(key, origin, destination)
            CommuteMode.TRANSIT -> transitRoute(key, origin, destination, city)
        }
        val summary = when (mode) {
            CommuteMode.DRIVING ->
                if (raw.trafficLights > 0) "驾车 · 含 ${raw.trafficLights} 个红绿灯" else "驾车 · 实时路况"
            CommuteMode.WALKING -> "步行路线"
            CommuteMode.BICYCLING -> "骑行路线"
            CommuteMode.EBIKE -> "电瓶车估时 · 按骑行路线折算"
            CommuteMode.TRANSIT -> raw.detail.ifBlank { "公交/地铁" }
        }
        return AmapRoute(
            minutes = mode.estimateMinutes(raw.seconds),
            distanceMeters = raw.distanceMeters,
            trafficLights = raw.trafficLights,
            summary = summary,
        )
    }

    private suspend fun drivingRoute(
        key: String,
        origin: CommutePlace,
        destination: CommutePlace,
    ): RawRoute {
        val url = buildString {
            append("https://restapi.amap.com/v3/direction/driving?key=")
            append(enc(key))
            append("&origin=").append(enc(origin.coordinate))
            append("&destination=").append(enc(destination.coordinate))
            append("&strategy=0&extensions=base&ferry=0")
        }
        val root = request(url)
        val path = root.obj("route")?.array("paths")?.firstOrNull()?.jsonObject
            ?: throw IOException("没有查到可用驾车路线")
        return path.toRawRoute("驾车路线")
    }

    private suspend fun walkingRoute(
        key: String,
        origin: CommutePlace,
        destination: CommutePlace,
    ): RawRoute {
        val url = buildString {
            append("https://restapi.amap.com/v3/direction/walking?key=")
            append(enc(key))
            append("&origin=").append(enc(origin.coordinate))
            append("&destination=").append(enc(destination.coordinate))
        }
        val root = request(url)
        val path = root.obj("route")?.array("paths")?.firstOrNull()?.jsonObject
            ?: throw IOException("没有查到步行路线，可能距离太远或地点不明确")
        return path.toRawRoute("步行路线")
    }

    private suspend fun bicyclingRoute(
        key: String,
        origin: CommutePlace,
        destination: CommutePlace,
    ): RawRoute {
        val url = buildString {
            append("https://restapi.amap.com/v4/direction/bicycling?key=")
            append(enc(key))
            append("&origin=").append(enc(origin.coordinate))
            append("&destination=").append(enc(destination.coordinate))
        }
        val root = request(url)
        val path = root.obj("data")?.array("paths")?.firstOrNull()?.jsonObject
            ?: throw IOException("没有查到骑行路线，可能距离太远或地点不明确")
        return path.toRawRoute("骑行路线")
    }

    private suspend fun transitRoute(
        key: String,
        origin: CommutePlace,
        destination: CommutePlace,
        city: String?,
    ): RawRoute {
        if (city.isNullOrBlank()) {
            throw IOException("公共交通需要先设置城市，请在设置中选择当前城市")
        }
        val url = buildString {
            append("https://restapi.amap.com/v3/direction/transit/integrated?key=")
            append(enc(key))
            append("&origin=").append(enc(origin.coordinate))
            append("&destination=").append(enc(destination.coordinate))
            append("&city=").append(enc(city))
            append("&cityd=").append(enc(city))
            append("&strategy=0&nightflag=0&extensions=base")
        }
        val root = request(url)
        val transits = root.obj("route")?.array("transits")
            ?: throw IOException("没有查到公共交通路线")
        val best = transits.mapNotNull { it as? JsonObject }
            .map { transit ->
                val seconds = transit.string("duration")?.toDoubleOrNull() ?: 0.0
                val meters = transit.string("distance")?.toDoubleOrNull() ?: 0.0
                val walking = transit.string("walking_distance")?.toDoubleOrNull() ?: 0.0
                val transfers = ((transit.array("segments")?.size ?: 1) - 1).coerceAtLeast(0)
                RawRoute(
                    seconds = seconds.toInt(),
                    distanceMeters = meters.toInt(),
                    detail = "公交/地铁 · 换乘 $transfers 次 · 步行 ${walking.toInt()} 米",
                )
            }
            .filter { it.seconds > 0 }
            .minByOrNull { it.seconds }
            ?: throw IOException("没有查到可用公共交通路线，请调整起终点")
        return best
    }

    private fun JsonObject.toRawRoute(label: String): RawRoute {
        val seconds = string("duration")?.toDoubleOrNull() ?: 0.0
        val meters = string("distance")?.toDoubleOrNull() ?: 0.0
        if (seconds <= 0.0) throw IOException("${label}耗时为 0，请换一个更明确的目的地")
        return RawRoute(
            seconds = seconds.toInt(),
            distanceMeters = meters.toInt(),
            trafficLights = string("traffic_lights")?.toIntOrNull() ?: 0,
        )
    }

    private suspend fun request(url: String): JsonObject {
        val text = httpGet(url)
        val root = runCatching { json.parseToJsonElement(text).jsonObject }
            .getOrElse { throw IOException("高德返回了无法解析的数据") }
        val status = root.string("status")
        val errcode = root.string("errcode")
        val ok = status == "1" || (status == null && errcode == "0")
        if (!ok) {
            val info = root.string("info") ?: root.string("errmsg") ?: "未知错误"
            val code = root.string("infocode") ?: errcode
            throw IOException(
                when {
                    code == "10001" || code == "10002" -> "高德 Key 无效，请检查 Key 与「Web服务」类型"
                    code == "10003" -> "高德 Key 今日配额已用完，请明天再试或更换 Key"
                    code == "10009" -> "高德 Key 平台类型不匹配，请创建「Web服务」Key"
                    code == "10010" -> "请求参数不合法"
                    code == "10021" -> "高德 Key 的 IP 白名单限制，请把白名单设为空"
                    else -> "高德接口错误：$info${code?.let { "（$it）" } ?: ""}"
                },
            )
        }
        return root
    }

    private fun JsonObject.string(name: String): String? {
        val value = this[name] as? JsonPrimitive ?: return null
        return value.content.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

    private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}