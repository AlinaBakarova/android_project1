package ru.messenger.featuremessages.internal

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import ru.messenger.common.*

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun close() { Dispatchers.resetMain() }
    private class Fake : MessengerRepository {
        var sends = 0
        var fail = false
        val gate = CompletableDeferred<Unit>()
        override suspend fun chats(limit: Int,offset: Int) = ChatPage(emptyList(),0,offset)
        override suspend fun createChat(name: String) { }
        override suspend fun messages(chatId: Long) = listOf(Message(1,"old"))
        override suspend fun send(chatId: Long,text: String): List<Message> {
            sends++
            gate.await()
            if (fail) throw MessengerException("offline")
            return listOf(Message(1,"old"),Message(2,text))
        }
    }
    @Test fun blocksDoubleSendAndClearsDraftOnlyAfterSuccess() = runTest(dispatcher) {
        val repo=Fake()
        val handle=SavedStateHandle()
        val vm=MessagesViewModel(1,repo,handle)
        advanceUntilIdle()
        vm.edit("hello")
        vm.send(); vm.send()
        runCurrent()
        assertEquals(1,repo.sends)
        assertEquals("hello",vm.draft.value)
        assertTrue(vm.state.value.sending)
        repo.gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("",vm.draft.value)
        assertEquals("hello",vm.state.value.messages.last().text)
    }
    @Test fun failedSendRetainsDraftAndExistingMessages() = runTest(dispatcher) {
        val repo=Fake().apply { fail=true; gate.complete(Unit) }
        val handle=SavedStateHandle(mapOf("draft" to "hello"))
        val vm=MessagesViewModel(1,repo,handle)
        advanceUntilIdle()
        vm.send(); advanceUntilIdle()
        assertEquals("hello",vm.draft.value)
        assertEquals("hello",handle.get<String>("draft"))
        assertEquals(listOf(Message(1,"old")),vm.state.value.messages)
        assertEquals("offline",vm.state.value.error)
        assertFalse(vm.state.value.sending)
    }
    @Test fun emptyMessageNeverReachesRepository() = runTest(dispatcher) {
        val repo=Fake()
        val vm=MessagesViewModel(1,repo,SavedStateHandle())
        advanceUntilIdle()
        vm.edit("  "); vm.send(); runCurrent()
        assertEquals(0,repo.sends)
    }
}
