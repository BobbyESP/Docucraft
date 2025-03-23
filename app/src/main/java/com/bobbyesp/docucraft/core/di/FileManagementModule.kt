package com.bobbyesp.docucraft.core.di

import com.bobbyesp.docucraft.core.data.local.repository.FileRepositoryImpl
import com.bobbyesp.docucraft.core.domain.repository.FileRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val fileManagementModule = module {
    single<FileRepository> { FileRepositoryImpl(context = androidContext()) }
}
