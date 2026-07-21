package com.claudecode.navigator

import com.claudecode.navigator.server.TcpServer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.ProjectActivity

/**
 * Service that manages the TCP server lifecycle for a project.
 */
@Service(Service.Level.PROJECT)
class NavigatorService(private val project: Project) : Disposable {
    private val logger = Logger.getInstance(NavigatorService::class.java)
    private var tcpServer: TcpServer? = null

    fun startServer() {
        if (tcpServer?.isRunning() == true) {
            logger.info("TCP server already running for project: ${project.name}")
            return
        }

        tcpServer = TcpServer(project).also {
            it.start()
            logger.info("Started TCP server for project: ${project.name}")
        }
    }

    fun stopServer() {
        tcpServer?.let {
            it.stop()
            logger.info("Stopped TCP server for project: ${project.name}")
        }
        tcpServer = null
    }

    fun isServerRunning(): Boolean = tcpServer?.isRunning() == true

    override fun dispose() {
        stopServer()
    }

    companion object {
        fun getInstance(project: Project): NavigatorService {
            return project.getService(NavigatorService::class.java)
        }
    }
}

/**
 * Startup activity that initializes the Navigator service when a project opens.
 */
class NavigatorStartupActivity : ProjectActivity {
    private val logger = Logger.getInstance(NavigatorStartupActivity::class.java)

    override suspend fun execute(project: Project) {
        logger.info("Navigator startup activity executing for project: ${project.name}")
        NavigatorService.getInstance(project).startServer()
        ReadOnlyEditorService.getInstance(project).start()
    }
}

/**
 * Listener that handles project open/close events.
 */
class NavigatorProjectListener : ProjectManagerListener {
    private val logger = Logger.getInstance(NavigatorProjectListener::class.java)

    override fun projectOpened(project: Project) {
        logger.info("Project opened: ${project.name}")
        // Server is started by NavigatorStartupActivity
    }

    override fun projectClosing(project: Project) {
        logger.info("Project closing: ${project.name}")
        ReadOnlyEditorService.getInstance(project).stop()
        NavigatorService.getInstance(project).stopServer()
    }
}
