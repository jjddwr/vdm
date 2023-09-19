package com.ingbyr.vdm.dao

import com.ingbyr.vdm.engines.utils.EngineType
import com.ingbyr.vdm.models.DownloadTaskStatus
import com.ingbyr.vdm.models.DownloadTaskType
import com.ingbyr.vdm.models.ProxyType
import com.ingbyr.vdm.utils.Attributes
import com.ingbyr.vdm.utils.DateTimeUtils
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DownloadTaskDAOTests {
    init {
        Database.connect(Attributes.DATABASE_URL, driver = "org.h2.Driver", user = "vdm", password = "vdm")
        transaction {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(DownloadTaskTable, TaskConfigTable)
        }
    }

    @Test
    fun `insert download task to db`() {
        val downloadTask = transaction {
            val newTaskConfig = TaskConfigDAO.new {
                url = "test url"
                downloadType = DownloadTaskType.SINGLE_MEDIA.name
                engineType = EngineType.YOUTUBE_DL.name
                downloadDefaultFormat = false
                storagePath = Attributes.APP_DIR.toString()
                cookie = "test cookie"
                ffmpeg = "test ffmpeg"
                formatId = "test format id"
                proxyType = ProxyType.SOCKS5.name
                proxyAddress = "127.0.0.1"
                proxyPort = "1080"
            }
            DownloadTaskDAO.new {
                taskConfig = newTaskConfig
                checked = false
                title = "test title"
                size = "1024 mb"
                status = DownloadTaskStatus.ANALYZING.name
                progress = 0.0F
                createdAt = DateTimeUtils.now()
            }
        }

        transaction {
            val taskInDb = DownloadTaskDAO.findById(downloadTask.id)
            Assertions.assertNotNull(taskInDb)
            Assertions.assertTrue(taskInDb?.taskConfig is TaskConfigDAO)
        }
    }

    @AfterEach
    fun `clear test data`() {
        transaction {
            DownloadTaskDAO.find { DownloadTaskTable.title eq "test title" }.forEach {
                it.delete()
                it.taskConfig.delete()
            }
        }
    }
}