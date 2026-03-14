package top.apricityx.workshop

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import top.apricityx.workshop.steam.protocol.OkHttpSteamCmSession
import top.apricityx.workshop.steam.protocol.SteamAccountSession
import top.apricityx.workshop.steam.protocol.SteamAuthPollResult
import top.apricityx.workshop.steam.protocol.SteamAuthSessionDetails
import top.apricityx.workshop.steam.protocol.SteamAuthenticationClient
import top.apricityx.workshop.steam.protocol.SteamCredentialAuthSession
import top.apricityx.workshop.steam.protocol.SteamDirectoryClient
import top.apricityx.workshop.steam.protocol.SteamGuardChallenge
import top.apricityx.workshop.steam.protocol.SteamGuardChallengeType
import top.apricityx.workshop.steam.protocol.SteamWebAccessTokens
import java.util.UUID
import java.io.IOException
import okhttp3.OkHttpClient

data class SteamAccountSummary(
    val accountId: String,
    val accountName: String,
    val steamId: Long,
    val isActive: Boolean,
    val requiresReauthentication: Boolean,
)

data class SteamAccountsSnapshot(
    val accounts: List<SteamAccountSummary> = emptyList(),
    val activeAccountId: String? = null,
) {
    val activeAccount: SteamAccountSummary?
        get() = accounts.firstOrNull { it.isActive }
}

data class SteamDownloadBinding(
    val accountId: String? = null,
    val accountName: String = "匿名",
)

sealed interface SteamSignInStep {
    data class RequiresGuardCode(
        val challenge: SteamGuardChallenge,
    ) : SteamSignInStep

    data class AwaitingConfirmation(
        val challenge: SteamGuardChallenge,
    ) : SteamSignInStep

    data class Success(
        val account: SteamAccountSummary,
        val snapshot: SteamAccountsSnapshot,
    ) : SteamSignInStep
}

