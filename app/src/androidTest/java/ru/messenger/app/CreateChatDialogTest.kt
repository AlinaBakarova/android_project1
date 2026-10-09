package ru.messenger.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.messenger.featurechats.R

@RunWith(AndroidJUnit4::class)
class CreateChatDialogTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)
    @Test fun opensValidatesAndCancelsWithoutSendingPost() {
        onView(withId(R.id.create)).perform(click())
        onView(withText("Новый чат")).check(matches(isDisplayed()))
        onView(withId(android.R.id.button1)).check(matches(not(isEnabled())))
        onView(withId(R.id.chat_name_input)).perform(replaceText("   "))
        onView(withId(android.R.id.button1)).check(matches(not(isEnabled())))
        onView(withId(R.id.chat_name_input)).perform(replaceText("UI check"), closeSoftKeyboard())
        onView(withId(android.R.id.button1)).check(matches(isEnabled()))
        onView(withId(android.R.id.button2)).perform(click())
        onView(withText("Новый чат")).check(doesNotExist())
    }
}
