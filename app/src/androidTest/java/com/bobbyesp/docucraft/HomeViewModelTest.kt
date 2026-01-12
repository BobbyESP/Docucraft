package com.bobbyesp.docucraft

import com.bobbyesp.docucraft.feature.pdfscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.pdfscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase.DeleteScannedPdfUseCase
import com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase.GetAllScannedPdfsUseCase
import com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase.GetScannedPdfByIdUseCase
import com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase.OpenPdfInViewerUseCase
import com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase.SaveScannedPdfUseCase
import com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase.ScanDocumentUseCase
import com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase.SearchPdfsUseCase
import com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase.SharePdfUseCase
import com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase.UpdatePdfMetadataUseCase
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.contract.HomeUiAction
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.viewmodel.HomeViewModel
import io.mockk.coEvery
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class HomeViewModelTest {

    private lateinit var viewModel: HomeViewModel
    private val getAllScannedPdfsUseCase: GetAllScannedPdfsUseCase = mockk()
    private val searchPdfsUseCase: SearchPdfsUseCase = mockk()
    private val getScannedPdfByIdUseCase: GetScannedPdfByIdUseCase = mockk()
    private val saveScannedPdfUseCase: SaveScannedPdfUseCase = mockk()
    private val deleteScannedPdfUseCase: DeleteScannedPdfUseCase = mockk()
    private val updatePdfMetadataUseCase: UpdatePdfMetadataUseCase = mockk()
    private val openPdfInViewerUseCase: OpenPdfInViewerUseCase = mockk()
    private val sharePdfUseCase: SharePdfUseCase = mockk()
    private val scanDocumentUseCase: ScanDocumentUseCase = mockk()

    @Before
    fun setup() {
        viewModel = HomeViewModel(
            getAllScannedPdfsUseCase,
            searchPdfsUseCase,
            getScannedPdfByIdUseCase,
            saveScannedPdfUseCase,
            deleteScannedPdfUseCase,
            updatePdfMetadataUseCase,
            openPdfInViewerUseCase,
            sharePdfUseCase,
            scanDocumentUseCase
        )
    }

//    @Test
//    fun onAction() = runTest {
//        val query = "example"
//        viewModel.onAction(HomeUiAction.UpdateSearchQuery(query))
//
//        val state = viewModel.uiState.value
//        assertEquals(query, state.searchQuery)
//    }

//    @Test
//    fun `onAction should clear search query when ClearSearch is triggered`() = runTest {
//        viewModel.onAction(HomeUiAction.UpdateSearchQuery("example"))
//        viewModel.onAction(HomeUiAction.ClearSearch)
//
//        val state = viewModel.uiState.value
//        assertEquals("", state.searchQuery)
//    }
//
//    @Test
//    fun `onAction should apply sort option when ApplySort is triggered`() = runTest {
//        val sortOption = SortOption(SortOption.Criteria.DATE, SortOption.Order.DESC)
//        viewModel.onAction(HomeUiAction.ApplySort(sortOption))
//
//        val state = viewModel.uiState.value
//        assertEquals(sortOption, state.filterOptions.sortBy)
//    }
//
//    @Test
//    fun `onAction should apply filter options when ApplyFilter is triggered`() = runTest {
//        val filterOptions = FilterOptions(minPageCount = 10, minFileSize = 1024)
//        viewModel.onAction(HomeUiAction.ApplyFilter(filterOptions))
//
//        val state = viewModel.uiState.value
//        assertEquals(filterOptions, state.filterOptions)
//    }
//
//    @Test
//    fun `onAction should clear filters when ClearFilters is triggered`() = runTest {
//        viewModel.onAction(HomeUiAction.ApplyFilter(FilterOptions(minPageCount = 10)))
//        viewModel.onAction(HomeUiAction.ClearFilters())
//
//        val state = viewModel.uiState.value
//        assertEquals(FilterOptions.default, state.filterOptions)
//    }
//
//    @Test
//    fun `onAction should launch scanner when OnScanButtonClicked is triggered`() = runTest {
//        val effects = mutableListOf<HomeUiEffect>()
//        val job = launch {
//            viewModel.uiEffect.collect { effects.add(it) }
//        }
//
//        viewModel.onAction(HomeUiAction.OnScanButtonClicked)
//
//        assertTrue(effects.contains(HomeUiEffect.LaunchScanner))
//        job.cancel()
//    }
//
//    @Test
//    fun `onAction should show error when scan fails`() = runTest {
//        val errorMessage = "Scan failed"
//        coEvery { scanDocumentUseCase(any()) } returns flowOf(Resource.Error(errorMessage))
//
//        viewModel.onAction(HomeUiAction.OnScanResultReceived("result"))
//
//        val state = viewModel.uiState.value
//        assertEquals(errorMessage, state.scanUserMessage)
//    }
}
