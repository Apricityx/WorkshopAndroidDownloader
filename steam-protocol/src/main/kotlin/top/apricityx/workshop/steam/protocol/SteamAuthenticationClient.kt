package top.apricityx.workshop.steam.protocol

import top.apricityx.workshop.steam.proto.CAuthentication_AccessToken_GenerateForApp_Request
import top.apricityx.workshop.steam.proto.CAuthentication_AccessToken_GenerateForApp_Response
import top.apricityx.workshop.steam.proto.CAuthentication_AllowedConfirmation
import top.apricityx.workshop.steam.proto.CAuthentication_BeginAuthSessionViaCredentials_Request
import top.apricityx.workshop.steam.proto.CAuthentication_BeginAuthSessionViaCredentials_Response
import top.apricityx.workshop.steam.proto.CAuthentication_DeviceDetails
import top.apricityx.workshop.steam.proto.CAuthentication_GetPasswordRSAPublicKey_Request
import top.apricityx.workshop.steam.proto.CAuthentication_GetPasswordRSAPublicKey_Response
import top.apricityx.workshop.steam.proto.CAuthentication_PollAuthSessionStatus_Request
import top.apricityx.workshop.steam.proto.CAuthentication_PollAuthSessionStatus_Response
import top.apricityx.workshop.steam.proto.CAuthentication_RefreshToken_Revoke_Request
import top.apricityx.workshop.steam.proto.CAuthentication_RefreshToken_Revoke_Response
import top.apricityx.workshop.steam.proto.CAuthentication_UpdateAuthSessionWithSteamGuardCode_Request
import top.apricityx.workshop.steam.proto.CAuthentication_UpdateAuthSessionWithSteamGuardCode_Response
import top.apricityx.workshop.steam.proto.EAuthSessionGuardType
import top.apricityx.workshop.steam.proto.EAuthTokenPlatformType
import top.apricityx.workshop.steam.proto.ESessionPersistence
import top.apricityx.workshop.steam.proto.ETokenRenewalType
import kotlinx.coroutines.delay
import java.io.Closeable
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher

class SteamAuthenticationClient(
    private val directoryClient: SteamDirectoryClient,
    private val sessionFactory: () -> SteamCmSession,
) {
    suspend fun beginAuthSession(details: SteamAuthSessionDetails): SteamCredentialAuthSession {
        val cmServers = directoryClient.loadServers()
        val session = sessionFactory()
        try {
            session.connect(cmServers)
            val publicKey = session.callServiceMethod(
                methodName = "Authentication.GetPasswordRSAPublicKey#1",
                request = CAuthentication_GetPasswordRSAPublicKey_Request.newBuilder()
                    .setAccountName(details.username)
                    .build(),
                parser = CAuthentication_GetPasswordRSAPublicKey_Response.parser(),
            )

            val beginResponse = session.callServiceMethod(
                methodName = "Authentication.BeginAuthSessionViaCredentials#1",
                request = CAuthentication_BeginAuthSessionViaCredentials_Request.newBuilder()
                    .setDeviceFriendlyName(details.deviceFriendlyName)
                    .setAccountName(details.username)
                    .setEncryptedPassword(encryptPassword(details.password, publicKey))
                    .setEncryptionTimestamp(publicKey.timestamp)
                    .setRememberLogin(details.isPersistentSession)
                    .setPlatformType(EAuthTokenPlatformType.k_EAuthTokenPlatformType_SteamClient)
                    .setPersistence(
                        if (details.isPersistentSession) {
                            ESessionPersistence.k_ESessionPersistence_Persistent
                        } else {
                            ESessionPersistence.k_ESessionPersistence_Ephemeral
                        },
                    )
                    .setWebsiteId(details.websiteId)
                    .setGuardData(details.guardData.orEmpty())
                    .setQosLevel(2)
                    .setDeviceDetails(
                        CAuthentication_DeviceDetails.newBuilder()
                            .setDeviceFriendlyName(details.deviceFriendlyName)
                            .setPlatformType(EAuthTokenPlatformType.k_EAuthTokenPlatformType_SteamClient)
                            .setOsType(details.clientOsType)
                            .setMachineId(machineId())
                            .build(),
                    )
                    .build(),
                parser = CAuthentication_BeginAuthSessionViaCredentials_Response.parser(),
            )

            return SteamCredentialAuthSession(
                session = session,
                steamId = beginResponse.steamid,
                clientId = beginResponse.clientId,
                requestId = beginResponse.requestId.toByteArray(),
                pollingIntervalMillis = (beginResponse.interval * 1_000f).toLong().coerceAtLeast(1_000L),
                challenges = beginResponse.allowedConfirmationsList.map(::mapChallenge).sortedBy(SteamGuardChallenge::sortOrder),
            )
        } catch (error: Throwable) {
            session.close()
            throw error.asAuthenticationException("Failed to begin Steam authentication")
        }
    }

    suspend fun generateAccessTokenForApp(
        account: SteamAccountSession,
        allowRenewal: Boolean,
    ): SteamWebAccessTokens {
        val cmServers = directoryClient.loadServers()
        return sessionFactory().use { session ->
            try {
                session.connectWithRefreshToken(cmServers, account)
                val response = session.callServiceMethod(
                    methodName = "Authentication.GenerateAccessTokenForApp#1",
                    request = CAuthentication_AccessToken_GenerateForApp_Request.newBuilder()
                        .setRefreshToken(account.refreshToken)
                        .setSteamid(account.steamId)
                        .setRenewalType(
                            if (allowRenewal) {
                                ETokenRenewalType.k_ETokenRenewalType_Allow
                            } else {
                                ETokenRenewalType.k_ETokenRenewalType_None
                            },
                        )
                        .build(),
                    parser = CAuthentication_AccessToken_GenerateForApp_Response.parser(),
                )
                SteamWebAccessTokens(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken.takeIf(String::isNotBlank),
                )
            } catch (error: Throwable) {
                throw error.asAuthenticationException("Failed to generate Steam web access token")
            }
        }
    }

    suspend fun revokeRefreshToken(
        account: SteamAccountSession,
        tokenId: ULong,
    ) {
        val cmServers = directoryClient.loadServers()
        sessionFactory().use { session ->
            try {
                session.connectWithRefreshToken(cmServers, account)
                session.callServiceMethod(
                    methodName = "Authentication.RevokeRefreshToken#1",
                    request = CAuthentication_RefreshToken_Revoke_Request.newBuilder()
                        .setTokenId(tokenId.toLong())
                        .setSteamid(account.steamId)
                        .build(),
                    parser = CAuthentication_RefreshToken_Revoke_Response.parser(),
                )
            } catch (error: Throwable) {
                throw error.asAuthenticationException("Failed to revoke Steam refresh token")
            }
        }
    }
}

