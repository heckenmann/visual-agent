# UC 0000158: View Project Information and Open-Source Licenses

## Goal

Let users inspect Visual Agent's project identity, the version and runtime they are using, project/community links, and the license documents bundled with the current application build.

## Primary Actor

User.

## Preconditions

- The workspace is open and the About panel is available in panel navigation.
- The current application package includes its curated project and third-party legal documents.

## Main Flow

1. The user opens the About panel from the workspace rail or command palette.
2. The Overview tab displays the Visual Agent name, icon, build-derived application version, short project description, repository owner, project links, community documents present in the repository, and local runtime/operating-system information.
3. The user opens a project or community link with its external-link action. The UI obtains these URLs from canonical project metadata rather than defining them in composables.
4. The user selects the Visual Agent license action to open the Open-source licenses tab; the project license appears first when it matches the current filter.
5. The panel loads the legal documents bundled with this build through the application protocol.
6. The user filters the document list by component or package-relative path. The project license follows the same filter as third-party documents.
7. The user selects a document; its original bundled text appears in a scrollable, selectable viewer and can be copied using normal text selection.

The local version comes from the same packaged build resource used by update detection; determining the current version does not access GitHub. Optional community links are only listed when their project document exists. Runtime details are limited to Java runtime/version, operating-system name/version, and architecture; credentials, workspace paths, and private configuration are not part of the information contract.

## Failure Flow

If local build/runtime metadata or the bundled document index cannot be loaded, the panel shows a clear error instead of silently displaying an incorrect version or an incomplete license list. A missing optional community document is omitted rather than linked. The bundled legal documents provide access to retained license and notice texts; their presence does not claim that every shipped component has been reviewed, that source obligations are fulfilled, or that a release is approved.

## Tool Calls

- None.

## Code Entry Points

- `modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui/info/AboutPanel.kt`
- `modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui/info/OpenSourceLicensesPanel.kt`
- `modules/protocol/src/main/kotlin/de/heckenmann/visualagent/protocol/ProjectMetadata.kt`
- `modules/protocol/src/main/kotlin/de/heckenmann/visualagent/protocol/ApplicationInformationPort.kt`
- `application/src/main/kotlin/de/heckenmann/visualagent/server/RunningApplicationInformationPort.kt`
- `modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui/workspace/ComposeWorkspaceWindowBody.kt`
- `modules/protocol/src/main/kotlin/de/heckenmann/visualagent/protocol/OpenSourceLicensePort.kt`
- `application/src/main/kotlin/de/heckenmann/visualagent/server/BundledOpenSourceLicensePort.kt`
- `modules/ui/src/test/kotlin/de/heckenmann/visualagent/ui/info/OpenSourceLicensesPanelTest.kt`
- `application/src/test/kotlin/de/heckenmann/visualagent/server/BundledOpenSourceLicensePortTest.kt`
