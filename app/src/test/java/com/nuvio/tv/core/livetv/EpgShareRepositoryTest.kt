package com.nuvio.tv.core.livetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgShareRepositoryTest {
    @Test fun matchesAddonNamesToEpgshareIdsAndDisplayNames() {
        val addon = EpgShareRepository.channelKeys("ESPN 5 HD")
        assertTrue(EpgShareRepository.channelKeys("ESPN.5.br", "BR1").any { it in addon })
        val city = EpgShareRepository.channelKeys("São Paulo/SP  HBO Family HD", "BR1")
        assertTrue(EpgShareRepository.channelKeys("HBO Family").any { it in city })
        val cityId = EpgShareRepository.channelKeys("S\u00e3o.Paulo/SP..Telecine.Action.HD.br", "BR1")
        assertTrue(EpgShareRepository.channelKeys("Telecine Action").any { it in cityId })
    }

    @Test fun keepsDifferentChannelsApart() {
        val a = EpgShareRepository.channelKeys("ESPN 2")
        val b = EpgShareRepository.channelKeys("ESPN 5")
        assertTrue(a.none { it in b })
    }

    @Test fun parsesXmltvTimestampsWithOffsets() {
        assertEquals(1_794_457_800_000L, EpgShareRepository.parseTimestamp("20261112043000 +0000"))
        assertEquals(
            EpgShareRepository.parseTimestamp("20261112043000 +0000") - 3 * 3_600_000L,
            EpgShareRepository.parseTimestamp("20261112043000 +0300"),
        )
        assertEquals(0L, EpgShareRepository.parseTimestamp("garbage"))
    }
}
