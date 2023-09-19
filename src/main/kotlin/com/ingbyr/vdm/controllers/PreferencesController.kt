package com.ingbyr.vdm.controllers

import com.ingbyr.vdm.engines.utils.EngineType
import com.ingbyr.vdm.events.UpdateEngineTask
import com.ingbyr.vdm.utils.Attributes
import com.ingbyr.vdm.utils.DebugUtils
import com.ingbyr.vdm.utils.config.update
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import tornadofx.*

class PreferencesController : Controller() {

    val debugModeProperty = SimpleBooleanProperty()
    var debugMode: Boolean by debugModeProperty
    var cookieList = mutableListOf<String>().observable()
    val cookieProperty = SimpleStringProperty()
    private var cookie by cookieProperty

    init {
        initDebugMode()
        freshCookieListAndContent()
    }

    private fun initDebugMode() {
        // enable debug default
        debugMode = app.config.boolean(Attributes.DEBUG_MODE, Attributes.Defaults.DEBUG_MODE)
        debugModeProperty.addListener { _, _, mode ->
            DebugUtils.changeDebugMode(mode)
            app.config.update(Attributes.DEBUG_MODE, mode)
        }
    }

    fun freshCookieListAndContent() {

    }

    fun readCookieContent() {
        // TODO save cookie to db
//        val cookieName = ConfigUtils.safeLoad(Attributes.CURRENT_COOKIE, "")
//        if (cookieName.isBlank()) cookie=  ""
//        val cookieFile = Attributes.COOKIES_DIR.resolve(cookieName).toFile()
//        cookie = if (cookieFile.exists()) {
//            cookieFile.readText()
//        } else {
//            ""
//        }
    }

    fun updateEngine(engineType: EngineType, localVersion: String) {
        fire(UpdateEngineTask(engineType, localVersion))
    }
}