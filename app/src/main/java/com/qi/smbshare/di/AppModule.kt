package com.qi.smbshare.di

import android.content.Context
import androidx.room.Room
import com.qi.smbshare.data.local.DataStoreManager
import com.qi.smbshare.data.local.TransferDatabase
import com.qi.smbshare.data.local.TransferTaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStoreManager(
        @ApplicationContext context: Context
    ): DataStoreManager = DataStoreManager(context)

    @Provides
    @Singleton
    fun provideTransferDatabase(
        @ApplicationContext context: Context
    ): TransferDatabase {
        return Room.databaseBuilder(
            context,
            TransferDatabase::class.java,
            "transfer_database"
        )
            .addMigrations(*TransferDatabase.MIGRATIONS)
            .build()
    }

    @Provides
    fun provideTransferTaskDao(database: TransferDatabase): TransferTaskDao {
        return database.transferTaskDao()
    }
}
