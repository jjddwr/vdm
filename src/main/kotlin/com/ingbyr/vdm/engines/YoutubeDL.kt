package com.ingbyr.vdm.engines

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ingbyr.vdm.engines.utils.EngineDownloadType
import com.ingbyr.vdm.engines.utils.EngineException
import com.ingbyr.vdm.engines.utils.EngineType
import com.ingbyr.vdm.models.DownloadTaskModel
import com.ingbyr.vdm.models.DownloadTaskStatus
import com.ingbyr.vdm.models.MediaFormat
import com.ingbyr.vdm.models.ProxyType
import com.ingbyr.vdm.utils.*
import com.ingbyr.vdm.utils.config.app
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern


class YoutubeDL : AbstractEngine() {
    override val logger: Logger = LoggerFactory.getLogger(this::class.java)
    override val downloadNewEngineNeedUnzip: Boolean = false
    override val remoteVersionUrl: String =
        "https://raw.githubusercontent.com/rg3/youtube-dl/master/youtube_dl/version.py"
    override val engineType = EngineType.YOUTUBE_DL
    override val enginePath: String = engineExecPath()
    override var remoteVersion: String? = null
    override var version: String = app.config.string(Attributes.YOUTUBE_DL_VERSION, Attributes.Defaults.ENGINE_VERSION)
    override var taskModel: DownloadTaskModel? = null
    override val argsMap: MutableMap<String, String> = mutableMapOf("engines" to enginePath)

    private var speed = "0MiB/s"
    private var progress = 0.0
    private var size = ""
    private var title = ""
    private val nameTemplate = "%(title)s.%(ext)s"
    private val progressPattern = Pattern.compile("\\d+\\W?\\d*%")
    private val speedPattern = Pattern.compile("\\d+\\W?\\d*\\w+/s")
    private val titlePattern = Pattern.compile("[/\\\\][^/^\\\\]+\\.\\w+")
    private val fileSizePattern = Pattern.compile("\\s\\d+\\W?\\d*\\w+B\\s")
    private val remoteVersionPattern = Pattern.compile("\\d+.+\\d+")

    override fun parseFormatsJson(jsonString: String): List<MediaFormat> {
        val formats = mutableListOf<MediaFormat>()
        val mapper = jacksonObjectMapper()
        val mediaJson = mapper.readValue<YoutubeDlMediaJson>(jsonString)
        mediaJson.formats.forEach {
            formats.add(
                MediaFormat(
                    title = mediaJson.title,
                    desc = mediaJson.description,
                    formatID = it.formatId,
                    format = it.format,
                    formatNote = it.formatNote,
                    fileSize = it.filesize,
                    ext = it.ext
                )
            )
        }
        return formats
    }

    override fun simulateJson(): AbstractEngine {
        argsMap["SimulateJson"] = "-j"
        return this
    }

    override fun url(url: String): AbstractEngine {
        argsMap["url"] = url
        return this
    }

    override fun addProxy(type: ProxyType, address: String, port: String): AbstractEngine {
        if (address.isEmpty() or port.isEmpty()) {
            return this
        }
        when (type) {
            ProxyType.SOCKS5 -> {
                argsMap["--proxy"] = "socks5://$address:$port"
            }
            ProxyType.HTTP -> {
                argsMap["--proxy"] = "$address:$port"
            }
            else -> {
                logger.debug("no proxy")
            }
        }
        return this
    }

    override fun fetchMediaJson(): String {
        val mediaData = execCommand(argsMap.build(), EngineDownloadType.JSON)
        if (mediaData != null) {
            return mediaData.toString()
        } else {
            logger.error("no media json return")
            throw EngineException("no media json return")
        }
    }

    override fun format(formatID: String): AbstractEngine {
        return if (formatID.isEmpty()) {
            this
        } else {
            argsMap["-f"] = formatID
            this
        }
    }

    override fun output(outputPath: String): AbstractEngine {
        argsMap["-o"] = Paths.get(outputPath, nameTemplate).toString()
        return this
    }

    override fun ffmpegPath(ffmpegPath: String): AbstractEngine {
        return if (ffmpegPath.isEmpty()) {
            this
        } else {
            argsMap["--ffmpeg-location"] = ffmpegPath
            this
        }
    }

    override fun cookies(cookies: String): AbstractEngine {
        return if (cookies.isEmpty()) {
            this
        } else {
            argsMap["--cookies"] = cookies
            this
        }
    }

    override fun downloadMedia(downloadTaskModel: DownloadTaskModel, message: ResourceBundle) {
        taskModel = downloadTaskModel
        taskModel?.run {
            // init display
            execCommand(argsMap.build(), EngineDownloadType.SINGLE)
        }
    }

