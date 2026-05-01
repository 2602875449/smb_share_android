package com.qi.smbshare.di

import android.content.Context
import com.qi.smbshare.data.discovery.AndroidSmbHostDiscovery
import com.qi.smbshare.data.discovery.SmbHostDiscovery
import com.qi.smbshare.data.local.SMBConnectionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object SmbViewModelModule {

    @Provides
    @ViewModelScoped
    // SMB 会话必须按 ViewModel/配置隔离，避免不同页面共享全局连接状态。
    fun provideSmbConnectionManager(): SMBConnectionManager = SMBConnectionManager()

    @Provides
    @ViewModelScoped
    fun provideSmbHostDiscovery(
        @ApplicationContext context: Context
    ): SmbHostDiscovery = AndroidSmbHostDiscovery(context)
}
