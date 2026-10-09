package ru.messenger.corenetwork

import org.junit.*
import org.junit.Assert.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.test.runTest
import ru.messenger.common.MessengerException

class NetworkRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: NetworkRepository
    @Before fun setup() {
        server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder().addInterceptor(OAuthInterceptor(TokenSource { "unit-test-token" })).retryOnConnectionFailure(false).build()
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .client(client).addConverterFactory(GsonConverterFactory.create()).build().create(MessengerApi::class.java)
        repository = NetworkRepository(api)
    }
    @After fun close() { server.shutdown() }
    private fun response(json: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(json)
    @Test fun addsOAuthAndParsesChatPage() = runTest {
        server.enqueue(response("""{"data":[{"id":1,"name":"Test"}],"total":1,"limit":20,"offset":0}"""))
        val result = repository.chats(20,0)
        assertEquals("Test", result.chats.single().name)
        val request = server.takeRequest()
        assertEquals("unit-test-token", request.getHeader("oauth"))
        assertEquals("/mipt_network/chats?limit=20&offset=0", request.path)
    }
    @Test fun readsWholeMessageHistoryAndPreservesServerOrder() = runTest {
        server.enqueue(response("""{"id":1,"messages":[{"id":8,"text":"a"}],"total":2,"limit":100,"offset":0}"""))
        server.enqueue(response("""{"id":1,"messages":[{"id":3,"text":"b"}],"total":2,"limit":100,"offset":1}"""))
        assertEquals(listOf(8L,3L), repository.messages(1).map { it.id })
        server.takeRequest()
        assertTrue(server.takeRequest().path!!.endsWith("offset=1"))
    }
    @Test fun doesNotRetryFailedPost() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        try { repository.send(1,"hello"); fail("Expected error") }
        catch(e: MessengerException) { assertEquals("Ошибка сервера. Попробуйте позже", e.message) }
        assertEquals(1,server.requestCount)
        assertEquals("POST",server.takeRequest().method)
    }
    @Test fun sendsQueryAndMapsMessages() = runTest {
        server.enqueue(response("""{"id":1,"messages":[{"id":4,"text":"Привет"}]}"""))
        assertEquals("Привет", repository.send(1,"Привет").single().text)
        val req=server.takeRequest()
        assertEquals("Привет",req.requestUrl!!.queryParameter("text"))
        assertEquals(0L,req.bodySize)
    }
    @Test fun createsChatWithEncodedName() = runTest {
        server.enqueue(response("""{"chats":[{"id":1,"name":"A & Б"}]}"""))
        repository.createChat("  A & Б  ")
        val req=server.takeRequest()
        assertEquals("POST",req.method)
        assertEquals("A & Б",req.requestUrl!!.queryParameter("name"))
    }
    @Test fun rejectsMissingRequiredFields() = runTest {
        server.enqueue(response("""{"data":[{"id":1}],"total":1,"offset":0}"""))
        try { repository.chats(20,0); fail("Expected validation error") }
        catch(e: MessengerException) { assertEquals("Сервер вернул некорректные данные", e.message) }
    }
    @Test fun mapsMalformedJson() = runTest {
        server.enqueue(response("{broken"))
        try { repository.chats(20,0); fail("Expected JSON error") }
        catch(e: MessengerException) { assertEquals("Сервер вернул некорректные данные",e.message) }
    }
    @Test fun rejectsBlankMessageWithoutRequest() = runTest {
        try { repository.send(1,"   "); fail("Expected validation error") } catch(e: MessengerException) { }
        assertEquals(0,server.requestCount)
    }
    @Test fun doesNotRetryDisconnectedPost() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        try { repository.send(1,"hello"); fail("Expected connection error") }
        catch(e: MessengerException) { assertEquals("Не удалось подключиться. Проверьте интернет",e.message) }
        assertEquals(1,server.requestCount)
    }
    @Test fun mapsAuthAndRateLimitErrors() = runTest {
        for ((code,message) in listOf(400 to "Запрос отклонён сервером",401 to "Проверьте OAuth-токен",403 to "Проверьте OAuth-токен",404 to "Чат или ресурс не найден",429 to "Слишком много запросов. Попробуйте позже",500 to "Ошибка сервера. Попробуйте позже")) {
            server.enqueue(MockResponse().setResponseCode(code))
            try { repository.chats(20,0); fail("Expected HTTP error") }
            catch(e: MessengerException) { assertEquals(message,e.message) }
        }
    }
}
