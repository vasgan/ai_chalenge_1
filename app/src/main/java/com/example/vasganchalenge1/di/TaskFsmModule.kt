package com.example.vasganchalenge1.di

import com.example.vasganchalenge1.data.taskfsm.DefaultTaskToolRunner
import com.example.vasganchalenge1.data.taskfsm.TaskToolRunner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TaskFsmModule {
    @Binds
    @Singleton
    abstract fun bindTaskToolRunner(
        impl: DefaultTaskToolRunner
    ): TaskToolRunner
}
