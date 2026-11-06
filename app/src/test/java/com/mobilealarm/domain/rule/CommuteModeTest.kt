package com.mobilealarm.domain.rule

import com.mobilealarm.domain.model.CommuteMode
import com.mobilealarm.domain.model.CommutePlace
import com.mobilealarm.domain.model.CommutePlan
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommuteModeTest {

    @Test
    fun `e-bike converts bike duration with its own factor`() {
        assertEquals(17, CommuteMode.BICYCLING.estimateMinutes(1000))
        assertEquals(12, CommuteMode.EBIKE.estimateMinutes(1000))
    }

    @Test
    fun `zero or negative duration still yields one minute`() {
        assertEquals(1, CommuteMode.WALKING.estimateMinutes(0))
        assertEquals(1, CommuteMode.DRIVING.estimateMinutes(-30))
    }

    @Test
    fun `plan mode and place city survive json round trip`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val plan = CommutePlan(
            enabled = true,
            mode = CommuteMode.TRANSIT,
            origin = CommutePlace("家", 30.1, 120.2, "小区", "杭州"),
            destination = CommutePlace("公司", 30.3, 120.3, "园区", "杭州"),
        )
        val restored = json.decodeFromString<CommutePlan>(json.encodeToString(CommutePlan.serializer(), plan))

        assertEquals(CommuteMode.TRANSIT, restored.mode)
        assertEquals("杭州", restored.origin?.cityName)
    }

    @Test
    fun `legacy plan without mode defaults to driving`() {
        val json = Json { ignoreUnknownKeys = true }
        val restored = json.decodeFromString<CommutePlan>(
            """{"enabled":true,"arrivalHour":9,"arrivalMinute":0,"bufferMinutes":10}""",
        )

        assertEquals(CommuteMode.DRIVING, restored.mode)
        assertTrue(restored.enabled)
    }
}
