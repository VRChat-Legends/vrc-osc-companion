package com.vrchatlegends.osccompanion.vrcl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommunityState(
    val posts: List<VrclPost> = emptyList(),
    val identity: VrclSocialIdentity? = null,
    val loading: Boolean = false,
    val posting: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

/** Backs the Community tab: the public social feed plus posting for signed-in Legends. */
class CommunityRepository(
    private val scope: CoroutineScope,
    private val client: VrclClient,
    private val isSignedIn: () -> Boolean,
) {
    private val _state = MutableStateFlow(CommunityState())
    val state: StateFlow<CommunityState> = _state.asStateFlow()

    fun loadOnce() {
        if (_state.value.loaded || _state.value.loading) return
        refresh()
    }

    fun refresh() {
        if (_state.value.loading) return
        scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val identity = if (isSignedIn()) client.socialIdentity().getOrNull() else null
            client.socialFeed()
                .onSuccess { posts ->
                    _state.update {
                        it.copy(
                            posts = posts,
                            identity = identity,
                            loading = false,
                            loaded = true,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            identity = identity,
                            loading = false,
                            loaded = true,
                            error = error.message ?: "Could not load the community feed",
                        )
                    }
                }
        }
    }

    fun post(body: String, onPosted: () -> Unit) {
        val playerId = _state.value.identity?.playerId
        val text = body.trim()
        if (text.isEmpty() || _state.value.posting) return
        if (playerId.isNullOrBlank()) {
            _state.update { it.copy(error = "Link a Legend profile on the website before posting") }
            return
        }
        scope.launch {
            _state.update { it.copy(posting = true, error = null, notice = null) }
            client.createPost(playerId, text)
                .onSuccess {
                    _state.update { it.copy(posting = false, notice = "Posted to your feed") }
                    onPosted()
                    refresh()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(posting = false, error = error.message ?: "Could not post")
                    }
                }
        }
    }

    fun like(post: VrclPost) {
        if (_state.value.identity?.canPost != true) {
            _state.update { it.copy(error = "Sign in on the Account tab to like posts") }
            return
        }
        // Optimistic: the feed reloads on the next refresh anyway.
        _state.update { current ->
            current.copy(
                posts = current.posts.map {
                    if (it.key != post.key) it
                    else it.copy(
                        viewerLiked = !it.viewerLiked,
                        likeCount = (it.likeCount + if (it.viewerLiked) -1 else 1).coerceAtLeast(0),
                    )
                },
            )
        }
        scope.launch {
            client.likePost(post.playerId, post.id).onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Could not like that post") }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, notice = null) }
    }

    fun onSignedOut() {
        _state.update { it.copy(identity = null) }
    }
}
