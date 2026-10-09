package ru.messenger.corenetwork

import dagger.Module
import dagger.Provides
import dagger.Binds
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import ru.messenger.common.*
import kotlinx.coroutines.CancellationException
import java.io.IOException

fun interface TokenSource { fun token(): String }
class OAuthInterceptor @Inject constructor(private val source: TokenSource) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val token = source.token()
        if (token.isBlank()) throw IOException("OAuth token is not configured")
        return chain.proceed(chain.request().newBuilder().header("oauth", token).build())
    }
}

data class ChatDto(val id: Long?, val name: String?)
data class MessageDto(val id: Long?, val text: String?)
data class ChatsPageDto(val data: List<ChatDto>?, val total: Int?, val limit: Int?, val offset: Int?)
data class MessagesResponseDto(val id: Long?, val messages: List<MessageDto>?, val total: Int?, val limit: Int?, val offset: Int?)
data class CreatedChatsDto(val chats: List<ChatDto>?)
interface MessengerApi {
    @GET("mipt_network/chats") suspend fun chats(@Query("limit") limit: Int, @Query("offset") offset: Int): ChatsPageDto
    @GET("mipt_network/chat") suspend fun messages(@Query("id") id: Long, @Query("limit") limit: Int, @Query("offset") offset: Int): MessagesResponseDto
    @POST("mipt_network/create_chat") suspend fun create(@Query("name") name: String): CreatedChatsDto
    @POST("mipt_network/msg") suspend fun send(@Query("id") id: Long, @Query("text") text: String): MessagesResponseDto
}
@Module
class NetworkModule {
    @Provides @Singleton fun client(interceptor: OAuthInterceptor): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(interceptor).retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
    @Provides @Singleton fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://emil-international.ru/").client(client)
        .addConverterFactory(GsonConverterFactory.create()).build()
    @Provides @Singleton fun api(retrofit: Retrofit): MessengerApi = retrofit.create(MessengerApi::class.java)
}
@Module abstract class RepositoryModule {
    @Binds @Singleton abstract fun repository(implementation: NetworkRepository): MessengerRepository
}
@Singleton
class NetworkRepository @Inject constructor(private val api: MessengerApi) : MessengerRepository {
    override suspend fun chats(limit: Int, offset: Int): ChatPage = safely {
        val page = api.chats(limit, offset)
        val total = page.total ?: invalid()
        val actualOffset = page.offset ?: invalid()
        if (total < 0 || actualOffset != offset) invalid()
        val list = (page.data ?: invalid()).map { Chat(it.id ?: invalid(), it.name ?: invalid()) }
        ChatPage(list, total, actualOffset)
    }
    // Explicit paging guarantees the whole history even if the unpaged API changes.
    override suspend fun messages(chatId: Long): List<Message> = safely {
        val result = linkedMapOf<Long, Message>()
        var offset = 0
        while (true) {
            val page = api.messages(chatId, 100, offset)
            if (page.id != chatId || page.offset != offset) invalid()
            val list = page.messages ?: invalid()
            list.toModels().forEach { result[it.id] = it }
            offset = nextOffset(offset, list.size, page.total ?: invalid()) ?: break
        }
        result.values.toList()
    }
    override suspend fun createChat(name: String) = safely {
        require(name.isNotBlank())
        if (api.create(name.trim()).chats == null) invalid()
        Unit
    }
    override suspend fun send(chatId: Long, text: String): List<Message> = safely {
        require(validMessage(text))
        val response = api.send(chatId, text.trim())
        if (response.id != chatId) invalid()
        (response.messages ?: invalid()).toModels().distinctBy { it.id }
    }
    private fun List<MessageDto>.toModels() = map { Message(it.id ?: invalid(), it.text ?: invalid()) }
    private fun invalid(): Nothing = throw MessengerException("Сервер вернул некорректные данные")
    private suspend fun <T> safely(block: suspend () -> T): T = try { block() }
    catch (e: CancellationException) { throw e }
    catch (e: MessengerException) { throw e }
    catch (e: retrofit2.HttpException) {
        throw MessengerException(when(e.code()) {
            400 -> "Запрос отклонён сервером"
            401, 403 -> "Проверьте OAuth-токен"
            404 -> "Чат или ресурс не найден"
            429 -> "Слишком много запросов. Попробуйте позже"
            else -> "Ошибка сервера. Попробуйте позже"
        })
    }
    catch (e: com.google.gson.stream.MalformedJsonException) { throw MessengerException("Сервер вернул некорректные данные") }
    catch (e: com.google.gson.JsonParseException) { throw MessengerException("Сервер вернул некорректные данные") }
    catch (e: IOException) { throw MessengerException("Не удалось подключиться. Проверьте интернет") }
    catch (e: Exception) { throw MessengerException("Не удалось прочитать ответ сервера") }
}
