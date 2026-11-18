package com.theveloper.pixelplay

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.service.player.loadControlBufferProfileFor
import org.junit.Test

class DualPlayerEngineBufferProfileTest {

    @Test
    fun loadControlBufferProfileFor_standardDevice_providesExtendedBufferAndBackBuffer() {
        val profile = loadControlBufferProfileFor(isLowRamDevice = false)
        assertThat(profile.minBufferMs).isEqualTo(60_000)
        assertThat(profile.maxBufferMs).isEqualTo(240_000)
        assertThat(profile.backBufferMs).isEqualTo(30_000)
    }

    @Test
    fun loadControlBufferProfileFor_lowRamDevice_providesConservativeBuffer() {
        val profile = loadControlBufferProfileFor(isLowRamDevice = true)
        assertThat(profile.minBufferMs).isEqualTo(15_000)
        assertThat(profile.maxBufferMs).isEqualTo(30_000)
        assertThat(profile.backBufferMs).isEqualTo(10_000)
    }
}
