package com.vrchatlegends.osccompanion.vrcl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Community tab's three halves. */
enum class CommunitySection(val label: String) {
    SOCIAL("Social"),
    SCRIPTS("Scripts"),
    LEADERBOARD("Leaderboard"),
}

/** Mirrors the tabs on https://vrchatlegends.com/social so both clients feel the same. */
enum class FeedMode(val id: String, val label: String) {
    FOR_YOU("foryou", "For you"),
    FOLLOWING("following", "Following"),
    TRENDING("trending", "Trending"),
    LATEST("latest", "Latest"),
}

enum class ScriptSort(val id: String, val label: String) {
    RECENT("recent", "Newest"),
    POPULAR("popular", "Popular"),
}

data class CommunityState(
    val section: CommunitySection = CommunitySection.SOCIAL,
    val feedMode: FeedMode = FeedMode.FOR_YOU,
    val posts: List<VrclPost> = emptyList(),
    // Held back rather than spliced in, so the list never jumps under the user's finger.
    val pending: List<VrclPost> = emptyList(),
    val live: Boolean = false,
    val identity: VrclSocialIdentity? = null,
    val loading: Boolean = false,
    val posting: Boolean = false,
    val loaded: Boolean = false,
    val scripts: List<VrclScript> = emptyList(),
    val scriptSort: ScriptSort = ScriptSort.RECENT,
    val scriptQuery: String = "",
    val scriptsLoading: Boolean = false,
    val scriptsLoaded: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
) {
    val canPost: Boolean get() = identity?.canPost == true
}

/**
 * Backs the Community tab: the public social feed, live updates over the same WebSocket the
 * website uses, and the shared script library.
 */
class CommunityRepository(
    private val scope: CoroutineScope,
    private val client: VrclClient,
    private val live: VrclLiveFeed,
    private val isSignedIn: () -> Boolean,
) {
    private val _state = MutableStateFlow(CommunityState())
    val state: StateFlow<CommunityState> = _state.asStateFlow()

    private var liveJob: Job? = null

    fun loadOnce() {
        if (!_state.value.loaded && !_state.value.loading) refresh()
        startLive()
    }

    fun selectSection(section: CommunitySection) {
        _state.update { it.copy(section = section) }
        if (section == CommunitySection.SCRIPTS && !_state.value.scriptsLoaded) refreshScripts()
    }

    fun selectFeedMode(mode: FeedMode) {
        if (_state.value.feedMode == mode) return
        _state.update { it.copy(feedMode = mode, pending = emptyList()) }
        refresh()
    }

    /** Moves the held-back live posts into the timeline. */
    fun showPending() {
        _state.update { current ->
            val existing = current.posts.map { it.key }.toSet()
            current.copy(
                posts = current.pending.filterNot { it.key in existing } + current.posts,
                pending = emptyList(),
            )
        }
    }

    private fun startLive() {
        if (liveJob != null) return
        liveJob = scope.launch {
            live.events().collect { event ->
                when (event) {
                    is VrclLiveFeed.Event.Connected -> _state.update { it.copy(live = event.connected) }

                    is VrclLiveFeed.Event.NewPost -> _state.update { current ->
                        // Following and Trending are server ranked, so a raw new post does not
                        // belong in them. Own posts already appear via the composer.
                        val ranked = current.feedMode == FeedMode.FOLLOWING || current.feedMode == FeedMode.TRENDING
                        val mine = event.post.playerId == current.identity?.playerId
                        val known = current.posts.any { it.key == event.post.key } ||
                            current.pending.any { it.key == event.post.key }
                        if (ranked || mine || known) current
                        else current.copy(pending = (listOf(event.post) + current.pending).take(40))
                    }

                    is VrclLiveFeed.Event.Deleted -> _state.update { current ->
                        val gone = { p: VrclPost -> p.id == event.postId && p.playerId == event.playerId }
                        current.copy(
                            posts = current.posts.filterNot(gone),
                            pending = current.pending.filterNot(gone),
                        )
                    }

                    is VrclLiveFeed.Event.Like -> _state.update { current ->
                        current.copy(
                            posts = current.posts.map {
                                if (it.id == event.postId && it.playerId == event.playerId) {
                                    it.copy(likeCount = event.likeCount)
                                } else {
                                    it
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    fun refresh() {
        if (_state.value.loading) return
        scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val identity = if (isSignedIn()) client.socialIdentity().getOrNull() else null
            client.socialFeed(mode = _state.value.feedMode.id)
                .onSuccess { posts ->
                    _state.update {
                        it.copy(
                            posts = posts,
                            pending = emptyList(),
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

    fun setScriptSort(sort: ScriptSort) {
        if (_state.value.scriptSort == sort) return
        _state.update { it.copy(scriptSort = sort) }
        refreshScripts()
    }

    fun setScriptQuery(query: String) {
        _state.update { it.copy(scriptQuery = query) }
    }

    fun refreshScripts() {
        if (_state.value.scriptsLoading) return
        scope.launch {
            _state.update { it.copy(scriptsLoading = true, error = null) }
            client.communityScripts(_state.value.scriptSort.id, _state.value.scriptQuery)
                .onSuccess { scripts ->
                    _state.update { it.copy(scripts = scripts, scriptsLoading = false, scriptsLoaded = true) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            scriptsLoading = false,
                            scriptsLoaded = true,
                            error = error.message ?: "Could not load community scripts",
                        )
                    }
                }
        }
    }

    fun likeScript(script: VrclScript) {
        if (!isSignedIn()) {
            _state.update { it.copy(error = "Sign in on the Account tab to like a script") }
            return
        }
        _state.update { current ->
            current.copy(
                scripts = current.scripts.map {
                    if (it.id != script.id) it
                    else it.copy(
                        viewerLiked = !it.viewerLiked,
                        likeCount = (it.likeCount + if (it.viewerLiked) -1 else 1).coerceAtLeast(0),
                    )
                },
            )
        }
        scope.launch {
            client.likeScript(script.id).onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Could not like that script") }
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
        // Optimistic: the live socket and the next refresh both correct the count.
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