class SteamAuthRepository(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val authMutex = Mutex()
    private val tokenMutex = Mutex()
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
    private val httpClient by lazy { OkHttpClient.Builder().build() }
    private val directoryClient by lazy { SteamDirectoryClient(httpClient) }
    private val authenticationClient by lazy {
        SteamAuthenticationClient(
            directoryClient = directoryClient,
            sessionFactory = { OkHttpSteamCmSession(httpClient) },
        )
    }

    @Volatile
    private var pendingAuthSession: SteamCredentialAuthSession? = null

    @Volatile
    private var pendingReplaceAccountId: String? = null

    init {
        clearLegacyCookieOnlyState()
    }

    fun activeAccountId(): String? = loadState().activeAccountId

    fun loadSnapshot(): SteamAccountsSnapshot {
        val state = loadState()
        return SteamAccountsSnapshot(
            accounts = state.accounts
                .sortedBy { it.accountName.lowercase() }
                .map { account ->
                    SteamAccountSummary(
                        accountId = account.accountId,
                        accountName = account.accountName,
                        steamId = account.steamId,
                        isActive = account.accountId == state.activeAccountId,
                        requiresReauthentication = account.requiresReauthentication,
                    )
                },
            activeAccountId = state.activeAccountId,
        )
    }

    fun currentDownloadBinding(): SteamDownloadBinding =
        loadSnapshot().activeAccount?.let {
            SteamDownloadBinding(
                accountId = it.accountId,
                accountName = it.accountName,
            )
        } ?: SteamDownloadBinding()

    fun accountSessionFor(accountId: String?): SteamAccountSession? =
        accountId
            ?.let { id -> loadState().accounts.firstOrNull { it.accountId == id } }
            ?.takeUnless(StoredSteamAccount::requiresReauthentication)
            ?.toProtocolSession()

    fun activeAccountRequiresReauthentication(): Boolean =
        loadSnapshot().activeAccount?.requiresReauthentication == true

    suspend fun beginSignIn(
        username: String,
        password: String,
        replaceAccountId: String? = null,
    ): SteamSignInStep = authMutex.withLock {
        pendingAuthSession?.close()
        pendingAuthSession = null
        pendingReplaceAccountId = replaceAccountId

        val authSession = authenticationClient.beginAuthSession(
            SteamAuthSessionDetails(
                username = username,
                password = password,
                guardData = storedGuardDataFor(username, replaceAccountId),
                isPersistentSession = true,
            ),
        )
        pendingAuthSession = authSession

        val primaryChallenge = authSession.challenges.firstOrNull()
        when {
            primaryChallenge == null || primaryChallenge.type == SteamGuardChallengeType.None -> finalizePendingAuth()
            primaryChallenge.type == SteamGuardChallengeType.DeviceConfirmation ||
                primaryChallenge.type == SteamGuardChallengeType.EmailConfirmation ->
                SteamSignInStep.AwaitingConfirmation(primaryChallenge)

            primaryChallenge.type == SteamGuardChallengeType.EmailCode ||
                primaryChallenge.type == SteamGuardChallengeType.DeviceCode ->
                SteamSignInStep.RequiresGuardCode(primaryChallenge)

            else -> throw IOException("Unsupported Steam Guard challenge: ${primaryChallenge.type}")
        }
    }

    suspend fun submitPendingGuardCode(code: String): SteamSignInStep = authMutex.withLock {
        val session = pendingAuthSession ?: throw IOException("No pending Steam sign-in session")
        val challenge = session.challenges.firstOrNull()
            ?: throw IOException("Steam did not provide a guard challenge")
        session.submitGuardCode(challenge.type, code)
        finalizePendingAuth()
    }

    suspend fun waitForPendingConfirmation(): SteamSignInStep = authMutex.withLock {
        finalizePendingAuth()
    }

    fun cancelPendingSignIn() {
        pendingReplaceAccountId = null
        pendingAuthSession?.close()
        pendingAuthSession = null
    }

    fun setActiveAccount(accountId: String?) {
        val state = loadState()
        val nextActiveId = accountId?.takeIf { id -> state.accounts.any { it.accountId == id } }
        saveState(state.copy(activeAccountId = nextActiveId))
    }

    fun removeAccount(accountId: String) {
        val state = loadState()
        val account = state.accounts.firstOrNull { it.accountId == accountId } ?: return
        runCatching {
            parseSteamJwtInfo(account.refreshToken).tokenId?.let { tokenId ->
                runBlocking {
                    authenticationClient.revokeRefreshToken(account.toProtocolSession(), tokenId)
                }
            }
        }
        val nextAccounts = state.accounts.filterNot { it.accountId == accountId }
        saveState(
            state.copy(
                accounts = nextAccounts,
                activeAccountId = state.activeAccountId.takeUnless { it == accountId },
            ),
        )
    }

    fun markAccountRequiresReauthentication(accountId: String) {
        updateAccount(accountId) { it.copy(requiresReauthentication = true) }
    }

    suspend fun cookieHeaderForAccount(
        url: HttpUrl,
        accountId: String?,
    ): String? {
        if (!url.host.isSteamDomain()) {
            return null
        }
        val account = loadState().accounts.firstOrNull { it.accountId == accountId } ?: return null
        if (account.requiresReauthentication) {
            return null
        }
        val refreshed = ensureFreshAccessToken(account.accountId) ?: return null
        return buildSteamLoginSecureCookie(account.steamId, refreshed.accessToken)
    }

    fun blockingCookieHeaderFor(
        url: HttpUrl,
        accountId: String?,
    ): String? = runBlocking {
        cookieHeaderForAccount(url, accountId)
    }

    private suspend fun finalizePendingAuth(): SteamSignInStep {
        val session = pendingAuthSession ?: throw IOException("No pending Steam sign-in session")
        val replaceAccountId = pendingReplaceAccountId
        return try {
            val result = session.awaitResult()
            val account = persistAccount(result = result, replaceAccountId = replaceAccountId)
            val snapshot = loadSnapshot()
            SteamSignInStep.Success(account = account, snapshot = snapshot)
        } finally {
            session.close()
            pendingAuthSession = null
            pendingReplaceAccountId = null
        }
    }

    private suspend fun ensureFreshAccessToken(accountId: String): SteamWebAccessTokens? = tokenMutex.withLock {
        val state = loadState()
        val account = state.accounts.firstOrNull { it.accountId == accountId } ?: return null
        if (account.requiresReauthentication) {
            return null
        }
        val nowEpochSeconds = System.currentTimeMillis() / 1000L
        if (!account.webAccessToken.isNullOrBlank() &&
            account.webAccessTokenExpEpochSeconds != null &&
            account.webAccessTokenExpEpochSeconds - TOKEN_REFRESH_WINDOW_SECONDS > nowEpochSeconds
        ) {
            return SteamWebAccessTokens(accessToken = account.webAccessToken)
        }

        return runCatching {
            authenticationClient.generateAccessTokenForApp(
                account = account.toProtocolSession(),
                allowRenewal = true,
            )
        }.onSuccess { tokens ->
            val nextAccessToken = tokens.accessToken
            val nextRefreshToken = tokens.refreshToken ?: account.refreshToken
            val tokenInfo = parseSteamJwtInfo(nextAccessToken)
            updateAccount(account.accountId) {
                it.copy(
                    refreshToken = nextRefreshToken,
                    webAccessToken = nextAccessToken,
                    webAccessTokenExpEpochSeconds = tokenInfo.expiresAtEpochSeconds,
                    requiresReauthentication = false,
                )
            }
        }.onFailure {
            updateAccount(account.accountId) {
                it.copy(
                    requiresReauthentication = true,
                    webAccessToken = null,
                    webAccessTokenExpEpochSeconds = null,
                )
            }
        }.getOrNull()
    }

    private fun persistAccount(
        result: SteamAuthPollResult,
        replaceAccountId: String?,
    ): SteamAccountSummary {
        val state = loadState()
        val existing = state.accounts.firstOrNull {
            it.accountId == replaceAccountId || it.steamId == result.steamId
        }
        val accessTokenInfo = parseSteamJwtInfo(result.accessToken)
        val accountId = existing?.accountId ?: UUID.randomUUID().toString()
        val nextAccount = StoredSteamAccount(
            accountId = accountId,
            accountName = result.accountName,
            steamId = result.steamId,
            refreshToken = result.refreshToken,
            guardData = result.newGuardData ?: existing?.guardData,
            webAccessToken = result.accessToken,
            webAccessTokenExpEpochSeconds = accessTokenInfo.expiresAtEpochSeconds,
            requiresReauthentication = false,
        )
        saveState(
            state.copy(
                accounts = state.accounts.filterNot { it.accountId == accountId || it.steamId == result.steamId } + nextAccount,
                activeAccountId = accountId,
            ),
        )
        return loadSnapshot().accounts.first { it.accountId == accountId }
    }

    private fun updateAccount(
        accountId: String,
        transform: (StoredSteamAccount) -> StoredSteamAccount,
    ) {
        val state = loadState()
        val nextAccounts = state.accounts.map { account ->
            if (account.accountId == accountId) {
                transform(account)
            } else {
                account
            }
        }
        saveState(state.copy(accounts = nextAccounts))
    }

    private fun storedGuardDataFor(
        username: String,
        replaceAccountId: String?,
    ): String? {
        val state = loadState()
        val account = state.accounts.firstOrNull {
            it.accountId == replaceAccountId || it.accountName.equals(username, ignoreCase = true)
        }
        return account?.guardData
    }

    private fun clearLegacyCookieOnlyState() {
        appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .takeIf { prefs ->
                prefs.contains(KEY_COMMUNITY_COOKIE_HEADER) ||
                    prefs.contains(KEY_STORE_COOKIE_HEADER) ||
                    prefs.contains(KEY_API_COOKIE_HEADER)
            }
            ?.edit()
            ?.clear()
            ?.apply()
    }

    private fun loadState(): StoredSteamState =
        prefs.getString(KEY_ACCOUNTS_JSON, null)
            ?.let { raw -> runCatching { json.decodeFromString<StoredSteamState>(raw) }.getOrNull() }
            ?: StoredSteamState()

    private fun saveState(state: StoredSteamState) {
        prefs.edit()
            .putString(KEY_ACCOUNTS_JSON, json.encodeToString(state))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "steam_accounts_secure"
        private const val KEY_ACCOUNTS_JSON = "accounts_json"
        private const val TOKEN_REFRESH_WINDOW_SECONDS = 15 * 60L

        private const val LEGACY_PREFS_NAME = "steam_auth"
        private const val KEY_COMMUNITY_COOKIE_HEADER = "community_cookie_header"
        private const val KEY_STORE_COOKIE_HEADER = "store_cookie_header"
        private const val KEY_API_COOKIE_HEADER = "api_cookie_header"
    }
}

