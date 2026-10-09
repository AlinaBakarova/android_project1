package ru.messenger.app

import android.app.Application
import dagger.Component
import dagger.BindsInstance
import javax.inject.Inject
import javax.inject.Singleton
import ru.messenger.corenetwork.*
import ru.messenger.featurechats.api.*
import ru.messenger.featuremessages.api.*

@Singleton
@Component(modules = [NetworkModule::class, RepositoryModule::class])
interface AppComponent {
    fun inject(application: MessengerApplication)
    @Component.Factory interface Factory {
        fun create(@BindsInstance token: TokenSource): AppComponent
    }
}
class MessengerApplication : Application(), ChatsDependencies, MessagesDependencies {
    @Inject override lateinit var chatsFactory: ChatsViewModelFactory
    @Inject override lateinit var messagesFactory: MessagesViewModelFactory
    override fun onCreate() {
        super.onCreate()
        DaggerAppComponent.factory().create(TokenSource { BuildConfig.OAUTH_TOKEN }).inject(this)
    }
}
