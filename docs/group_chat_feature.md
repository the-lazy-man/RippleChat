# Group Chat Feature Architecture

## Overview
The Group Chat feature in RippleChat allows users to create multi-person conversations, share messages/media, 
and manage group properties like icons and participant lists. It is built natively on top of the existing Firestore 1-on-1 chat schema.

## Database Schema (Firestore)
- **chats (Sub-collection under `users/{uid}/chats`)**
  - `peerUid`: For groups, this is identical to the `groupId` (e.g., UUID).
  - `isGroup`: `true` (Boolean flag differentiating it from direct messages).
  - `groupName`: Name of the group.
  - `groupIcon`: URL of the group's avatar (uploaded and stored in Cloudinary).
  - `adminUid`: The UID of the creator who holds admin privileges (e.g., kicking users).
  - `participants`: Array of Strings containing UIDs of all group members.

## Flow & Implementation

### 1. Group Creation (`NewGroupScreen.kt`)
- Users select contacts (excluding existing groups) from their cached chat list.
- A unique `groupId` (UUID) is generated.
- If a group icon is selected, it's uploaded synchronously via `CloudinaryUploadHelper`.
- The app iterates over all selected participants (including the creator) and creates identical metadata documents in each user's `chats`
- sub-collection to ensure the group immediately appears on their Dashboard.

### 2. Message Broadcasting (`ChatRepository.kt` & `FirebaseSource.kt`)
- When a message is sent in a group, `ChatRepository` fetches the sender's name and attaches `senderName`
- directly to the Firestore message payload. This prevents needing multiple async queries when rendering the chat UI.
- The `updateChatMetadata` function fetches the sender's `chats` document. If `isGroup` is true, it extracts the 
- `participants` array and broadcasts the `lastMessage` and `lastTimestamp` to all members, while incrementing the `unreadCount` 
- for everyone *except* the sender.

### 3. UI Display & Interaction (`ChatScreen.kt` & `ChatVM.kt`)
- **Metadata State**: `ChatVM` listens to the chat document. When updated, it asynchronously resolves the
- display names of all `participants` and stores them in a `participantNames` Map.
- **TopAppBar**: Uses `isGroup` to render the group icon and options menu.
- **Message Bubbles**: Uses the resolved `participantNames` (or the cached `senderName` in the payload) to display the sender's name above 
- incoming group messages (like WhatsApp).
- **Group Settings (Admin Controls)**: The TopAppBar includes an options menu for groups:
  - **Group Info**: Displays a dialog with participant names. If the current user matches the `adminUid`, a "Remove" (X) button is
  - rendered next to other members, triggering a Firestore update to splice them from the array.
  - **Change Icon**: Launches an image picker, uploads the new image to Cloudinary, and concurrently updates the `groupIcon` field 
  - for all participants in the database.

## Future Enhancements
- **In-App Camera for QR/Media**: Transition from external Intent-based scanners (like ZXing `ScanContract`) to fully
- embedded Compose CameraX implementations to keep the UI strictly within the app frame.
- **Add Participants**: Implement an "Add Member" flow for admins to append UIDs to the `participants` array.
