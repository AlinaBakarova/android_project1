package ru.messenger.app

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.res.Configuration
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import ru.messenger.featurechats.api.ChatsFeature
import ru.messenger.featuremessages.api.MessagesFeature

class NavigationViewModel(private val saved: SavedStateHandle) : ViewModel() {
    var selectedId: Long? get() = saved["selectedId"]; set(value) { saved["selectedId"] = value }
    var selectedName: String get() = saved["selectedName"] ?: ""; set(value) { saved["selectedName"] = value }
}
class MainActivity : AppCompatActivity() {
    private val navigation: NavigationViewModel by viewModels()
    private val landscape get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        // Recreate the same feature fragments in their new containers. Activity-scoped
        // SavedStateHandle ViewModels retain data, drafts and search across rotation.
        val retainedStates = supportFragmentManager.fragments.filter { it.tag == "chats" || it.tag == "messages" }
            .associate { it.tag to supportFragmentManager.saveFragmentInstanceState(it) }
        supportFragmentManager.beginTransaction().apply {
            supportFragmentManager.fragments.filter { it.tag == "chats" || it.tag == "messages" }.forEach { remove(it) }
        }.commitNow()
        fun add(container: Int, fragment: Fragment, tag: String) {
            retainedStates[tag]?.let { fragment.setInitialSavedState(it) }
            supportFragmentManager.beginTransaction().add(container, fragment, tag).commitNow()
        }
        if (landscape) {
            findViewById<View>(R.id.messages_container).visibility = if (navigation.selectedId == null) View.GONE else View.VISIBLE
            add(R.id.chats_container, ChatsFeature.create(), "chats")
            navigation.selectedId?.let { add(R.id.messages_container, MessagesFeature.create(it, navigation.selectedName), "messages") }
        } else {
            val id = navigation.selectedId
            if (id == null) add(R.id.single_container, ChatsFeature.create(), "chats")
            else add(R.id.single_container, MessagesFeature.create(id, navigation.selectedName), "messages")
        }
        supportFragmentManager.setFragmentResultListener("selectChat", this) { _, result ->
            navigation.selectedId = result.getLong("id")
            navigation.selectedName = result.getString("name").orEmpty()
            if (landscape) findViewById<View>(R.id.messages_container).visibility = View.VISIBLE
            val container = if (landscape) R.id.messages_container else R.id.single_container
            supportFragmentManager.beginTransaction().replace(container, MessagesFeature.create(navigation.selectedId!!, navigation.selectedName), "messages").commit()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (navigation.selectedId != null) {
                    navigation.selectedId = null
                    if (landscape) supportFragmentManager.findFragmentByTag("messages")?.let {
                        supportFragmentManager.beginTransaction().remove(it).commit()
                        findViewById<View>(R.id.messages_container).visibility = View.GONE
                    } else supportFragmentManager.beginTransaction().replace(R.id.single_container, ChatsFeature.create(), "chats").commit()
                } else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
    }
}
