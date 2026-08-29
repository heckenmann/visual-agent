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
4. Each conversational group shows only the author avatar in its first column and the chronological message contents in its second column.
5. Each applicable message action (Edit, Retry, or Delete) is exposed through one compact contextual menu at its top-right corner; actions never consume a dedicated message row.

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
- User and assistant groups use distinct avatars without repeating role labels beside the grouped content.
- Every grouped message remains selectable, renders Markdown, and exposes its applicable actions without increasing the height of a one-line message.