class SteamCookieInterceptor(
    private val authRepository: SteamAuthRepository,
    private val accountIdProvider: (() -> String?)? = null,
    private val fallbackToActiveAccount: Boolean = true,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val accountId = when {
            accountIdProvider != null -> accountIdProvider.invoke()
            fallbackToActiveAccount -> authRepository.activeAccountId()
            else -> null
        }
        val cookieHeader = authRepository.blockingCookieHeaderFor(
            url = originalRequest.url,
            accountId = accountId,
        )
        val request = if (cookieHeader.isNullOrBlank()) {
            originalRequest
        } else {
            originalRequest.newBuilder()
                .header("Cookie", cookieHeader)
                .build()
        }
        return chain.proceed(request)
    }
}

class SteamLanguageInterceptor(
    private val languagePreferenceProvider: () -> SteamLanguagePreference,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (!originalRequest.url.host.isSteamDomain()) {
            return chain.proceed(originalRequest)
        }
        val languagePreference = languagePreferenceProvider()
        return chain.proceed(
            originalRequest.newBuilder()
                .header("Accept-Language", languagePreference.acceptLanguageValue)
                .build(),
        )
    }
}

@Serializable
private data class StoredSteamState(
    val accounts: List<StoredSteamAccount> = emptyList(),
    val activeAccountId: String? = null,
)

@Serializable
private data class StoredSteamAccount(
    val accountId: String,
    val accountName: String,
    val steamId: Long,
    val refreshToken: String,
    val guardData: String? = null,
    val webAccessToken: String? = null,
    val webAccessTokenExpEpochSeconds: Long? = null,
    val requiresReauthentication: Boolean = false,
)

private fun StoredSteamAccount.toProtocolSession(): SteamAccountSession =
    SteamAccountSession(
        accountName = accountName,
        steamId = steamId,
        refreshToken = refreshToken,
    )

private fun String.isSteamDomain(): Boolean =
    endsWith("steamcommunity.com") || this == "store.steampowered.com" || this == "api.steampowered.com"
