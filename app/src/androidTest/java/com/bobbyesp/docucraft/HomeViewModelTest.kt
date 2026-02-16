package com.bobbyesp.docucraft

import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.DeleteDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.GetDocumentByIdUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.OpenDocumentInViewerUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveScannedDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ScanDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SearchDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ShareDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.UpdateDocumentFieldsUseCase
import com.bobbyesp.docucraft.feature.docscanner.presentation.pages.home.viewmodel.HomeViewModel
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before

@ExperimentalCoroutinesApi
class HomeViewModelTest {

    private lateinit var viewModel: HomeViewModel
    private val observeDocumentsUseCase: ObserveDocumentsUseCase = mockk()
    private val searchDocumentsUseCase: SearchDocumentsUseCase = mockk()
    private val getDocumentByIdUseCase: GetDocumentByIdUseCase = mockk()
    private val saveScannedDocumentUseCase: SaveScannedDocumentUseCase = mockk()
    private val deleteDocumentUseCase: DeleteDocumentUseCase = mockk()
    private val updateDocumentFieldsUseCase: UpdateDocumentFieldsUseCase = mockk()
    private val openDocumentInViewerUseCase: OpenDocumentInViewerUseCase = mockk()
    private val shareDocumentUseCase: ShareDocumentUseCase = mockk()
    private val scanDocumentUseCase: ScanDocumentUseCase = mockk()

    @Before
    fun setup() {
        viewModel =
            HomeViewModel(
                observeDocumentsUseCase,
                searchDocumentsUseCase,
                getDocumentByIdUseCase,
                saveScannedDocumentUseCase,
                deleteDocumentUseCase,
                updateDocumentFieldsUseCase,
                openDocumentInViewerUseCase,
                shareDocumentUseCase,
                scanDocumentUseCase,
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
