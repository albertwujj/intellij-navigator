package com.claudecode.navigator.vcs

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository

class ShowNetDiffAgainstPreviousCommitAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.project
        val hasGitChanges = project != null && selectedChanges(event).any { change ->
            val filePath = netFilePath(change)
            filePath != null && !filePath.isDirectory && GitUtil.isUnderGit(filePath)
        }

        event.presentation.isEnabledAndVisible = hasGitChanges
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val changes = selectedChanges(event).filter { change ->
            val filePath = netFilePath(change)
            filePath != null && !filePath.isDirectory
        }

        object : Task.Backgroundable(project, "Preparing Net Diff Since Previous Commit", true) {
            private val errors = mutableListOf<String>()
            private var netChanges: List<Change> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                val hasParentByRepository = mutableMapOf<GitRepository, Boolean>()
                netChanges = changes.mapNotNull { change ->
                    indicator.checkCanceled()
                    buildNetChange(change, hasParentByRepository)
                }
            }

            override fun onSuccess() {
                if (project.isDisposed) return

                when {
                    netChanges.isNotEmpty() -> ShowDiffAction.showDiffForChange(project, netChanges)
                    errors.isNotEmpty() -> Messages.showErrorDialog(
                        project,
                        errors.joinToString("\n"),
                        "Net Diff Since Previous Commit",
                    )
                    else -> Messages.showInfoMessage(
                        project,
                        "Selected changes have no net difference from the version before the last commit.",
                        "Net Diff Since Previous Commit",
                    )
                }
            }

            override fun onThrowable(error: Throwable) {
                if (!project.isDisposed) {
                    Messages.showErrorDialog(
                        project,
                        error.message ?: error.javaClass.simpleName,
                        "Net Diff Since Previous Commit",
                    )
                }
            }

            private fun buildNetChange(
                change: Change,
                hasParentByRepository: MutableMap<GitRepository, Boolean>,
            ): Change? {
                val filePath = netFilePath(change) ?: return null
                val repository = findRepository(filePath) ?: return null
                val hasParent = hasParentByRepository.getOrPut(repository) {
                    Git.getInstance().resolveReference(repository, PARENT_REVISION) != null
                }

                val beforeRevision = createParentRevision(repository, filePath, hasParent)
                val afterRevision = createCurrentRevision(filePath)

                if (beforeRevision == null && afterRevision == null) return null
                return Change(beforeRevision, afterRevision)
            }

            private fun findRepository(filePath: FilePath): GitRepository? {
                return try {
                    GitUtil.getRepositoryForFile(project, filePath)
                } catch (e: VcsException) {
                    errors += "${filePath.presentableUrl}: ${e.message}"
                    null
                }
            }

            private fun createParentRevision(
                repository: GitRepository,
                filePath: FilePath,
                hasParent: Boolean,
            ): ContentRevision? {
                if (!hasParent) return null
                if (!existsAtParent(repository, filePath)) return null

                return GitContentRevision.createRevision(
                    filePath,
                    GitRevisionNumber(PARENT_REVISION),
                    project,
                )
            }

            private fun existsAtParent(repository: GitRepository, filePath: FilePath): Boolean {
                val relativePath = GitUtil.getRelativePath(repository.root.path, filePath)
                val handler = GitLineHandler(project, repository.root, GitCommand.CAT_FILE)
                handler.setSilent(true)
                handler.addParameters("-e", "$PARENT_REVISION:$relativePath")
                return Git.getInstance().runCommand(handler).success()
            }
        }.queue()
    }

    private fun createCurrentRevision(filePath: FilePath): ContentRevision? {
        if (!filePath.ioFile.exists()) return null
        return CurrentContentRevision.create(filePath)
    }

    private fun selectedChanges(event: AnActionEvent): List<Change> {
        return event.getData(VcsDataKeys.SELECTED_CHANGES)?.toList()
            ?: event.getData(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS)?.toList()
            ?: event.getData(VcsDataKeys.CHANGES)?.toList()
            ?: emptyList()
    }

    private fun netFilePath(change: Change): FilePath? {
        return change.afterRevision?.file ?: change.beforeRevision?.file
    }

    private companion object {
        const val PARENT_REVISION = "HEAD~1"
    }
}