    override fun parseDownloadOutput(line: String) {
        if (title.isEmpty()) {
            title = titlePattern.matcher(line).takeIf { it.find() }?.group()?.toString() ?: title
            title = title.removePrefix("/").removePrefix("\\")
            if (title.isNotEmpty()) taskModel?.title = title
        }
        if (size.isEmpty()) {
            size = fileSizePattern.matcher(line).takeIf { it.find() }?.group()?.toString() ?: size
            if (size.isNotEmpty()) taskModel?.size = size.trim()
        }

        progress = progressPattern.matcher(line).takeIf { it.find() }?.group()?.toProgress() ?: progress
        speed = speedPattern.matcher(line).takeIf { it.find() }?.group()?.toString() ?: speed
        logger.debug("$line -> title=$title, progress=$progress, size=$size, speed=$speed")

        taskModel?.run {
            if (this@YoutubeDL.progress >= 1.0) {
                this.progress = 1.0
                if (line.trim().startsWith("[ffmpeg]"))
                    this.status = DownloadTaskStatus.MERGING
                else
                    this.status = DownloadTaskStatus.COMPLETED
            } else if (this@YoutubeDL.progress > 0) {
                this.progress = this@YoutubeDL.progress
                this.status = DownloadTaskStatus.DOWNLOADING
            }
        }
    }

    override fun execCommand(command: MutableList<String>, downloadType: EngineDownloadType): StringBuilder? {
        /**
         * Exec the command by invoking the system shell etc.
         * Long time models
         */
        running.set(true)
        val builder = ProcessBuilder(command)
        builder.redirectErrorStream(true)
        val p = builder.start()
        val r = BufferedReader(InputStreamReader(p.inputStream, charset))
        logger.debug("parse output as $charset")
        val output = StringBuilder()
        var line: String?
        when (downloadType) {
            EngineDownloadType.JSON -> {
                // fetch the media json and return string builder
                while (running.get()) {
                    line = r.readLine()
                    if (line != null) {
                        output.append(line.trim())
                    } else {
                        break
                    }
                }
            }

            EngineDownloadType.SINGLE, EngineDownloadType.PLAYLIST -> {
                while (running.get()) {
                    line = r.readLine()
                    if (line != null) {
                        parseDownloadOutput(line)
                    } else {
                        break
                    }
                }
            }
        }

        if (p.isAlive) { // means user stop this models manually
            p.destroy()
            p.waitFor(200, TimeUnit.MICROSECONDS)
        }

        if (p.isAlive) {
            p.destroyForcibly()
        }

        return if (running.get()) {
            running.set(false)
            output
        } else { // means user stop this models manually
            taskModel?.run {
                status = DownloadTaskStatus.STOPPED
            }
            logger.debug("stop the models of $taskModel")
            null
        }
    }

    override fun engineExecPath(): String = when (OSUtils.currentOS) {
        OSType.WINDOWS -> {
            Attributes.ENGINES_DIR.resolve("youtube-dl.exe").toAbsolutePath().toString()
        }
        OSType.LINUX -> {
            Attributes.ENGINES_DIR.resolve("youtube-dl").toAbsolutePath().toString()
        }
        OSType.MAC_OS -> {
            Attributes.ENGINES_DIR.resolve("youtube-dl").toAbsolutePath().toString()
        }
    }

    override fun updateUrl() = when (OSUtils.currentOS) {
        OSType.WINDOWS -> {
            "https://github.com/rg3/youtube-dl/releases/download/$remoteVersion/youtube-dl.exe"
        }
        OSType.LINUX, OSType.MAC_OS -> {
            "https://github.com/rg3/youtube-dl/releases/download/$remoteVersion/youtube-dl"
        }
    }

    override fun existNewVersion(localVersion: String): Boolean {
        val remoteVersionInfo = NetUtils().get(remoteVersionUrl)
        return if (remoteVersionInfo?.isNotEmpty() == true) {
            remoteVersion = remoteVersionPattern.matcher(remoteVersionInfo).takeIf { it.find() }?.group()
            if (remoteVersion != null) {
                logger.debug("[$engineType] local version $localVersion, remote version $remoteVersion")
                UpdateUtils.check(localVersion, remoteVersion!!)
            } else {
                logger.error("[$engineType] get remote version failed")
                false
            }
        } else {
            logger.error("[$engineType] get remote version failed")
            false
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class YoutubeDlMediaJson(
    @JsonProperty("formats") val formats: List<Format> = listOf(),
    @JsonProperty("fulltitle") val fulltitle: String = "",
    @JsonProperty("description") val description: String = "",
    @JsonProperty("title") val title: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Format(
    @JsonProperty("format") val format: String = "",
    @JsonProperty("format_note") val formatNote: String = "",
    @JsonProperty("ext") val ext: String = "",
    @JsonProperty("filesize") val filesize: Long = 0,
    @JsonProperty("format_id") val formatId: String = ""
)