class SteamCredentialAuthSession internal constructor(
    private val session: SteamCmSession,
    val steamId: Long,
    private val clientId: Long,
    private val requestId: ByteArray,
    val pollingIntervalMillis: Long,
    val challenges: List<SteamGuardChallenge>,
) : Closeable {
    suspend fun submitGuardCode(
        type: SteamGuardChallengeType,
        code: String,
    ) {
        try {
            session.callServiceMethod(
                methodName = "Authentication.UpdateAuthSessionWithSteamGuardCode#1",
                request = CAuthentication_UpdateAuthSessionWithSteamGuardCode_Request.newBuilder()
                    .setClientId(clientId)
                    .setSteamid(steamId)
                    .setCode(code)
                    .setCodeType(type.toProto())
                    .build(),
                parser = CAuthentication_UpdateAuthSessionWithSteamGuardCode_Response.parser(),
            )
        } catch (error: Throwable) {
            throw error.asAuthenticationException("Failed to submit Steam Guard code")
        }
    }

    suspend fun pollStatus(): SteamAuthPollResult? {
        try {
            val response = session.callServiceMethod(
                methodName = "Authentication.PollAuthSessionStatus#1",
                request = CAuthentication_PollAuthSessionStatus_Request.newBuilder()
                    .setClientId(clientId)
                    .setRequestId(com.google.protobuf.ByteString.copyFrom(requestId))
                    .build(),
                parser = CAuthentication_PollAuthSessionStatus_Response.parser(),
            )
            if (response.refreshToken.isBlank()) {
                return null
            }
            return SteamAuthPollResult(
                steamId = steamId,
                accountName = response.accountName,
                refreshToken = response.refreshToken,
                accessToken = response.accessToken,
                newGuardData = response.newGuardData.takeIf(String::isNotBlank),
            )
        } catch (error: Throwable) {
            throw error.asAuthenticationException("Failed to poll Steam authentication status")
        }
    }

    suspend fun awaitResult(): SteamAuthPollResult {
        while (true) {
            pollStatus()?.let { return it }
            delay(pollingIntervalMillis)
        }
    }

    override fun close() {
        session.close()
    }
}

