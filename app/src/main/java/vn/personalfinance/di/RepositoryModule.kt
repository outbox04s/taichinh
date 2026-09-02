package vn.personalfinance.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import vn.personalfinance.data.repository.SupabaseFinanceRepository
import vn.personalfinance.domain.repository.FinanceRepository

@Module @InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindFinanceRepository(impl: SupabaseFinanceRepository): FinanceRepository
}
