package top.apricityx.workshop

import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import top.apricityx.workshop.steam.protocol.SteamAccountSession
import top.apricityx.workshop.steam.protocol.SteamAuthPollResult
import top.apricityx.workshop.steam.protocol.SteamAuthSessionDetails
import top.apricityx.workshop.steam.protocol.SteamAuthenticationClient
import top.apricityx.workshop.steam.protocol.SteamCredentialAuthSession
import top.apricityx.workshop.steam.protocol.SteamDirectoryClient
import top.apricityx.workshop.steam.protocol.SteamGuardChallenge
import top.apricityx.workshop.steam.protocol.SteamGuardChallengeType
import top.apricityx.workshop.steam.protocol.SteamPublishedFileClient
import top.apricityx.workshop.steam.protocol.SteamPublishedFileQuery
import top.apricityx.workshop.steam.protocol.SteamPublishedFileQueryResult
import top.apricityx.workshop.steam.protocol.SteamWebAccessTokens
import top.apricityx.workshop.steam.protocol.applyDefaultHttpTimeouts
import top.apricityx.workshop.steam.protocol.applySteamHttpCompatibility
import java.util.UUID
import java.io.IOException
import java.security.SecureRandom
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
    private val steamClientIdentity = SteamClientIdentity(appContext)
    private val settingsRepository = DownloadSettingsRepository(appContext)
    private val experimentalWorkshopDirectAccessRuntime =
        createExperimentalWorkshopDirectAccessRuntime(appContext.filesDir)
    private val json = Json { ignoreUnknownKeys = true }
    private val authMutex = Mutex()
    private val tokenMutex = Mutex()
    private val prefs by lazy {
        createEncryptedPrefsOrFallback(
            context = appContext,
            encryptedPrefsName = PREFS_NAME,
            fallbackPrefsName = FALLBACK_PREFS_NAME,
            storageLabel = "Steam authentication state",
        )
    }
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .applyDefaultHttpTimeouts()
            .applySteamHttpCompatibility()
            .applyAppNetworkLogging("steam-auth")
            .hostnameVerifier(experimentalWorkshopDirectAccessRuntime.hostnameVerifier)
            .addExperimentalWorkshopDirectAccess(
                runtime = experimentalWorkshopDirectAccessRuntime,
                enabledProvider = settingsRepository::isExperimentalWorkshopDirectAccessEnabled,
                steamCookieJar = CookieJar.NO_COOKIES,
            )
            .build()
    }
    private val directoryClient by lazy { SteamDirectoryClient(httpClient) }
    private val authenticationClient by lazy {
        SteamAuthenticationClient(
            directoryClient = directoryClient,
            sessionFactory = { steamClientIdentity.createSession(httpClient) },
        )
    }
    private val publishedFileClient by lazy {
        SteamPublishedFileClient(
            directoryClient = directoryClient,
            sessionFactory = { steamClientIdentity.createSession(httpClient) },
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
            ?.toProtocolSession(machineName = steamClientIdentity.machineName)

    fun activeAccountRequiresReauthentication(): Boolean =
        loadSnapshot().activeAccount?.requiresReauthentication == true

    suspend fun beginSignIn(
        username: String,
        password: String,
        replaceAccountId: String? = null,
        debugLogger: ((String) -> Unit)? = null,
    ): SteamSignInStep = authMutex.withLock {
        debugLogger.log(
            "Repository: beginSignIn username=${username.maskForLog()} passwordLength=${password.length} replaceAccount=${replaceAccountId != null}.",
        )
        if (pendingAuthSession != null) {
            debugLogger.log("Repository: closing previous pending auth session before starting a new one.")
        }
        pendingAuthSession?.close()
        pendingAuthSession = null
        pendingReplaceAccountId = replaceAccountId

        val guardData = storedGuardDataFor(username, replaceAccountId)
        debugLogger.log(
            "Repository: stored guard data present=${!guardData.isNullOrBlank()} deviceName=${steamClientIdentity.machineName}.",
        )
        val authSession = authenticationClient.beginAuthSession(
            SteamAuthSessionDetails(
                username = username,
                password = password,
                guardData = guardData,
                isPersistentSession = true,
                deviceFriendlyName = steamClientIdentity.machineName,
            ),
            debugLogger = debugLogger,
        )
        pendingAuthSession = authSession

        val primaryChallenge = authSession.challenges.firstOrNull()
        debugLogger.log(
            "Repository: credential auth session steamId=${authSession.steamId} challenges=${authSession.challenges.summaryForLog()}.",
        )
        when {
            primaryChallenge == null || primaryChallenge.type == SteamGuardChallengeType.None -> {
                debugLogger.log("Repository: Steam did not require extra verification; finalizing auth immediately.")
                finalizePendingAuth(debugLogger)
            }

            primaryChallenge.type == SteamGuardChallengeType.DeviceConfirmation ||
                primaryChallenge.type == SteamGuardChallengeType.EmailConfirmation -> {
                debugLogger.log("Repository: awaiting confirmation type=${primaryChallenge.type.name}.")
                SteamSignInStep.AwaitingConfirmation(primaryChallenge)
            }

            primaryChallenge.type == SteamGuardChallengeType.EmailCode ||
                primaryChallenge.type == SteamGuardChallengeType.DeviceCode -> {
                debugLogger.log("Repository: guard code required type=${primaryChallenge.type.name}.")
                SteamSignInStep.RequiresGuardCode(primaryChallenge)
            }

            else -> throw IOException("Unsupported Steam Guard challenge: ${primaryChallenge.type}")
        }
    }

    suspend fun signInWithRefreshToken(
        refreshToken: String,
        accountNameHint: String? = null,
        replaceAccountId: String? = null,
        debugLogger: ((String) -> Unit)? = null,
    ): SteamSignInStep = authMutex.withLock {
        debugLogger.log(
            "Repository: signInWithRefreshToken refreshLength=${refreshToken.length} accountHint=${accountNameHint.maskForLog()} replaceAccount=${replaceAccountId != null}.",
        )
        if (pendingAuthSession != null) {
            debugLogger.log("Repository: closing previous pending auth session before refresh-token login.")
        }
        pendingAuthSession?.close()
        pendingAuthSession = null
        pendingReplaceAccountId = null

        val steamId = parseSteamJwtInfo(refreshToken).steamId
            ?: throw IOException("无法解析 Steam Refresh Token，请确认粘贴的是完整令牌。")
        debugLogger.log("Repository: parsed Steam refresh token steamId=$steamId.")
        val state = loadState()
        val existing = state.accounts.firstOrNull {
            it.accountId == replaceAccountId || it.steamId == steamId
        }
        val accountName = accountNameHint
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: existing?.accountName
            ?: "Steam $steamId"
        debugLogger.log(
            "Repository: resolved accountName=${accountName.maskForLog()} existingAccount=${existing != null}.",
        )
        val tokens = authenticationClient.generateAccessTokenForApp(
            account = SteamAccountSession(
                accountName = accountName,
                steamId = steamId,
                refreshToken = refreshToken,
                machineName = steamClientIdentity.machineName,
            ),
            allowRenewal = true,
            debugLogger = debugLogger,
        )
        debugLogger.log(
            "Repository: refresh-token login received accessLength=${tokens.accessToken.length} refreshUpdated=${!tokens.refreshToken.isNullOrBlank()}.",
        )
        val account = persistAccount(
            state = state,
            accountName = accountName,
            steamId = steamId,
            refreshToken = tokens.refreshToken ?: refreshToken,
            accessToken = tokens.accessToken,
            replaceAccountId = replaceAccountId,
        )
        val snapshot = loadSnapshot()
        debugLogger.log(
            "Repository: refresh-token login persisted accountId=${account.accountId} account=${account.accountName.maskForLog()}.",
        )
        return SteamSignInStep.Success(account = account, snapshot = snapshot)
    }

    suspend fun submitPendingGuardCode(
        code: String,
        debugLogger: ((String) -> Unit)? = null,
    ): SteamSignInStep = authMutex.withLock {
        val session = pendingAuthSession ?: throw IOException("No pending Steam sign-in session")
        val challenge = session.challenges.firstOrNull()
            ?: throw IOException("Steam did not provide a guard challenge")
        debugLogger.log(
            "Repository: submitting pending guard code type=${challenge.type.name} codeLength=${code.length}.",
        )
        session.submitGuardCode(challenge.type, code)
        finalizePendingAuth(debugLogger)
    }

    suspend fun waitForPendingConfirmation(
        debugLogger: ((String) -> Unit)? = null,
    ): SteamSignInStep = authMutex.withLock {
        debugLogger.log("Repository: waiting for pending Steam confirmation result.")
        finalizePendingAuth(debugLogger)
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
                    authenticationClient.revokeRefreshToken(
                        account.toProtocolSession(machineName = steamClientIdentity.machineName),
                        tokenId,
                    )
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
    ): String? =
        projectedCookiesForAccount(
            url = url,
            accountId = accountId,
        ).takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "; ") { cookie -> "${cookie.name}=${cookie.value}" }

    suspend fun projectedCookiesForAccount(
        url: HttpUrl,
        accountId: String?,
    ): List<Cookie> {
        if (!url.host.isSteamDomain()) {
            return emptyList()
        }
        val webLoginContext = webLoginContextForAccount(accountId) ?: return emptyList()
        return buildList {
            add(
                Cookie.Builder()
                    .name("steamLoginSecure")
                    .value(buildSteamLoginSecureCookieValue(webLoginContext.steamId, webLoginContext.accessToken))
                    .domain(url.host)
                    .path("/")
                    .build(),
            )
            add(
                Cookie.Builder()
                    .name("sessionid")
                    .value(webLoginContext.sessionId)
                    .domain(url.host)
                    .path("/")
                    .build(),
            )
        }
    }

    suspend fun webLoginContextForAccount(accountId: String?): SteamWebLoginContext? {
        val resolvedAccountId = accountId ?: return null
        val account = ensureProjectedWebSessionState(resolvedAccountId) ?: return null
        if (account.requiresReauthentication) {
            return null
        }
        val refreshed = ensureFreshAccessToken(account.accountId) ?: return null
        return SteamWebLoginContext(
            steamId = account.steamId,
            accessToken = refreshed.accessToken,
            sessionId = account.webSessionId ?: generateSteamWebSessionId(),
        )
    }

    fun blockingCookieHeaderFor(
        url: HttpUrl,
        accountId: String?,
    ): String? = runBlocking {
        cookieHeaderForAccount(url, accountId)
    }

    fun blockingProjectedCookiesFor(
        url: HttpUrl,
        accountId: String?,
    ): List<Cookie> = runBlocking {
        projectedCookiesForAccount(url, accountId)
    }

    suspend fun queryPublishedFiles(
        accountId: String?,
        query: SteamPublishedFileQuery,
    ): SteamPublishedFileQueryResult? {
        val accountSession = accountSessionFor(accountId) ?: return null
        return publishedFileClient.queryFiles(
            account = accountSession,
            query = query,
        )
    }

    private suspend fun finalizePendingAuth(
        debugLogger: ((String) -> Unit)? = null,
    ): SteamSignInStep {
        val session = pendingAuthSession ?: throw IOException("No pending Steam sign-in session")
        val replaceAccountId = pendingReplaceAccountId
        return try {
            debugLogger.log("Repository: finalizing pending auth session.")
            val result = session.awaitResult()
            debugLogger.log(
                "Repository: pending auth completed steamId=${result.steamId} account=${result.accountName.maskForLog()} guardDataUpdated=${!result.newGuardData.isNullOrBlank()}.",
            )
            val account = persistAccount(result = result, replaceAccountId = replaceAccountId)
            val snapshot = loadSnapshot()
            debugLogger.log(
                "Repository: pending auth persisted accountId=${account.accountId} account=${account.accountName.maskForLog()}.",
            )
            SteamSignInStep.Success(account = account, snapshot = snapshot)
        } finally {
            debugLogger.log("Repository: closing pending auth session.")
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
                account = account.toProtocolSession(machineName = steamClientIdentity.machineName),
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
    ): SteamAccountSummary =
        persistAccount(
            state = loadState(),
            accountName = result.accountName,
            steamId = result.steamId,
            refreshToken = result.refreshToken,
            accessToken = result.accessToken,
            replaceAccountId = replaceAccountId,
            guardDataOverride = result.newGuardData,
        )

    private fun persistAccount(
        state: StoredSteamState,
        accountName: String,
        steamId: Long,
        refreshToken: String,
        accessToken: String,
        replaceAccountId: String?,
        guardDataOverride: String? = null,
    ): SteamAccountSummary {
        val existing = state.accounts.firstOrNull {
            it.accountId == replaceAccountId || it.steamId == steamId
        }
        val accessTokenInfo = parseSteamJwtInfo(accessToken)
        val accountId = existing?.accountId ?: UUID.randomUUID().toString()
        val nextAccount = StoredSteamAccount(
            accountId = accountId,
            accountName = accountName,
            steamId = steamId,
            refreshToken = refreshToken,
            guardData = guardDataOverride ?: existing?.guardData,
            webAccessToken = accessToken,
            webAccessTokenExpEpochSeconds = accessTokenInfo.expiresAtEpochSeconds,
            webSessionId = existing?.webSessionId ?: generateSteamWebSessionId(),
            requiresReauthentication = false,
        )
        saveState(
            state.copy(
                accounts = state.accounts.filterNot { it.accountId == accountId || it.steamId == steamId } + nextAccount,
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

    private fun ensureProjectedWebSessionState(accountId: String): StoredSteamAccount? {
        val state = loadState()
        val account = state.accounts.firstOrNull { it.accountId == accountId } ?: return null
        if (!account.webSessionId.isNullOrBlank()) {
            return account
        }
        val nextSessionId = generateSteamWebSessionId()
        val updatedAccount = account.copy(webSessionId = nextSessionId)
        saveState(
            state.copy(
                accounts = state.accounts.map { existing ->
                    if (existing.accountId == accountId) {
                        existing.copy(webSessionId = existing.webSessionId ?: nextSessionId)
                    } else {
                        existing
                    }
                },
            ),
        )
        return updatedAccount
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
        private const val FALLBACK_PREFS_NAME = "steam_accounts_secure_fallback"
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
    val webSessionId: String? = null,
    val requiresReauthentication: Boolean = false,
)

private fun StoredSteamAccount.toProtocolSession(machineName: String): SteamAccountSession =
    SteamAccountSession(
        accountName = accountName,
        steamId = steamId,
        refreshToken = refreshToken,
        machineName = machineName,
    )

internal fun String.isSteamDomain(): Boolean {
    val host = lowercase()
    return host == "steamcommunity.com" ||
        host.endsWith(".steamcommunity.com") ||
        host == "steampowered.com" ||
        host.endsWith(".steampowered.com")
}

private val steamWebSessionRandom = SecureRandom()

private fun generateSteamWebSessionId(): String {
    val bytes = ByteArray(12)
    steamWebSessionRandom.nextBytes(bytes)
    val result = StringBuilder(bytes.size * 2)
    bytes.forEach { byte ->
        val value = byte.toInt() and 0xFF
        result.append(HEX_CHARS[value ushr 4])
        result.append(HEX_CHARS[value and 0x0F])
    }
    return result.toString()
}

private val HEX_CHARS = charArrayOf(
    '0', '1', '2', '3', '4', '5', '6', '7',
    '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
)

private fun List<SteamGuardChallenge>.summaryForLog(): String =
    if (isEmpty()) {
        "none"
    } else {
        joinToString(separator = ",") { challenge ->
            buildString {
                append(challenge.type.name)
                challenge.message
                    ?.takeIf(String::isNotBlank)
                    ?.let {
                        append("(message)")
                    }
            }
        }
    }

private fun String?.maskForLog(): String =
    this
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let { value ->
            when {
                value.length <= 2 -> "*".repeat(value.length)
                else -> "${value.first()}***${value.last()}"
            }
        }
        ?: "-"

private fun ((String) -> Unit)?.log(line: String) {
    this?.invoke(line)
}
