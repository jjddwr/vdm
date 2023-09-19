package com.ingbyr.vdm.controllers

import com.ingbyr.vdm.engines.AbstractEngine
import com.ingbyr.vdm.engines.utils.EngineFactory
import com.ingbyr.vdm.engines.utils.EngineType
import com.ingbyr.vdm.events.CreateDownloadTask
import com.ingbyr.vdm.events.RefreshEngineVersion
import com.ingbyr.vdm.events.RestorePreferencesViewEvent
import com.ingbyr.vdm.events.UpdateEngineTask
import com.ingbyr.vdm.models.DownloadTaskModel
import com.ingbyr.vdm.models.DownloadTaskStatus
import com.ingbyr.vdm.models.DownloadTaskType
import com.ingbyr.vdm.models.TaskConfig
import com.ingbyr.vdm.utils.*
import com.ingbyr.vdm.utils.Attributes
import com.ingbyr.vdm.utils.config.update
import javafx.application.Platform
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tornadofx.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap


class MainController : Controller() {

    init {
        messages = ResourceBundle.getBundle("i18n/MainView")
    }

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    val downloadTaskModelList = mutableListOf<DownloadTaskModel>().observable()
    private val engineList = ConcurrentHashMap<String, AbstractEngine>() // FIXME auto clean the finished models engines

    init {

        // debug mode
        DebugUtils.changeDebugMode(app.config.boolean(Attributes.DEBUG_MODE, Attributes.Defaults.DEBUG_MODE))

        subscribe<CreateDownloadTask> {
            logger.debug("create models: ${it.downloadTask}")
            addToModelListAndStartTask(it.downloadTask)
            saveTaskToDB(it.downloadTask)
        }

        // background thread
        subscribe<UpdateEngineTask> {
            val charset = app.config.string(Attributes.CHARSET, Attributes.Defaults.CHARSET)
            val engine = EngineFactory.create(it.engineType, charset)
            val taskConfig = TaskConfig("", it.engineType, DownloadTaskType.ENGINE, true, engine.enginePath)
            val downloadTask = DownloadTaskModel(taskConfig, DateTimeUtils.now(), title = "[${messages["ui.update"]} ${it.engineType.name}]")
            // make sure thread safe
            Platform.runLater {
                downloadTaskModelList.add(downloadTask)
            }
            try {
                if (engine.existNewVersion(it.localVersion)) {
                    downloadTask.taskConfig.url = engine.updateUrl()
                    logger.info("[${downloadTask.taskConfig.engineType}] update engine from ${downloadTask.taskConfig.url}")
                    NetUtils().downloadEngine(downloadTask, engine.remoteVersion!!, needUnzip = engine.downloadNewEngineNeedUnzip)
                } else {
                    downloadTask.title += messages["ui.noAvailableUpdates"]
                    downloadTask.size = ""
                    downloadTask.status = DownloadTaskStatus.COMPLETED
                    downloadTask.progress = 1.0
                }
            } catch (e: Exception) {
                logger.error(e.toString())
                downloadTask.status = DownloadTaskStatus.FAILED
            }
            fire(RestorePreferencesViewEvent)
        }

        subscribe<RefreshEngineVersion> {
            when (it.engineType) {
                EngineType.YOUTUBE_DL -> {
                    app.config.update(Attributes.YOUTUBE_DL_VERSION, it.newVersion)
                }
                EngineType.ANNIE -> {
                    app.config.update(Attributes.ANNIE_VERSION, it.newVersion)
                }
            }
        }
    }


    fun startTask(downloadTask: DownloadTaskModel) {
        if (downloadTask.taskConfig.downloadType == DownloadTaskType.ENGINE) return
        downloadTask.status = DownloadTaskStatus.ANALYZING
        val charset = app.config.string(Attributes.CHARSET, Attributes.Defaults.CHARSET)
        runAsync {
            // download
            val engine = EngineFactory.create(downloadTask.taskConfig.engineType, charset)
            engineList[downloadTask.createdAt] = engine
            val taskConfig = downloadTask.taskConfig
            engine.addProxy(taskConfig.proxyType, taskConfig.proxyAddress, taskConfig.proxyPort)
                    .format(taskConfig.formatId)
                    .output(taskConfig.storagePath)
                    .cookies(taskConfig.cookie)
                    .ffmpegPath(taskConfig.ffmpeg)
                    .url(taskConfig.url)
                    .downloadMedia(downloadTask, messages)
        }
    }

    fun startAllTask() {
        downloadTaskModelList.forEach {
            if (it.status != DownloadTaskStatus.COMPLETED && it.taskConfig.downloadType != DownloadTaskType.ENGINE) {
                startTask(it)
            }
        }
    }

    fun stopTask(downloadTask: DownloadTaskModel) {
        logger.debug("try to stop download models $downloadTask")
        engineList[downloadTask.createdAt]?.stopTask()
    }

    fun stopAllTask() {
        logger.debug("try to stop all download tasks")
        engineList.forEach {
            it.value.stopTask()
        }
    }

    fun deleteTask(downloadTaskModel: DownloadTaskModel) {
        stopTask(downloadTaskModel)
        downloadTaskModelList.remove(downloadTaskModel)
        DBUtils.deleteDownloadTask(downloadTaskModel)
    }

    private fun saveTaskToDB(downloadTask: DownloadTaskModel) {
        logger.debug("add download task $downloadTask to db")
        DBUtils.saveDownloadTask(downloadTask)
    }

    private fun addToModelListAndStartTask(downloadTask: DownloadTaskModel) {
        downloadTaskModelList.add(downloadTask)
        startTask(downloadTask)
    }

    fun loadTaskFromDB() {
        DBUtils.loadAllDownloadTasks(downloadTaskModelList)
    }

    fun clear() {
        stopAllTask()
        downloadTaskModelList.forEach { saveTaskToDB(it) }
    }
}