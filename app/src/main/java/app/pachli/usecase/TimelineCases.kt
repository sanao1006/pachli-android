/* Copyright 2018 charlag
 *
 * This file is a part of Pachli.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Pachli is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package app.pachli.usecase

import android.util.Log
import app.pachli.appstore.BlockEvent
import app.pachli.appstore.BookmarkEvent
import app.pachli.appstore.EventHub
import app.pachli.appstore.FavoriteEvent
import app.pachli.appstore.MuteConversationEvent
import app.pachli.appstore.MuteEvent
import app.pachli.appstore.PinEvent
import app.pachli.appstore.PollVoteEvent
import app.pachli.appstore.ReblogEvent
import app.pachli.appstore.StatusDeletedEvent
import app.pachli.entity.DeletedStatus
import app.pachli.entity.Poll
import app.pachli.entity.Relationship
import app.pachli.entity.Status
import app.pachli.network.MastodonApi
import app.pachli.util.getServerErrorMessage
import at.connyduck.calladapter.networkresult.NetworkResult
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.onFailure
import at.connyduck.calladapter.networkresult.onSuccess
import javax.inject.Inject

class TimelineCases @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val eventHub: EventHub,
) {

    suspend fun reblog(statusId: String, reblog: Boolean): NetworkResult<Status> {
        return if (reblog) {
            mastodonApi.reblogStatus(statusId)
        } else {
            mastodonApi.unreblogStatus(statusId)
        }.onSuccess {
            eventHub.dispatch(ReblogEvent(statusId, reblog))
        }
    }

    suspend fun favourite(statusId: String, favourite: Boolean): NetworkResult<Status> {
        return if (favourite) {
            mastodonApi.favouriteStatus(statusId)
        } else {
            mastodonApi.unfavouriteStatus(statusId)
        }.onSuccess {
            eventHub.dispatch(FavoriteEvent(statusId, favourite))
        }
    }

    suspend fun bookmark(statusId: String, bookmark: Boolean): NetworkResult<Status> {
        return if (bookmark) {
            mastodonApi.bookmarkStatus(statusId)
        } else {
            mastodonApi.unbookmarkStatus(statusId)
        }.onSuccess {
            eventHub.dispatch(BookmarkEvent(statusId, bookmark))
        }
    }

    suspend fun muteConversation(statusId: String, mute: Boolean): NetworkResult<Status> {
        return if (mute) {
            mastodonApi.muteConversation(statusId)
        } else {
            mastodonApi.unmuteConversation(statusId)
        }.onSuccess {
            eventHub.dispatch(MuteConversationEvent(statusId, mute))
        }
    }

    suspend fun mute(statusId: String, notifications: Boolean, duration: Int?) {
        try {
            mastodonApi.muteAccount(statusId, notifications, duration)
            eventHub.dispatch(MuteEvent(statusId))
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to mute account", t)
        }
    }

    suspend fun block(statusId: String) {
        try {
            mastodonApi.blockAccount(statusId)
            eventHub.dispatch(BlockEvent(statusId))
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to block account", t)
        }
    }

    suspend fun delete(statusId: String): NetworkResult<DeletedStatus> {
        return mastodonApi.deleteStatus(statusId)
            .onSuccess { eventHub.dispatch(StatusDeletedEvent(statusId)) }
            .onFailure { Log.w(TAG, "Failed to delete status", it) }
    }

    suspend fun pin(statusId: String, pin: Boolean): NetworkResult<Status> {
        return if (pin) {
            mastodonApi.pinStatus(statusId)
        } else {
            mastodonApi.unpinStatus(statusId)
        }.fold({ status ->
            eventHub.dispatch(PinEvent(statusId, pin))
            NetworkResult.success(status)
        }, { e ->
            Log.w(TAG, "Failed to change pin state", e)
            NetworkResult.failure(TimelineError(e.getServerErrorMessage()))
        },)
    }

    suspend fun voteInPoll(statusId: String, pollId: String, choices: List<Int>): NetworkResult<Poll> {
        if (choices.isEmpty()) {
            return NetworkResult.failure(IllegalStateException())
        }

        return mastodonApi.voteInPoll(pollId, choices).onSuccess { poll ->
            eventHub.dispatch(PollVoteEvent(statusId, poll))
        }
    }

    suspend fun acceptFollowRequest(accountId: String): NetworkResult<Relationship> {
        return mastodonApi.authorizeFollowRequest(accountId)
    }

    suspend fun rejectFollowRequest(accountId: String): NetworkResult<Relationship> {
        return mastodonApi.rejectFollowRequest(accountId)
    }

    companion object {
        private const val TAG = "TimelineCases"
    }
}

class TimelineError(message: String?) : RuntimeException(message)