private fun SteamGuardChallenge.sortOrder(): Int =
    when (type) {
        SteamGuardChallengeType.None -> 0
        SteamGuardChallengeType.DeviceConfirmation -> 1
        SteamGuardChallengeType.DeviceCode -> 2
        SteamGuardChallengeType.EmailCode -> 3
        SteamGuardChallengeType.EmailConfirmation -> 4
        SteamGuardChallengeType.MachineToken -> 5
        SteamGuardChallengeType.LegacyMachineAuth -> 6
        SteamGuardChallengeType.Unknown -> 7
    }

private fun mapChallenge(source: CAuthentication_AllowedConfirmation): SteamGuardChallenge =
    SteamGuardChallenge(
        type = when (source.confirmationType) {
            EAuthSessionGuardType.k_EAuthSessionGuardType_None -> SteamGuardChallengeType.None
            EAuthSessionGuardType.k_EAuthSessionGuardType_EmailCode -> SteamGuardChallengeType.EmailCode
            EAuthSessionGuardType.k_EAuthSessionGuardType_DeviceCode -> SteamGuardChallengeType.DeviceCode
            EAuthSessionGuardType.k_EAuthSessionGuardType_DeviceConfirmation -> SteamGuardChallengeType.DeviceConfirmation
            EAuthSessionGuardType.k_EAuthSessionGuardType_EmailConfirmation -> SteamGuardChallengeType.EmailConfirmation
            EAuthSessionGuardType.k_EAuthSessionGuardType_MachineToken -> SteamGuardChallengeType.MachineToken
            EAuthSessionGuardType.k_EAuthSessionGuardType_LegacyMachineAuth -> SteamGuardChallengeType.LegacyMachineAuth
            else -> SteamGuardChallengeType.Unknown
        },
        message = source.associatedMessage.takeIf(String::isNotBlank),
    )

private fun SteamGuardChallengeType.toProto(): EAuthSessionGuardType =
    when (this) {
        SteamGuardChallengeType.None -> EAuthSessionGuardType.k_EAuthSessionGuardType_None
        SteamGuardChallengeType.EmailCode -> EAuthSessionGuardType.k_EAuthSessionGuardType_EmailCode
        SteamGuardChallengeType.DeviceCode -> EAuthSessionGuardType.k_EAuthSessionGuardType_DeviceCode
        SteamGuardChallengeType.DeviceConfirmation -> EAuthSessionGuardType.k_EAuthSessionGuardType_DeviceConfirmation
        SteamGuardChallengeType.EmailConfirmation -> EAuthSessionGuardType.k_EAuthSessionGuardType_EmailConfirmation
        SteamGuardChallengeType.MachineToken -> EAuthSessionGuardType.k_EAuthSessionGuardType_MachineToken
        SteamGuardChallengeType.LegacyMachineAuth -> EAuthSessionGuardType.k_EAuthSessionGuardType_LegacyMachineAuth
        SteamGuardChallengeType.Unknown -> EAuthSessionGuardType.k_EAuthSessionGuardType_Unknown
    }

private fun encryptPassword(
    password: String,
    publicKey: CAuthentication_GetPasswordRSAPublicKey_Response,
): String {
    val modulus = BigInteger(1, decodeHex(publicKey.publickeyMod))
    val exponent = BigInteger(1, decodeHex(publicKey.publickeyExp))
    val keySpec = RSAPublicKeySpec(modulus, exponent)
    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.ENCRYPT_MODE, KeyFactory.getInstance("RSA").generatePublic(keySpec))
    return Base64.getEncoder().encodeToString(cipher.doFinal(password.toByteArray(Charsets.UTF_8)))
}

private fun decodeHex(value: String): ByteArray {
    require(value.length % 2 == 0) { "Invalid hex input length" }
    return ByteArray(value.length / 2) { index ->
        val offset = index * 2
        value.substring(offset, offset + 2).toInt(16).toByte()
    }
}

private fun machineId(): com.google.protobuf.ByteString {
    val digest = MessageDigest.getInstance("SHA-1")
    return com.google.protobuf.ByteString.copyFrom(digest.digest("android-workshop-demo".toByteArray()))
}

private fun Throwable.asAuthenticationException(prefix: String): SteamAuthenticationException =
    when (this) {
        is SteamAuthenticationException -> this
        is SteamServiceMethodException -> SteamAuthenticationException(
            resultCode = resultCode,
            message = listOfNotNull(prefix, steamMessage ?: message).joinToString(": "),
            cause = this,
        )

        else -> SteamAuthenticationException(
            resultCode = 2,
            message = listOfNotNull(prefix, message).joinToString(": "),
            cause = this,
        )
    }
