package com.habitrpg.android.habitica.modules

import com.habitrpg.android.habitica.apiclient.ConnectionProblemDialogs
import com.habitrpg.android.habitica.apiclient.OkhttpWrapper
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BindOkhttpMod {
    @Binds
    abstract fun bindOkhttp(okhttp: OkhttpWrapper.Base): OkhttpWrapper


    @Binds
    abstract fun bindDialogs(dialogs: ConnectionProblemDialogs.Base): ConnectionProblemDialogs
}