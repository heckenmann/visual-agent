# UC-0000096: Group Consecutive Conversation Messages

## Goal

Make the conversation transcript denser and easier to scan by presenting adjacent messages from the same conversational author in one shared panel.

## Primary Actor

Desktop user.

## Preconditions

- The conversation panel contains persisted messages.

## Main Flow

1. The conversation timeline derives presentation groups from adjacent persisted entries.
2. Consecutive user messages form one user group; consecutive assistant messages form one assistant group.
3. A system, tool, or sub-agent entry ends the active group and renders as its own timeline item.
4. Each conversational group shows the author avatar and name in its first column and the chronological message contents in its second column.
5. Message actions, Markdown rendering, and message order remain available for every message within the group.

## Result

The transcript uses less repeated header space while retaining the complete content and actions of every message.

## Design Decision

The grouping is implemented as a local Compose presentation transformation. The library research found no maintained Compose Multiplatform chat UI dependency that fits the existing desktop conversation model without replacing the message list and its scrolling behavior.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.conversation.buildConversationTimeline`
- `de.heckenmann.visualagent.ui.conversation.ConversationMessageGroupRow`
- `de.heckenmann.visualagent.ui.conversation.ConversationPanel`

## Acceptance Criteria

- Adjacent user and assistant messages are grouped independently.
- Any system, tool, or sub-agent entry splits a group.
- User and assistant groups use distinct avatars and show their name beside the grouped content.
- Every grouped message remains selectable, renders Markdown, and exposes its applicable actions.
