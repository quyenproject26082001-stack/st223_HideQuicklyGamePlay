package com.wanted.poster.hihi.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wanted.poster.hihi.data.local.dao.UserDao
import com.wanted.poster.hihi.data.local.entity.User

@Database(entities = [User::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}