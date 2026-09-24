# UC-0000096: Group Consecutive Conversation Messages

## Goal

Make the conversation transcript denser and easier to scan by presenting adjacent messages from the same conversational author in one shared panel.

## Primary Actor

Desktop user.

## Preconditions

- The conversation panel contains persisted messages.

## Main Flow

1. The conversation timeline groups adjacent persisted user entries.
2. Assistant turns remain separate so provider rounds, intermediate prose, and final answers keep their own stable identity.
3. A tool row with a typed parent ID is nested beneath that assistant turn in provider declaration order; legacy tool rows without a parent remain standalone.
4. A system or sub-agent entry renders as its own timeline item.
5. Each user group shows only the author avatar in its first column and the chronological message contents in its second column.
6. Each applicable message action (Edit, Retry, or Delete) is exposed through one compact contextual menu at its top-right corner; actions never consume a dedicated message row.

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

- Adjacent user messages are grouped, while assistant turns are never merged.
- A tool is grouped only when its persisted parent assistant-turn ID resolves to a structural assistant parent; row adjacency and metadata are not used to infer ownership.
- User groups use their avatar without repeating role labels beside grouped content.
- Every grouped message remains selectable, renders Markdown, and exposes its applicable actions without increasing the height of a one-line message.
