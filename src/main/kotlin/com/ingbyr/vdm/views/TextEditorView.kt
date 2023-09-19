package com.ingbyr.vdm.views

import com.ingbyr.vdm.controllers.TextEditorController
import com.ingbyr.vdm.events.RefreshCookieContent
import com.ingbyr.vdm.utils.FileEditorOption
import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXTextField
import javafx.scene.control.TextArea
import javafx.scene.layout.AnchorPane
import tornadofx.*
import java.util.*

class TextEditorView : View() {
    init {
        messages = ResourceBundle.getBundle("i18n/TextEditorView")
    }

    override val root: AnchorPane by fxml("/fxml/FileEditorView.fxml")
    private val controller: TextEditorController by inject()
    private var fileEditorOption = params["fileEditorOption"] as FileEditorOption

    private val contentTextArea: TextArea by fxid()
    private val fileNameTextField: JFXTextField by fxid()
    private val confirmButton: JFXButton by fxid()
    private val cancleButton: JFXButton by fxid()


    init {
        cancleButton.setOnMouseClicked {
            this.close()
        }
    }

    // create new file and edit it
    private fun initNewFileEditor() {
        fileNameTextField.isDisable = false
        fileNameTextField.text = fileEditorOption.extension
        contentTextArea.text = ""

        // add extension to filename
        fileNameTextField.focusedProperty().addListener { _, _, focused ->
            if (!focused) {
                if (!fileNameTextField.text.trim().endsWith(fileEditorOption.extension)) {
                    fileNameTextField.text += fileEditorOption.extension
                    fileNameTextField.text.trim()
                }
            }
        }

        // save file action
        confirmButton.setOnMouseClicked {
            if (textFieldIsNotBlank()) {

            }
        }
    }

    // edit existed file
    private fun initExistedFileEditor() {
        fileNameTextField.isDisable = true
        fileNameTextField.text = fileEditorOption.filePath.fileName.toString()
        contentTextArea.text = fileEditorOption.filePath.toFile().readText()
        confirmButton.setOnMouseClicked {
            if (textFieldIsNotBlank()) {

            }
        }
    }

    private fun textFieldIsNotBlank(): Boolean {
        return contentTextArea.text.isNotBlank() && fileNameTextField.text.isNotBlank()
    }

    override fun onDock() {
        fileEditorOption = params["fileEditorOption"] as FileEditorOption
        if (fileEditorOption.isNewFile) {
            initNewFileEditor()
        } else {
            initExistedFileEditor()
        }
    }
}