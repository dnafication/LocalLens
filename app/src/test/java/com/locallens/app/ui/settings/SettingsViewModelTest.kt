package com.locallens.app.ui.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsViewModelTest {

    @Test
    fun `initial state has correct defaults`() {
        val viewModel = SettingsViewModel()
        val state = viewModel.uiState.value
        assertFalse(state.indexScreenshots)
        assertTrue(state.indexVideos)
    }

    @Test
    fun `toggling screenshots updates state`() {
        val viewModel = SettingsViewModel()
        viewModel.onToggleScreenshots(true)
        assertTrue(viewModel.uiState.value.indexScreenshots)

        viewModel.onToggleScreenshots(false)
        assertFalse(viewModel.uiState.value.indexScreenshots)
    }

    @Test
    fun `toggling videos updates state`() {
        val viewModel = SettingsViewModel()
        viewModel.onToggleVideos(false)
        assertFalse(viewModel.uiState.value.indexVideos)

        viewModel.onToggleVideos(true)
        assertTrue(viewModel.uiState.value.indexVideos)
    }
}
