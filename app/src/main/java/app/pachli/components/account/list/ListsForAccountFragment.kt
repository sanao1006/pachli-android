/* Copyright 2022 kyori19
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

package app.pachli.components.account.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import app.pachli.R
import app.pachli.components.account.list.ListsForAccountViewModel.Error
import app.pachli.components.account.list.ListsForAccountViewModel.FlowError
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.designsystem.R as DR
import app.pachli.databinding.FragmentListsForAccountBinding
import app.pachli.databinding.ItemAddOrRemoveFromListBinding
import app.pachli.util.BindingHolder
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Shows all the user's lists with a button to allow them to add/remove the given
 * account from each list.
 */
@AndroidEntryPoint
class ListsForAccountFragment : DialogFragment() {
    private val viewModel: ListsForAccountViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<ListsForAccountViewModel.Factory> { factory ->
                factory.create(requireArguments().getString(ARG_ACCOUNT_ID)!!)
            }
        },
    )

    private val binding by viewBinding(FragmentListsForAccountBinding::bind)

    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, DR.style.AppDialogFragmentStyle)
    }

    override fun onStart() {
        super.onStart()
        dialog?.apply {
            window?.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_lists_for_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.listsView.layoutManager = LinearLayoutManager(view.context)
        binding.listsView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.listsWithMembership.collectLatest(::bind)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.errors.collectLatest { error ->
                when (error) {
                    is Error.AddAccounts -> {
                        Snackbar.make(binding.root, R.string.failed_to_add_to_list, Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.action_retry) {
                                viewModel.addAccountToList(error.listId)
                            }
                            .show()
                    }
                    is Error.DeleteAccounts -> {
                        Snackbar.make(binding.root, R.string.failed_to_remove_from_list, Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.action_retry) {
                                viewModel.deleteAccountFromList(error.listId)
                            }
                            .show()
                    }
                }
            }
        }

        binding.doneButton.setOnClickListener {
            dismiss()
        }

        load()
    }

    private fun load() {
        binding.progressBar.show()
        binding.listsView.hide()
        binding.messageView.hide()
    }

    private fun bind(result: Result<ListsWithMembership, FlowError>) {
        result.onSuccess {
            when (it) {
                ListsWithMembership.Loading -> {
                    binding.progressBar.show()
                }
                is ListsWithMembership.Loaded -> {
                    binding.progressBar.hide()
                    if (it.listsWithMembership.isEmpty()) {
                        binding.messageView.show()
                        binding.messageView.setup(R.drawable.elephant_friend_empty, R.string.no_lists) {
                            load()
                        }
                    } else {
                        binding.listsView.show()
                        adapter.submitList(it.listsWithMembership.values.toList())
                    }
                }
            }
        }

        result.onFailure {
            binding.progressBar.hide()
            binding.listsView.hide()
            binding.messageView.apply {
                show()
                setup(it.throwable) {
                    viewModel.refresh()
                    load()
                }
            }
        }
    }

    private object Differ : DiffUtil.ItemCallback<ListWithMembership>() {
        override fun areItemsTheSame(
            oldItem: ListWithMembership,
            newItem: ListWithMembership,
        ): Boolean {
            return oldItem.list.id == newItem.list.id
        }

        override fun areContentsTheSame(
            oldItem: ListWithMembership,
            newItem: ListWithMembership,
        ): Boolean {
            return oldItem == newItem
        }
    }

    inner class Adapter :
        ListAdapter<ListWithMembership, BindingHolder<ItemAddOrRemoveFromListBinding>>(Differ) {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): BindingHolder<ItemAddOrRemoveFromListBinding> {
            val binding =
                ItemAddOrRemoveFromListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return BindingHolder(binding)
        }

        override fun onBindViewHolder(holder: BindingHolder<ItemAddOrRemoveFromListBinding>, position: Int) {
            val item = getItem(position)
            holder.binding.listNameView.text = item.list.title
            holder.binding.addButton.apply {
                visible(!item.isMember)
                setOnClickListener {
                    viewModel.addAccountToList(item.list.id)
                }
            }
            holder.binding.removeButton.apply {
                visible(item.isMember)
                setOnClickListener {
                    viewModel.deleteAccountFromList(item.list.id)
                }
            }
        }
    }

    companion object {
        /** The ID of the account to add/remove the lists */
        private const val ARG_ACCOUNT_ID = "accountId"

        fun newInstance(accountId: String): ListsForAccountFragment {
            val args = Bundle().apply {
                putString(ARG_ACCOUNT_ID, accountId)
            }
            return ListsForAccountFragment().apply { arguments = args }
        }
    }
}
