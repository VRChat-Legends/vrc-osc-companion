package com.vrchatlegends.osccompanion.vrcl

import com.vrchatlegends.osccompanion.scripts.CompanionScriptPolicy
import com.vrchatlegends.osccompanion.scripts.CompanionScriptRunner
import com.vrchatlegends.osccompanion.scripts.CompanionScriptStore
import com.vrchatlegends.osccompanion.scripts.InstalledCompanionScript
import com.vrchatlegends.osccompanion.scripts.ScriptRunnerState
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

/** Bytes pulled off a content:// URI by the UI layer, so this file stays free of Android APIs. */
data class PickedMedia(val bytes: ByteArray, val mimeType: String)

/** One attachment in the composer, uploaded as soon as it is picked. */
data class ComposerAttachment(
    val localId: String,
    val previewUri: String,
    val mimeType: String,
    val progress: Float = 0f,
    val uploaded: VrclMedia? = null,
    val error: String? = null,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val done: Boolean get() = uploaded != null
}

/** The open comment thread. Null [post] means the sheet is closed. */
data class ThreadState(
    val post: VrclPost? = null,
    val comments: List<VrclComment> = emptyList(),
    val loading: Boolean = false,
    val sending: Boolean = false,
    val replyTo: VrclComment? = null,
)

