/* Copyright 2018 Conny Duck
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

package app.pachli.components.preference

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import app.pachli.BaseActivity
import app.pachli.MainActivity
import app.pachli.R
import app.pachli.appstore.EventHub
import app.pachli.appstore.PreferenceChangedEvent
import app.pachli.databinding.ActivityPreferencesBinding
import app.pachli.settings.PrefKeys
import app.pachli.settings.PrefKeys.APP_THEME
import app.pachli.util.APP_THEME_DEFAULT
import app.pachli.util.getNonNullString
import app.pachli.util.setAppNightMode
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.launch
import javax.inject.Inject

class PreferencesActivity :
    BaseActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    HasAndroidInjector {

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var androidInjector: DispatchingAndroidInjector<Any>

    private val restartActivitiesOnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            /* Switching themes won't actually change the theme of activities on the back stack.
             * Either the back stack activities need to all be recreated, or do the easier thing, which
             * is hijack the back button press and use it to launch a new MainActivity and clear the
             * back stack. */
            val intent = Intent(this@PreferencesActivity, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivityWithSlideInAnimation(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityPreferencesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        val preferenceType = intent.getIntExtra(EXTRA_PREFERENCE_TYPE, 0)

        val fragmentTag = "preference_fragment_$preferenceType"

        val fragment: Fragment = supportFragmentManager.findFragmentByTag(fragmentTag)
            ?: when (preferenceType) {
                GENERAL_PREFERENCES -> PreferencesFragment.newInstance()
                ACCOUNT_PREFERENCES -> AccountPreferencesFragment.newInstance()
                NOTIFICATION_PREFERENCES -> NotificationPreferencesFragment.newInstance()
                else -> throw IllegalArgumentException("preferenceType not known")
            }

        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment, fragmentTag)
        }

        onBackPressedDispatcher.addCallback(this, restartActivitiesOnBackPressedCallback)
        restartActivitiesOnBackPressedCallback.isEnabled = intent.extras?.getBoolean(
            EXTRA_RESTART_ON_BACK,
        ) ?: savedInstanceState?.getBoolean(EXTRA_RESTART_ON_BACK, false) ?: false
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference,
    ): Boolean {
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment!!,
        )
        fragment.arguments = args
        fragment.setTargetFragment(caller, 0)
        supportFragmentManager.commit {
            setCustomAnimations(
                R.anim.slide_from_right,
                R.anim.slide_to_left,
                R.anim.slide_from_left,
                R.anim.slide_to_right,
            )
            replace(R.id.fragment_container, fragment)
            addToBackStack(null)
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun saveInstanceState(outState: Bundle) {
        outState.putBoolean(EXTRA_RESTART_ON_BACK, restartActivitiesOnBackPressedCallback.isEnabled)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(EXTRA_RESTART_ON_BACK, restartActivitiesOnBackPressedCallback.isEnabled)
        super.onSaveInstanceState(outState)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            APP_THEME -> {
                val theme = sharedPreferences.getNonNullString(APP_THEME, APP_THEME_DEFAULT)
                Log.d("activeTheme", theme)
                setAppNightMode(theme)

                restartActivitiesOnBackPressedCallback.isEnabled = true
                this.restartCurrentActivity()
            }
            PrefKeys.FONT_FAMILY, PrefKeys.UI_TEXT_SCALE_RATIO -> {
                restartActivitiesOnBackPressedCallback.isEnabled = true
                this.restartCurrentActivity()
            }
            PrefKeys.STATUS_TEXT_SIZE, PrefKeys.ABSOLUTE_TIME_VIEW, PrefKeys.SHOW_BOT_OVERLAY, PrefKeys.ANIMATE_GIF_AVATARS, PrefKeys.USE_BLURHASH,
            PrefKeys.SHOW_SELF_USERNAME, PrefKeys.SHOW_CARDS_IN_TIMELINES, PrefKeys.CONFIRM_REBLOGS, PrefKeys.CONFIRM_FAVOURITES,
            PrefKeys.ENABLE_SWIPE_FOR_TABS, PrefKeys.MAIN_NAV_POSITION, PrefKeys.HIDE_TOP_TOOLBAR, PrefKeys.SHOW_STATS_INLINE,
            -> {
                restartActivitiesOnBackPressedCallback.isEnabled = true
            }
        }
        lifecycleScope.launch {
            eventHub.dispatch(PreferenceChangedEvent(key))
        }
    }

    private fun restartCurrentActivity() {
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        val savedInstanceState = Bundle()
        saveInstanceState(savedInstanceState)
        intent.putExtras(savedInstanceState)
        startActivityWithSlideInAnimation(intent)
        finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    override fun androidInjector() = androidInjector

    companion object {
        @Suppress("unused")
        private const val TAG = "PreferencesActivity"
        const val GENERAL_PREFERENCES = 0
        const val ACCOUNT_PREFERENCES = 1
        const val NOTIFICATION_PREFERENCES = 2
        private const val EXTRA_PREFERENCE_TYPE = "EXTRA_PREFERENCE_TYPE"
        private const val EXTRA_RESTART_ON_BACK = "restart"

        @JvmStatic
        fun newIntent(context: Context, preferenceType: Int): Intent {
            val intent = Intent(context, PreferencesActivity::class.java)
            intent.putExtra(EXTRA_PREFERENCE_TYPE, preferenceType)
            return intent
        }
    }
}