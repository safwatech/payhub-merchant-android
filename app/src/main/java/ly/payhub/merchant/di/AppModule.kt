package ly.payhub.merchant.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ly.payhub.merchant.data.MerchantRepository
import ly.payhub.merchant.data.TokenStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppCoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @AppCoroutineScope
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): TokenStore = TokenStore(context)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideMerchantRepository(
        tokenStore: TokenStore,
        httpClient: OkHttpClient,
        @AppCoroutineScope appScope: CoroutineScope,
    ): MerchantRepository = MerchantRepository(
        tokenStore = tokenStore,
        sharedHttpClient = httpClient,
        appScope = appScope,
    )
}
