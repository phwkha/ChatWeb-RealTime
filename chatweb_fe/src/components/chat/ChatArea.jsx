import React from 'react'
import ChatIcon from './ChatIcon.jsx'
import ChatHeader from './ChatHeader.jsx'
import MessageStream from './MessageStream.jsx'
import MessageComposer from './MessageComposer.jsx'
import MessageContextMenu from './MessageContextMenu.jsx'

export const ChatArea = React.memo(function ChatArea({
  activeSection,
  selectedUser,
  currentUser,
  connectionState,
  selectedConversationBlocked,
  selectedUserIsTyping,
  conversationMenuOpen,
  conversationMenuRef,
  latestWorldMessage,
  language,
  t,
  onOpenWorld,
  onBack,
  onToggleMenu,
  onOpenSearch,
  onUnblockConversation,
  onConfirmBlock,
  onConfirmUnfriend,
  onOpenReport,
  onOpenFriendsSection,
  messagesState,
  contextMenu,
  closeContextMenu,
  openContextMenu,
  showToast,
}) {
  const {
    conversationPages,
    loadingConversation,
    loadingOlderMessages,
    activeMessages,
    rateLimitRemaining,
    messageDraft,
    emojiPickerOpen,
    setEmojiPickerOpen,
    uploadingMedia,
    reactionPickerMessageId,
    setReactionPickerMessageId,
    reactionSubmittingId,
    detailMessageId,
    setDetailMessageId,
    editHistoryMessageId,
    setEditHistoryMessageId,
    messageEditHistory,
    editingMessageId,
    setEditingMessageId,
    editingMessageContent,
    setEditingMessageContent,
    messageActionPending,
    setRevokeTargetMessage,
    messageStreamRef,
    messagesEndRef,
    mediaInputRef,
    messageInputRef,
    loadOlderConversation,
    handleMessageStreamScroll,
    submitMessage,
    retryFailedMessage,
    handleMediaSelection,
    handleDraftChange,
    insertMessageEmoji,
    toggleReaction,
    beginMessageEdit,
    saveMessageEdit,
  } = messagesState

  return (
    <section className={`chat-main${activeSection !== 'chat' ? ' is-section-hidden' : ''}`}>
      <div className="world-ticker" role="region" aria-label={t('worldShort')}>
        <span className="world-ticker__icon">
          <ChatIcon name="globe" size={17} />
        </span>
        <strong>{t('worldShort')}</strong>
        <span className="world-ticker__viewport">
          <span
            className="world-ticker__track"
            key={`${latestWorldMessage?.timestamp || 'empty'}-${language}`}
          >
            <i>{latestWorldMessage?.sender || t('admin')}</i>
            <span>{latestWorldMessage?.content || t('worldEmpty')}</span>
          </span>
        </span>
      </div>

      {selectedUser ? (
        <>
          <ChatHeader
            selectedUser={selectedUser}
            connectionState={connectionState}
            conversationMenuOpen={conversationMenuOpen}
            conversationMenuRef={conversationMenuRef}
            selectedConversationBlocked={selectedConversationBlocked}
            isTyping={selectedUserIsTyping}
            t={t}
            onBack={onBack}
            onToggleMenu={onToggleMenu}
            onOpenSearch={onOpenSearch}
            onUnblock={onUnblockConversation}
            onConfirmBlock={onConfirmBlock}
            onConfirmUnfriend={onConfirmUnfriend}
            onOpenReport={onOpenReport}
          />

          <MessageStream
            messageStreamRef={messageStreamRef}
            onScroll={handleMessageStreamScroll}
            loadingConversation={loadingConversation}
            hasMore={conversationPages[selectedUser.username]?.hasMore}
            loadingOlderMessages={loadingOlderMessages}
            onLoadOlder={loadOlderConversation}
            activeMessages={activeMessages}
            selectedUser={selectedUser}
            user={currentUser}
            isTyping={selectedUserIsTyping}
            messagesEndRef={messagesEndRef}
            detailMessageId={detailMessageId}
            setDetailMessageId={setDetailMessageId}
            reactionPickerMessageId={reactionPickerMessageId}
            setReactionPickerMessageId={setReactionPickerMessageId}
            reactionSubmittingId={reactionSubmittingId}
            editHistoryMessageId={editHistoryMessageId}
            setEditHistoryMessageId={setEditHistoryMessageId}
            messageEditHistory={messageEditHistory}
            editingMessageId={editingMessageId}
            editingMessageContent={editingMessageContent}
            setEditingMessageId={setEditingMessageId}
            setEditingMessageContent={setEditingMessageContent}
            messageActionPending={messageActionPending}
            connectionState={connectionState}
            language={language}
            t={t}
            onContextMenu={openContextMenu}
            onToggleReaction={toggleReaction}
            onRetry={retryFailedMessage}
            onBeginEdit={beginMessageEdit}
            onSaveEdit={saveMessageEdit}
            onRevokeMessage={setRevokeTargetMessage}
          >
            <MessageContextMenu
              menu={contextMenu}
              user={currentUser}
              t={t}
              reactionSubmittingId={reactionSubmittingId}
              messageActionPending={messageActionPending}
              onClose={closeContextMenu}
              onToggleReaction={toggleReaction}
              onCopy={async (m) => {
                try {
                  if (!m?.content || !navigator.clipboard) throw new Error()
                  await navigator.clipboard.writeText(m.content)
                  closeContextMenu()
                  showToast(t('messageCopied'))
                } catch {
                  showToast(t('copyMessageFailed'), 'error')
                }
              }}
              onBeginEdit={beginMessageEdit}
              onRevoke={(m) => setRevokeTargetMessage(m)}
            />
          </MessageStream>

          <MessageComposer
            messageDraft={messageDraft}
            rateLimitRemaining={rateLimitRemaining}
            uploadingMedia={uploadingMedia}
            connectionState={connectionState}
            emojiPickerOpen={emojiPickerOpen}
            mediaInputRef={mediaInputRef}
            messageInputRef={messageInputRef}
            t={t}
            onSubmit={submitMessage}
            onDraftChange={handleDraftChange}
            onMediaSelect={handleMediaSelection}
            onToggleEmojiPicker={() => setEmojiPickerOpen((cur) => !cur)}
            onInsertEmoji={insertMessageEmoji}
          />
        </>
      ) : (
        <div className="chat-welcome">
          <div className="chat-welcome__art">
            <span>
              <ChatIcon name="chat" size={42} />
            </span>
            <i />
            <b />
          </div>
          <span className="chat-welcome__eyebrow">CHATWEB · REALTIME</span>
          <h2>
            {t('welcomeTitle')}, {currentUser?.firstName || currentUser?.username}!
          </h2>
          <p>{t('welcomeBody')}</p>
          <button type="button" onClick={onOpenFriendsSection}>
            <ChatIcon name="search" size={18} />
            {t('searchPeople')}
          </button>
        </div>
      )}
    </section>
  )
})

export default ChatArea
