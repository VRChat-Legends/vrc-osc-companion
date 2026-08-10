package com.vrchatlegends.osccompanion.vrchat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VrchatToolsState(
    val sessionChecked: Boolean = false,
    val user: VrchatUser? = null,
    val twoFactorMethods: List<String> = emptyList(),
    val friends: List<VrchatFriend> = emptyList(),
    val notifications: List<VrchatNotification> = emptyList(),
    val favoriteAvatars: List<VrchatAvatar> = emptyList(),
    val favoriteWorlds: List<VrchatWorld> = emptyList(),
    val busy: Boolean = false,
    val refreshing: Boolean = false,
    val activeAction: String? = null,
    val error: String? = null,
    val notice: String? = null,
)

class VrchatToolsRepository(
    private val scope: CoroutineScope,
    private val client: VrchatClient,
) {
    private val _state = MutableStateFlow(VrchatToolsState())
    val state: StateFlow<VrchatToolsState> = _state.asStateFlow()

    fun restoreSession() {
        if (_state.value.sessionChecked || _state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        scope.launch {
            client.restoreSession()
                .onSuccess { result ->
                    when (result) {
                        null -> _state.update { it.copy(sessionChecked = true, busy = false) }
                        is VrchatLoginResult.SignedIn -> onSignedIn(result.user)
                        is VrchatLoginResult.TwoFactorRequired -> _state.update {
                            it.copy(
                                sessionChecked = true,
                                busy = false,
                                twoFactorMethods = result.methods,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            sessionChecked = true,
                            busy = false,
                            error = error.message ?: "Could not restore the VRChat session",
                        )
                    }
                }
        }
    }

    fun signIn(username: String, password: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, notice = null) }
        scope.launch {
            client.login(username, password)
                .onSuccess { result ->
                    when (result) {
                        is VrchatLoginResult.SignedIn -> onSignedIn(result.user)
                        is VrchatLoginResult.TwoFactorRequired -> _state.update {
                            it.copy(
                                sessionChecked = true,
                                busy = false,
                                twoFactorMethods = result.methods,
                                notice = "Enter your VRChat two-factor code",
                            )
                        }
                    }
                }
                .onFailure(::showAuthError)
        }
    }

    fun verifyTwoFactor(code: String) {
        val methods = _state.value.twoFactorMethods
        if (_state.value.busy || methods.isEmpty()) return
        _state.update { it.copy(busy = true, error = null, notice = null) }
        scope.launch {
            client.verifyTwoFactor(code, methods)
                .onSuccess { user -> onSignedIn(user) }
                .onFailure(::showAuthError)
        }
    }

    fun cancelSignIn() {
        scope.launch {
            client.logout()
            _state.value = VrchatToolsState(sessionChecked = true)
        }
    }

    fun signOut() {
        scope.launch {
            _state.update { it.copy(busy = true, error = null) }
            client.logout()
            _state.value = VrchatToolsState(
                sessionChecked = true,
                notice = "Signed out of VRChat",
            )
        }
    }

    fun refresh() {
        if (_state.value.user == null || _state.value.refreshing) return
        if (_state.value.activeAction != null) {
            _state.update { it.copy(notice = "Wait for the current VRChat action to finish") }
            return
        }
        scope.launch { refreshInternal() }
    }

    fun acceptFriendRequest(notification: VrchatNotification) = action(
        key = "accept:${notification.id}",
        successMessage = "Friend request accepted",
        request = { client.acceptFriendRequest(notification.id) },
        onSuccess = {
            _state.update { state ->
                state.copy(notifications = state.notifications.filterNot { it.id == notification.id })
            }
            refreshInternal()
        },
    )

    fun hideNotification(notification: VrchatNotification) = action(
        key = "hide:${notification.id}",
        successMessage = "Notification dismissed",
        request = { client.hideNotification(notification.id) },
        onSuccess = {
            _state.update { state ->
                state.copy(notifications = state.notifications.filterNot { it.id == notification.id })
            }
        },
    )

    fun requestInvite(friend: VrchatFriend) = action(
        key = "request:${friend.id}",
        successMessage = "Invite request sent to ${friend.displayName}",
        request = { client.requestInvite(friend.id) },
    )

    fun inviteFriend(friend: VrchatFriend) = action(
        key = "invite:${friend.id}",
        successMessage = "Invite sent to ${friend.displayName}",
        request = { client.invite(friend.id) },
    )

    fun joinFriend(friend: VrchatFriend) = action(
        key = "join:${friend.id}",
        successMessage = "Join invite sent to your VRChat notifications",
        request = { client.inviteMyselfTo(friend.location) },
    )

    fun joinInvite(notification: VrchatNotification) = action(
        key = "join-notification:${notification.id}",
        successMessage = "Join invite sent to your VRChat notifications",
        request = { client.joinInvite(notification.worldId, notification.instanceId) },
    )

    fun respondToInviteRequest(notification: VrchatNotification) {
        val senderId = notification.senderUserId
        if (senderId.isNullOrBlank()) {
            _state.update { it.copy(error = "This request has no sender account") }
            return
        }
        action(
            key = "invite-request:${notification.id}",
            successMessage = "Invite sent to ${notification.senderDisplayName}",
            request = { client.invite(senderId) },
        )
    }

    fun selectAvatar(avatar: VrchatAvatar) = action(
        key = "avatar:${avatar.id}",
        successMessage = "Equipped ${avatar.name}",
        request = { client.selectAvatar(avatar.id) },
    )

    fun clearNotice() {
        _state.update { it.copy(notice = null, error = null) }
    }

    private suspend fun onSignedIn(user: VrchatUser) {
        _state.update {
            it.copy(
                sessionChecked = true,
                user = user,
                twoFactorMethods = emptyList(),
                busy = false,
                error = null,
                notice = "Signed in as ${user.displayName}",
            )
        }
        refreshInternal()
    }

    private suspend fun refreshInternal() {
        if (_state.value.user == null) return
        _state.update { it.copy(refreshing = true, error = null) }
        val online = client.friends(offline = false)
        val offline = client.friends(offline = true)
        val notifications = client.notifications()
        val avatars = client.favoriteAvatars()
        val worlds = client.favoriteWorlds()
        val failedSections = buildList {
            if (online.isFailure || offline.isFailure) add("friends")
            if (notifications.isFailure) add("inbox")
            if (avatars.isFailure) add("avatars")
            if (worlds.isFailure) add("worlds")
        }
        _state.update { current ->
            current.copy(
                friends = if (online.isSuccess && offline.isSuccess) {
                    (online.getOrThrow() + offline.getOrThrow()).distinctBy(VrchatFriend::id)
                } else {
                    current.friends
                },
                notifications = notifications.getOrElse { current.notifications },
                favoriteAvatars = avatars.getOrElse { current.favoriteAvatars },
                favoriteWorlds = worlds.getOrElse { current.favoriteWorlds },
                refreshing = false,
                error = failedSections.takeIf { it.isNotEmpty() }?.let {
                    "Could not refresh ${it.joinToString(", ")}"
                },
            )
        }
    }

    private fun beginAction(key: String): Boolean {
        while (true) {
            val current = _state.value
            if (current.refreshing) {
                _state.compareAndSet(
                    current,
                    current.copy(notice = "Wait for the current refresh to finish"),
                )
                return false
            }
            if (current.activeAction != null) {
                _state.compareAndSet(
                    current,
                    current.copy(notice = "Wait for the current VRChat action to finish"),
                )
                return false
            }
            if (_state.compareAndSet(
                    current,
                    current.copy(activeAction = key, error = null, notice = null),
                )
            ) return true
        }
    }

    private fun action(
        key: String,
        successMessage: String,
        request: suspend () -> Result<Unit>,
        onSuccess: suspend () -> Unit = {},
    ) {
        if (!beginAction(key)) return
        scope.launch {
            request()
                .onSuccess {
                    _state.update { it.copy(activeAction = null, notice = successMessage) }
                    onSuccess()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            activeAction = null,
                            error = error.message ?: "VRChat action failed",
                        )
                    }
                }
        }
    }

    private fun showAuthError(error: Throwable) {
        _state.update {
            it.copy(
                sessionChecked = true,
                busy = false,
                error = error.message ?: "VRChat sign-in failed",
            )
        }
    }
}