package com.claudecode.navigator.frontend

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.ProjectActivity

@Service(Service.Level.PROJECT)
class ScrollService(private val project: Project) : Disposable {
    private val logger = Logger.getInstance(ScrollService::class.java)
    private var server: ScrollServer? = null

    fun start() {
        if (server != null) {
            logger.info("Scroll server already running for project: ${project.name}")
            return
        }
        server = ScrollServer(project).also {
            it.start()
            logger.info("Started scroll server for project: ${project.name}")
        }
    }

    fun stop() {
        server?.let {
            it.stop()
            logger.info("Stopped scroll server for project: ${project.name}")
        }
        server = null
    }

    override fun dispose() {
        stop()
    }

    companion object {
        fun getInstance(project: Project): ScrollService {
            return project.getService(ScrollService::class.java)
        }
    }
}

class FrontendStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ScrollService.getInstance(project).start()
    }
}

class FrontendProjectListener : ProjectManagerListener {
    override fun projectClosing(project: Project) {
        ScrollService.getInstance(project).stop()
    }
}
