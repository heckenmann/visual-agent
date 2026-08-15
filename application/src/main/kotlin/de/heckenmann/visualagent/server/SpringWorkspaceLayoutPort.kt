package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.LayoutPosition
import de.heckenmann.visualagent.protocol.LayoutSize
import de.heckenmann.visualagent.protocol.LayoutWindowState
import de.heckenmann.visualagent.protocol.WorkspaceLayoutPort
import de.heckenmann.visualagent.protocol.WorkspaceLayoutSnapshot
import de.heckenmann.visualagent.workspace.layout.DesktopState
import de.heckenmann.visualagent.workspace.layout.StagePosition
import de.heckenmann.visualagent.workspace.layout.StageState
import de.heckenmann.visualagent.workspace.layout.WorkspaceLayoutService
import de.heckenmann.visualagent.workspace.layout.WorkspaceWindowState
import org.springframework.stereotype.Component

/** Adapts the Spring workspace layout service to the transport-neutral protocol. */
@Component
class SpringWorkspaceLayoutPort(
    private val service: WorkspaceLayoutService,
) : WorkspaceLayoutPort {
    override fun report(): WorkspaceLayoutSnapshot =
        service.report().let { layout ->
            WorkspaceLayoutSnapshot(
                stage = layout.stage?.toProtocol(),
                stagePosition = layout.stagePosition?.toProtocol(),
                desktop = layout.desktop?.toProtocol(),
                windows = layout.windows.map(WorkspaceWindowState::toProtocol),
            )
        }

    override fun bind(
        stage: LayoutSize,
        desktop: LayoutSize,
        windows: List<LayoutWindowState>,
    ) {
        service.bind(stage.toApplicationStage(), desktop.toApplicationDesktop(), windows.map(LayoutWindowState::toApplication))
    }

    override fun applyWindowStates(
        states: List<LayoutWindowState>,
        notifyListeners: Boolean,
    ) {
        service.applyWindowStates(states.map(LayoutWindowState::toApplication), notifyListeners)
    }

    override fun saveStage(
        stage: LayoutSize,
        position: LayoutPosition?,
    ) {
        service.saveStage(stage.toApplicationStage(), position?.toApplicationStagePosition())
    }

    override fun addWindowStateListener(listener: (List<LayoutWindowState>) -> Unit): AutoCloseable =
        service.addWindowStateListener { states -> listener(states.map(WorkspaceWindowState::toProtocol)) }
}

private fun StageState.toProtocol() = LayoutSize(width, height)

private fun DesktopState.toProtocol() = LayoutSize(width, height)

private fun StagePosition.toProtocol() = LayoutPosition(x, y)

private fun WorkspaceWindowState.toProtocol() =
    LayoutWindowState(
        id = id,
        order = order,
        visible = visible,
        preferredWidth = preferredWidth,
    )

private fun LayoutSize.toApplicationStage() = StageState(width, height)

private fun LayoutSize.toApplicationDesktop() = DesktopState(width, height)

private fun LayoutPosition.toApplicationStagePosition() = StagePosition(x, y)

private fun LayoutWindowState.toApplication() =
    WorkspaceWindowState(
        id = id,
        order = order,
        visible = visible,
        preferredWidth = preferredWidth,
    )
