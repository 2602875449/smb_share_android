package com.qi.smbshare.ui.navigation

import androidx.lifecycle.SavedStateHandle
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.data.repository.ConnectionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppNavigationViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state is restored from SavedStateHandle`() {
        val handle = SavedStateHandle()
        val viewModel = AppNavigationViewModel(handle, emptyConnectionRepository())
        val config = sampleConfig()

        viewModel.showFiles(config, "docs")
        viewModel.startEditing(config.copy(id = "edit"))
        viewModel.setPreviewVisible(true)

        val restored = AppNavigationViewModel(handle, emptyConnectionRepository()).state.value

        assertEquals(config.id, restored.currentConfig?.id)
        assertEquals("edit", restored.editConfig?.id)
        assertEquals("", restored.currentConfig?.password)
        assertEquals("", restored.editConfig?.password)
        assertEquals("docs", restored.initialPath)
        assertTrue(restored.isFilePreviewVisible)
    }

    @Test
    fun `clearCurrentConnection keeps edit state untouched and hides preview`() {
        val handle = SavedStateHandle()
        val viewModel = AppNavigationViewModel(handle, emptyConnectionRepository())
        val config = sampleConfig()

        viewModel.showFiles(config)
        viewModel.startEditing(config)
        viewModel.setPreviewVisible(true)
        viewModel.clearCurrentConnection()

        val state = viewModel.state.value
        assertNull(state.currentConfig)
        assertEquals(config.id, state.editConfig?.id)
        assertEquals("", state.initialPath)
        assertFalse(state.isFilePreviewVisible)
    }

    @Test
    fun `SavedStateHandle stores only non secret config snapshot`() {
        val handle = SavedStateHandle()
        val viewModel = AppNavigationViewModel(handle, emptyConnectionRepository())
        val config = sampleConfig()

        viewModel.showFiles(config, "docs")
        viewModel.startEditing(config)

        val savedValues = handle.keys().mapNotNull { key ->
            handle.get<Any>(key)?.toString()
        }
        val serializedState = savedValues.joinToString(separator = "\n")

        assertFalse(serializedState.contains("pass"))
        assertFalse(serializedState.contains("password"))
        assertTrue(serializedState.contains(config.id))
        assertTrue(serializedState.contains(config.serverAddress))
    }

    @Test
    fun `repository restore does not persist password back into SavedStateHandle`() = runTest {
        val handle = SavedStateHandle()
        val config = sampleConfig()
        AppNavigationViewModel(handle, emptyConnectionRepository()).showFiles(config)

        val restoredConfig = config.copy(password = "restored-secret")
        val viewModel = AppNavigationViewModel(
            handle,
            connectionRepositoryReturning(restoredConfig)
        )

        advanceUntilIdle()

        assertEquals("restored-secret", viewModel.state.value.currentConfig?.password)
        val serializedState = handle.keys()
            .mapNotNull { key -> handle.get<Any>(key)?.toString() }
            .joinToString(separator = "\n")
        assertFalse(serializedState.contains("restored-secret"))
        assertFalse(serializedState.contains("password"))
    }

    private fun sampleConfig(): SMBConfig {
        return SMBConfig(
            id = "config-1",
            serverAddress = "192.168.0.20",
            shareName = "share",
            username = "user",
            password = "pass"
        )
    }

    private fun emptyConnectionRepository(): ConnectionRepository {
        return mockk {
            coEvery { getConfigById(any()) } returns null
        }
    }

    private fun connectionRepositoryReturning(config: SMBConfig): ConnectionRepository {
        return mockk {
            coEvery { getConfigById(any()) } returns null
            coEvery { getConfigById(config.id) } returns config
        }
    }
}
