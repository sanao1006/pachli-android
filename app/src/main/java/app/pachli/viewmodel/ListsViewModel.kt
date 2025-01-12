/*
 * Copyright 2024 Pachli Association
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
 * You should have received a copy of the GNU General Public License along with Pachli; if not,
 * see <http://www.gnu.org/licenses>.
 */

package app.pachli.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.data.repository.ListsError
import app.pachli.core.data.repository.ListsRepository
import com.github.michaelbull.result.onFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface Error : ListsError {
    val title: String

    data class Create(override val title: String, private val error: ListsError.Create) : Error, ListsError by error

    data class Delete(override val title: String, private val error: ListsError.Delete) : Error, ListsError by error

    data class Update(override val title: String, private val error: ListsError.Update) : Error, ListsError by error
}

@HiltViewModel
internal class ListsViewModel @Inject constructor(
    private val listsRepository: ListsRepository,
) : ViewModel() {
    private val _errors = Channel<Error>()
    val errors = _errors.receiveAsFlow()

    val lists = listsRepository.lists

    init {
        listsRepository.refresh()
    }

    fun refresh() = viewModelScope.launch {
        listsRepository.refresh()
    }

    fun createNewList(title: String, exclusive: Boolean) = viewModelScope.launch {
        listsRepository.createList(title, exclusive).onFailure {
            _errors.send(Error.Create(title, it))
        }
    }

    fun updateList(listId: String, title: String, exclusive: Boolean) = viewModelScope.launch {
        listsRepository.editList(listId, title, exclusive).onFailure {
            _errors.send(Error.Update(title, it))
        }
    }

    fun deleteList(listId: String, title: String) = viewModelScope.launch {
        listsRepository.deleteList(listId).onFailure {
            _errors.send(Error.Delete(title, it))
        }
    }
}
