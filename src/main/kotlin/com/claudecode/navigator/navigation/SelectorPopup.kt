package com.claudecode.navigator.navigation

import com.claudecode.navigator.model.NavigationTarget
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.wm.WindowManager
import javax.swing.Icon

class SelectorPopup(
    private val project: Project,
    private val targets: List<NavigationTarget>,
    private val onSelect: (NavigationTarget) -> Unit
) {
    fun show(): JBPopup {
        val step = NavigationTargetPopupStep(targets, onSelect)
        val popup = JBPopupFactory.getInstance()
            .createListPopup(step)

        // Show popup in the center of the IDE frame
        val frame = WindowManager.getInstance().getFrame(project)
        if (frame != null) {
            popup.showInCenterOf(frame)
        } else {
            popup.showInFocusCenter()
        }

        return popup
    }

    private class NavigationTargetPopupStep(
        private val targets: List<NavigationTarget>,
        private val onSelect: (NavigationTarget) -> Unit
    ) : BaseListPopupStep<NavigationTarget>("Select Target", targets) {

        override fun getTextFor(value: NavigationTarget): String {
            return value.displayName
        }

        override fun getIconFor(value: NavigationTarget): Icon? {
            return null
        }

        override fun onChosen(selectedValue: NavigationTarget, finalChoice: Boolean): PopupStep<*>? {
            if (finalChoice) {
                onSelect(selectedValue)
            }
            return FINAL_CHOICE
        }

        override fun isSpeedSearchEnabled(): Boolean = true

        override fun hasSubstep(selectedValue: NavigationTarget?): Boolean = false
    }
}
