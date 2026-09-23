package com.hsr.railfocus.domain.usecase

import com.hsr.railfocus.data.local.dataaccess.FocusTypeDataAccess
import com.hsr.railfocus.data.local.dataaccess.JournalDataAccess
import com.hsr.railfocus.data.local.dataaccess.JourneyDataAccess
import com.hsr.railfocus.data.local.dataaccess.VisitedStationDataAccess
import com.hsr.railfocus.data.local.entity.FocusTypeEntity
import com.hsr.railfocus.data.local.entity.JourneyJournalEntity
import com.hsr.railfocus.data.local.entity.JourneyRecordEntity
import com.hsr.railfocus.data.preferences.DailyGoalState
import com.hsr.railfocus.data.preferences.UserPreferencesRepository
import com.hsr.railfocus.domain.model.AppBackupData
import com.hsr.railfocus.domain.model.FrequentFlyerState
import com.hsr.railfocus.domain.model.MembershipTier
import com.hsr.railfocus.util.appJson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ExportUserDataUseCaseTest {

    private lateinit var journeyDataAccess: JourneyDataAccess
    private lateinit var visitedStationDataAccess: VisitedStationDataAccess
    private lateinit var focusTypeDataAccess: FocusTypeDataAccess
    private lateinit var journalDataAccess: JournalDataAccess
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var useCase: ExportUserDataUseCase

    @Before
    fun setup() {
        journeyDataAccess = mockk()
        visitedStationDataAccess = mockk()
        focusTypeDataAccess = mockk()
        journalDataAccess = mockk()
        preferencesRepository = mockk()

        useCase = ExportUserDataUseCase(
            journeyDataAccess,
            visitedStationDataAccess,
            focusTypeDataAccess,
            journalDataAccess,
            preferencesRepository,
        )
    }

    @Test
    fun testExportAllData_serializesAndRecoversSuccessfullyFromZip() = runTest {
        val dummyJourney = JourneyRecordEntity(
            id = "j_001",
            startStationId = "南京南",
            endStationId = "北京南",
            plannedDurationMin = 210,
            actualDurationMin = 210,
            pathJson = "{}",
            createdAt = 1000L,
            completedAt = 2000L,
            status = "COMPLETED",
        )

        val dummyType = FocusTypeEntity(
            id = "code",
            displayName = "编程",
            iconName = "Code",
            colorHex = 0xFF0000,
            containerColorHex = 0xAA0000,
            isRemovable = true,
            order = 0,
        )

        val dummyJournal = JourneyJournalEntity(
            id = "journal_001",
            journeyId = "j_001",
            stationId = "北京南",
            stationName = "北京南",
            content = "京沪线飞驰，手账留念。",
            imagePathsJson = """["img1.jpg"]""",
            audioDurationSec = 15,
            createdAt = 2000L,
            updatedAt = 2000L,
        )

        coEvery { journeyDataAccess.getAllRecords() } returns listOf(dummyJourney)
        coEvery { visitedStationDataAccess.getAllVisitedStationIds() } returns listOf("南京南", "北京南")
        coEvery { focusTypeDataAccess.getAllList() } returns listOf(dummyType)
        coEvery { journalDataAccess.getAllJournals() } returns listOf(dummyJournal)
        every { preferencesRepository.frequentFlyerState } returns flowOf(
            FrequentFlyerState(tier = MembershipTier.PLATINUM, totalFocusMinutes = 3600)
        )
        every { preferencesRepository.dailyGoalState } returns flowOf(
            DailyGoalState(goalMin = 60, todayFocusMin = 30)
        )

        val outputStream = ByteArrayOutputStream()
        val result = useCase.exportToStream(outputStream, "1.7")

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        val exportedBytes = outputStream.toByteArray()
        assertTrue(exportedBytes.isNotEmpty())

        val zipIn = ZipInputStream(ByteArrayInputStream(exportedBytes))
        var jsonString: String? = null
        var entry = zipIn.nextEntry
        while (entry != null) {
            if (entry.name == "data.json") {
                val out = ByteArrayOutputStream()
                zipIn.copyTo(out)
                jsonString = out.toString("UTF-8")
                break
            }
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }

        assertNotNull("导出的 ZIP 中必须包含 data.json", jsonString)
        val backupData: AppBackupData = appJson.decodeFromString(jsonString!!)

        assertEquals("1.7", backupData.appVersion)
        assertEquals(1, backupData.journeys.size)
        assertEquals("j_001", backupData.journeys.first().id)
        assertEquals(2, backupData.visitedStationIds.size)
        assertEquals(1, backupData.focusTypes.size)
        assertEquals(1, backupData.journals.size)
        assertEquals("京沪线飞驰，手账留念。", backupData.journals.first().content)
        assertEquals(3600, backupData.statistics?.lifetimeFocusMin)
        assertEquals(0, backupData.statistics?.focusStreak)
        assertEquals("PLATINUM", backupData.statistics?.membershipTier)
    }
}
