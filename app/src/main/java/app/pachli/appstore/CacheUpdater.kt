package app.pachli.appstore

import app.pachli.db.AccountManager
import app.pachli.db.TimelineDao
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class CacheUpdater @Inject constructor(
    eventHub: EventHub,
    accountManager: AccountManager,
    timelineDao: TimelineDao,
    gson: Gson,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            eventHub.events.collect { event ->
                val accountId = accountManager.activeAccount?.id ?: return@collect
                when (event) {
                    is FavoriteEvent ->
                        timelineDao.setFavourited(accountId, event.statusId, event.favourite)
                    is ReblogEvent ->
                        timelineDao.setReblogged(accountId, event.statusId, event.reblog)
                    is BookmarkEvent ->
                        timelineDao.setBookmarked(accountId, event.statusId, event.bookmark)
                    is UnfollowEvent ->
                        timelineDao.removeAllByUser(accountId, event.accountId)
                    is StatusDeletedEvent ->
                        timelineDao.delete(accountId, event.statusId)
                    is PollVoteEvent -> {
                        val pollString = gson.toJson(event.poll)
                        timelineDao.setVoted(accountId, event.statusId, pollString)
                    }
                    is PinEvent ->
                        timelineDao.setPinned(accountId, event.statusId, event.pinned)
                }
            }
        }
    }

    fun stop() {
        this.scope.cancel()
    }
}
