package com.bobbyesp.docucraft.core.di

import com.bobbyesp.docucraft.core.data.datasource.MlKitDataSource
import com.bobbyesp.docucraft.core.data.datasource.impl.MlKitDataSourceImpl
import com.bobbyesp.docucraft.core.data.repository.MlKitDocumentScannerRepository
import com.bobbyesp.docucraft.core.domain.repository.DocumentScannerRepository
import org.koin.dsl.module

val coreModule = module {
    // Data Source
    single<MlKitDataSource> { MlKitDataSourceImpl() }

    // Repository
    single<DocumentScannerRepository> { MlKitDocumentScannerRepository(get(), get()) }
}