data class CommunityState(
    val section: CommunitySection = CommunitySection.SCRIPTS,
    val feedMode: FeedMode = FeedMode.FOR_YOU,
    val posts: List<VrclPost> = emptyList(),
    // Held back rather than spliced in, so the list never jumps under the user's finger.
    val pending: List<VrclPost> = emptyList(),
    val live: Boolean = false,
    val identity: VrclSocialIdentity? = null,
    val limits: VrclSocialLimits = VrclSocialLimits(),
    val attachments: List<ComposerAttachment> = emptyList(),
    val thread: ThreadState = ThreadState(),
    val following: Set<String> = emptySet(),
    val followBusy: Set<String> = emptySet(),
    val loading: Boolean = false,
    val posting: Boolean = false,
    val loaded: Boolean = false,
    val scripts: List<VrclScript> = emptyList(),
    val scriptSort: ScriptSort = ScriptSort.RECENT,
    val scriptQuery: String = "",
    val scriptsLoading: Boolean = false,
    val scriptsLoaded: Boolean = false,
    val installedScripts: List<InstalledCompanionScript> = emptyList(),
    val scriptLibraryLoaded: Boolean = false,
    val rejectedScriptFiles: Int = 0,
    val rejectedRemoteScripts: Int = 0,
    val scriptBusy: Set<String> = emptySet(),
    val scriptRunner: ScriptRunnerState = ScriptRunnerState(),
    val error: String? = null,
    val notice: String? = null,
) {
    val canPost: Boolean get() = identity?.canPost == true

    /** Blocks the post button while an attachment is still going up. */
    val attachmentsBusy: Boolean get() = attachments.any { it.uploaded == null && it.error == null }

    val canAttachMore: Boolean get() = attachments.size < MAX_ATTACHMENTS

    companion object {
        const val MAX_ATTACHMENTS = 4
    }
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
    private val readMedia: suspend (String) -> PickedMedia?,
    private val scriptStore: CompanionScriptStore,
    private val scriptRunner: CompanionScriptRunner,
) {
    private val _state = MutableStateFlow(CommunityState())
    val state: StateFlow<CommunityState> = _state.asStateFlow()

    private var liveJob: Job? = null

    init {
        scope.launch {
            scriptStore.state.collect { library ->
                _state.update {
                    it.copy(
                        installedScripts = library.scripts,
                        scriptLibraryLoaded = library.loaded,
                        rejectedScriptFiles = library.rejectedFiles,
                    )
                }
            }
        }
        scope.launch {
            scriptRunner.state.collect { runner ->
                _state.update { it.copy(scriptRunner = runner) }
            }
        }
        scope.launch {
            runCatching { scriptStore.reload() }.onFailure { error ->
                _state.update {
                    it.copy(
                        scriptLibraryLoaded = true,
                        error = error.message ?: "Could not open the private Scripts folder",
                    )
                }
            }
        }
    }

    fun loadOnce() {
        if (!_state.value.scriptsLoaded && !_state.value.scriptsLoading) refreshScripts()
    }

    fun selectSection(section: CommunitySection) {
        if (section == CommunitySection.SOCIAL) return
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

                    // Only meaningful while a thread is open; the feed only carries a count.
                    is VrclLiveFeed.Event.NewComment ->
                        if (_state.value.thread.post?.id == event.postId) refreshThread()

                    is VrclLiveFeed.Event.CommentDeleted -> _state.update { current ->
                        if (current.thread.post?.id != event.postId) current
                        else current.copy(
                            thread = current.thread.copy(
                                comments = current.thread.comments.filterNot { it.id == event.commentId },
                            ),
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
            val limits = if (isSignedIn()) client.socialLimits().getOrNull() else null
            client.socialFeed(mode = _state.value.feedMode.id)
                .onSuccess { posts ->
                    _state.update {
                        it.copy(
                            posts = posts,
                            pending = emptyList(),
                            identity = identity,
                            limits = limits ?: it.limits,
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
                            limits = limits ?: it.limits,
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
                    val safe = scripts.filter { CompanionScriptPolicy.fromRemote(it, installedAtMs = 0L).isSuccess }
                    _state.update {
                        it.copy(
                            scripts = safe,
                            scriptsLoading = false,
                            scriptsLoaded = true,
                            rejectedRemoteScripts = scripts.size - safe.size,
                        )
                    }
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

    fun previewScript(script: VrclScript): Result<InstalledCompanionScript> =
        CompanionScriptPolicy.fromRemote(script, installedAtMs = 0L)

    fun installScript(script: VrclScript) {
        if (script.id in _state.value.scriptBusy) return
        scope.launch {
            _state.update {
                it.copy(scriptBusy = it.scriptBusy + script.id, error = null, notice = null)
            }
            scriptStore.install(script)
                .onSuccess { installed ->
                    _state.update { it.copy(notice = "Installed ${installed.title}") }
                    scope.launch { client.recordScriptInstall(script.id) }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "Could not install that script") }
                }
            _state.update { it.copy(scriptBusy = it.scriptBusy - script.id) }
        }
    }

    fun removeScript(script: InstalledCompanionScript) {
        if (script.sourceId in _state.value.scriptBusy) return
        if (_state.value.scriptRunner.runningScriptId == script.sourceId) {
            _state.update { it.copy(error = "Stop the script before removing it") }
            return
        }
        scope.launch {
            _state.update {
                it.copy(scriptBusy = it.scriptBusy + script.sourceId, error = null, notice = null)
            }
            scriptStore.remove(script.sourceId)
                .onSuccess { _state.update { it.copy(notice = "Removed ${script.title}") } }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "Could not remove that script") }
                }
            _state.update { it.copy(scriptBusy = it.scriptBusy - script.sourceId) }
        }
    }

    fun runScript(script: InstalledCompanionScript) {
        scriptRunner.start(script.sourceId)
    }

    fun stopScript() {
        scriptRunner.cancel()
    }

    fun post(body: String, onPosted: () -> Unit) {
        val playerId = _state.value.identity?.playerId
        val text = body.trim()
        val ready = _state.value.attachments.mapNotNull { it.uploaded }
        if ((text.isEmpty() && ready.isEmpty()) || _state.value.posting) return
        if (_state.value.attachmentsBusy) {
            _state.update { it.copy(error = "Wait for the upload to finish") }
            return
        }
        if (playerId.isNullOrBlank()) {
            _state.update { it.copy(error = "Link a Legend profile on the website before posting") }
            return
        }
        scope.launch {
            _state.update { it.copy(posting = true, error = null, notice = null) }
            client.createPost(playerId, text, ready)
                .onSuccess {
                    _state.update {
                        it.copy(posting = false, attachments = emptyList(), notice = "Posted to your feed")
                    }
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

    // ---------------------------------------------------------------- attachments

    /**
     * Takes a picked content URI, checks it against the account's tier ceiling, then uploads it
     * straight away so the post itself is instant once the user hits send.
     */
    fun attach(uri: String) {
        val playerId = _state.value.identity?.playerId
        if (playerId.isNullOrBlank()) {
            _state.update { it.copy(error = "Link a Legend profile on the website before posting") }
            return
        }
        if (!_state.value.canAttachMore) {
            _state.update { it.copy(error = "Up to ${CommunityState.MAX_ATTACHMENTS} attachments per post") }
            return
        }
        val localId = uri + ":" + System.nanoTime()
        scope.launch {
            val picked = runCatching { readMedia(uri) }.getOrNull()
            if (picked == null) {
                _state.update { it.copy(error = "Could not read that file") }
                return@launch
            }
            val limits = _state.value.limits
            val isVideo = picked.mimeType.startsWith("video/")
            if (isVideo && !limits.canUploadVideo) {
                _state.update { it.copy(error = "Video posting is Gold Legend and above") }
                return@launch
            }
            val ceiling = if (isVideo) limits.maxVideoBytes else limits.maxImageBytes
            if (picked.bytes.size > ceiling) {
                val mb = ceiling / (1024 * 1024)
                _state.update { it.copy(error = "That file is over your $mb MB limit") }
                return@launch
            }

            _state.update { current ->
                current.copy(
                    attachments = current.attachments + ComposerAttachment(
                        localId = localId,
                        previewUri = uri,
                        mimeType = picked.mimeType,
                    ),
                    error = null,
                )
            }
            client.uploadMedia(playerId, picked.bytes, picked.mimeType) { fraction ->
                _state.update { current ->
                    current.copy(
                        attachments = current.attachments.map {
                            if (it.localId == localId) it.copy(progress = fraction) else it
                        },
                    )
                }
            }
                .onSuccess { media ->
                    _state.update { current ->
                        current.copy(
                            attachments = current.attachments.map {
                                if (it.localId == localId) it.copy(uploaded = media, progress = 1f) else it
                            },
                        )
                    }
                }
                .onFailure { error ->
                    val message = error.message ?: "Upload failed"
                    _state.update { current ->
                        current.copy(
                            attachments = current.attachments.map {
                                if (it.localId == localId) it.copy(error = message) else it
                            },
                            error = message,
                        )
                    }
                }
        }
    }

    fun removeAttachment(localId: String) {
        _state.update { current ->
            current.copy(attachments = current.attachments.filterNot { it.localId == localId })
        }
    }

    // ---------------------------------------------------------------- comments

    fun openThread(post: VrclPost) {
        _state.update { it.copy(thread = ThreadState(post = post, loading = true)) }
        refreshThread()
    }

    fun closeThread() {
        _state.update { it.copy(thread = ThreadState()) }
    }

    fun setReplyTo(comment: VrclComment?) {
        _state.update { it.copy(thread = it.thread.copy(replyTo = comment)) }
    }

    fun refreshThread() {
        val post = _state.value.thread.post ?: return
        scope.launch {
            _state.update { it.copy(thread = it.thread.copy(loading = true)) }
            client.comments(post.playerId, post.id)
                .onSuccess { list ->
                    _state.update { current ->
                        // Guard against the sheet having been closed or swapped mid flight.
                        if (current.thread.post?.id != post.id) current
                        else current.copy(thread = current.thread.copy(comments = list, loading = false))
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            thread = it.thread.copy(loading = false),
                            error = error.message ?: "Could not load comments",
                        )
                    }
                }
        }
    }

    fun sendComment(body: String) {
        val post = _state.value.thread.post ?: return
        val text = body.trim()
        if (text.isEmpty() || _state.value.thread.sending) return
        if (!isSignedIn()) {
            _state.update { it.copy(error = "Sign in on the Account tab to comment") }
            return
        }
        val parentId = _state.value.thread.replyTo?.id
        scope.launch {
            _state.update { it.copy(thread = it.thread.copy(sending = true), error = null) }
            client.createComment(post.playerId, post.id, text, parentId)
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            thread = current.thread.copy(sending = false, replyTo = null),
                            // Keep the feed's count honest without a full reload.
                            posts = current.posts.map {
                                if (it.id == post.id) it.copy(commentCount = it.commentCount + 1) else it
                            },
                        )
                    }
                    refreshThread()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            thread = it.thread.copy(sending = false),
                            error = error.message ?: "Could not post that comment",
                        )
                    }
                }
        }
    }

    fun likeComment(comment: VrclComment) {
        val post = _state.value.thread.post ?: return
        if (!isSignedIn()) {
            _state.update { it.copy(error = "Sign in on the Account tab to like comments") }
            return
        }
        _state.update { current ->
            current.copy(
                thread = current.thread.copy(
                    comments = current.thread.comments.map {
                        if (it.id != comment.id) it
                        else it.copy(
                            likedByMe = !it.likedByMe,
                            likeCount = (it.likeCount + if (it.likedByMe) -1 else 1).coerceAtLeast(0),
                        )
                    },
                ),
            )
        }
        scope.launch {
            client.likeComment(post.playerId, post.id, comment.id).onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Could not like that comment") }
            }
        }
    }

    fun deleteComment(comment: VrclComment) {
        val post = _state.value.thread.post ?: return
        scope.launch {
            client.deleteComment(post.playerId, post.id, comment.id)
                .onSuccess { refreshThread() }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "Could not delete that comment") }
                }
        }
    }

    // ---------------------------------------------------------------- follows

    /** Asks the server once per author so the button never shows a stale state. */
    fun ensureFollowState(playerId: String) {
        if (!isSignedIn()) return
        if (playerId in _state.value.following || playerId in _state.value.followBusy) return
        scope.launch {
            client.followStatus(playerId).onSuccess { status ->
                if (status.following) {
                    _state.update { it.copy(following = it.following + playerId) }
                }
            }
        }
    }

    fun toggleFollow(playerId: String) {
        if (!isSignedIn()) {
            _state.update { it.copy(error = "Sign in on the Account tab to follow Legends") }
            return
        }
        if (playerId == _state.value.identity?.playerId) return
        if (playerId in _state.value.followBusy) return
        val wasFollowing = playerId in _state.value.following
        _state.update { current ->
            current.copy(
                following = if (wasFollowing) current.following - playerId else current.following + playerId,
                followBusy = current.followBusy + playerId,
            )
        }
        scope.launch {
            val result = if (wasFollowing) client.unfollow(playerId) else client.follow(playerId)
            result.onFailure { error ->
                // Put the button back where it was; the optimistic flip was wrong.
                _state.update { current ->
                    current.copy(
                        following = if (wasFollowing) current.following + playerId else current.following - playerId,
                        error = error.message ?: "Could not update follow",
                    )
                }
            }
            _state.update { it.copy(followBusy = it.followBusy - playerId) }
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
        scriptRunner.clearMessage()
    }

    fun onSignedOut() {
        _state.update {
            it.copy(
                identity = null,
                following = emptySet(),
                attachments = emptyList(),
                limits = VrclSocialLimits(),
            )
        }
    }
}
