// ! Bu araç @kerimmkirac tarafından | @CS-Karma için yazılmıştır!
// Kotlin port of ResolveURL's "Byse" resolver (Filemoon/Byse mirror network):
// https://github.com/Gujal00/ResolveURL/blob/master/script.module.resolveurl/lib/resolveurl/plugins/byse.py
@file:OptIn(ExperimentalUnsignedTypes::class)

package com.kerimmkirac

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.yield
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.URI
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.interfaces.ECPublicKey
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

open class FileMoonExtractor(
    override var mainUrl: String = "https://filemoon.to"
) : ExtractorApi() {
    override var name = "Filemoon"
    override val requiresReferer = false

    companion object {
        private const val TAG = "FileMoonExtractor"

        private const val UA =
            "Mozilla/5.0 (Linux; Android 10; TX6s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"

        private val REDIRECT_DOMAINS = setOf(
            "boosteradx.online", "byse.sx", "streamlyplayer.online"
        )

        // Mirrors ByseResolver.pattern - matches host + media id out of an embed url.
        private val URL_REGEX = Regex(
            "(?://|\\.)((?:filemoon|cinegrab|moonmov|kerapoxy|furher|1azayf9w|81u6xl9d|f16px|sb1254w9megshle|" +
                "smdfs40r|bf0skv|z1ekv717|l1afav|222i8x|8mhlloqo|96ar|xcoic|f51rm|c1z39|boosteradx|streamlyplayero?|moflix-stream|" +
                "(?:embedplay)?byse(?:sayeveum|tayico|zejataos|koze|sukior|jikuar|fujedu|dikamoum|buho|wihe|lapuix|vepoin|zoxexe)?)" +
                "\\.(?:sx|top?|s?k?in|link|nl|wf|com|eu|art|pro|cc|xyz|org|fun|net|lol|online))" +
                "/(?:(?:e|d|download)/)?([0-9a-zA-Z]+)"
        )

        val DOMAINS = listOf(
            "https://filemoon.to", "https://filemoon.sx", "https://filemoon.in", "https://filemoon.link",
            "https://filemoon.wf", "https://filemoon.eu", "https://filemoon.art", "https://filemoon.nl",
            "https://cinegrab.com", "https://moonmov.pro", "https://96ar.com", "https://kerapoxy.cc",
            "https://furher.in", "https://1azayf9w.xyz", "https://81u6xl9d.xyz", "https://smdfs40r.skin",
            "https://c1z39.com", "https://bf0skv.org", "https://z1ekv717.fun", "https://l1afav.net",
            "https://222i8x.lol", "https://8mhlloqo.fun", "https://f51rm.com", "https://xcoic.com",
            "https://boosteradx.online", "https://streamlyplayer.online", "https://streamlyplayero.online",
            "https://bysewihe.com", "https://byselapuix.com", "https://embedplaybyse.top",
            "https://sb1254w9megshle.org", "https://moflix-stream.link", "https://bysezoxexe.com",
            "https://f16px.com", "https://bysesayeveum.com", "https://bysetayico.com", "https://bysevepoin.com",
            "https://bysezejataos.com", "https://bysekoze.com", "https://bysesukior.com", "https://bysejikuar.com",
            "https://bysefujedu.com", "https://bysedikamoum.com", "https://bysebuho.com", "https://byse.sx"
        )


        private fun b64UrlEncode(bytes: ByteArray): String =
            Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        private fun b64UrlDecode(value: String): ByteArray {
            val padded = value + "=".repeat((4 - value.length % 4) % 4)
            return Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
        }

        private fun sha256(bytes: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(bytes)

        private fun fh(value: Double): String = b64UrlEncode(sha256(value.toString().toByteArray(Charsets.US_ASCII)))

        private fun bigIntTo32Bytes(n: BigInteger): ByteArray {
            val raw = n.toByteArray()
            return when {
                raw.size == 32 -> raw
                raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
                else -> ByteArray(32 - raw.size) + raw
            }
        }

        private fun derToRawEcdsaSignature(der: ByteArray, componentLen: Int = 32): ByteArray {
            var offset = 1 // skip SEQUENCE tag (0x30)
            var seqLen = der[offset].toInt() and 0xFF
            offset++
            if (seqLen and 0x80 != 0) offset += (seqLen and 0x7F)

            offset++ // skip INTEGER tag (0x02) for r
            val rLen = der[offset].toInt() and 0xFF
            offset++
            val rBytes = der.copyOfRange(offset, offset + rLen)
            offset += rLen

            offset++ // skip INTEGER tag (0x02) for s
            val sLen = der[offset].toInt() and 0xFF
            offset++
            val sBytes = der.copyOfRange(offset, offset + sLen)

            fun fixedLen(b: ByteArray): ByteArray {
                var trimmed = b
                var start = 0
                while (start < trimmed.size - 1 && trimmed[start] == 0.toByte()) start++
                trimmed = trimmed.copyOfRange(start, trimmed.size)
                return if (trimmed.size >= componentLen) trimmed.copyOfRange(trimmed.size - componentLen, trimmed.size)
                else ByteArray(componentLen - trimmed.size) + trimmed
            }

            return fixedLen(rBytes) + fixedLen(sBytes)
        }

        private fun randomHex(byteLen: Int): String {
            val bytes = ByteArray(byteLen)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        // ---- custom PoW hash (mirrors ByseResolver.gr/ye/re/wr) -------------------

        private fun rot(state: UIntArray) {
            state[0] += state[1]; state[3] = (state[3] xor state[0]).rotateLeft(16)
            state[2] += state[3]; state[1] = (state[1] xor state[2]).rotateLeft(12)
            state[0] += state[1]; state[3] = (state[3] xor state[0]).rotateLeft(8)
            state[2] += state[3]; state[1] = (state[1] xor state[2]).rotateLeft(7)
        }

        private fun powHash(input: ByteArray): UIntArray {
            val e = uintArrayOf(1779033703u, 3144134277u, 1013904242u, 2773480762u)
            for (byte in input) {
                e[0] += (byte.toInt() and 0xFF).toUInt()
                e[0] = e[0].rotateLeft(7)
                rot(e)
            }
            repeat(8) { rot(e) }

            val scratch = UIntArray(512)
            for (i in 0 until 512) {
                rot(e)
                scratch[i] = e[0] xor e[2]
            }

            val lt = 511u
            val lr = 2654435761u
            val hr = 2246822519u
            repeat(2) {
                for (s in 0 until 512) {
                    val a = (scratch[s] and lt).toInt()
                    var c = scratch[s] + scratch[a]
                    c = c.rotateLeft(13)
                    c = c xor (scratch[(s + 1) and 511] * lr)
                    scratch[s] = c
                    e[0] = e[0] xor c
                    rot(e)
                }
            }

            val out = UIntArray(8)
            val chunk = 512 / 8
            for (i in 0 until 8) {
                rot(e)
                var s = e[0]
                val base = i * chunk
                for (c in 0 until chunk) {
                    val d = scratch[base + c]
                    s += d
                    s = s.rotateLeft(5)
                    s = s xor (d * hr)
                }
                out[i] = s xor e[2]
            }
            return out
        }

        private fun leadingZeroBits(hash: UIntArray): Int {
            var total = 0
            for (word in hash) {
                if (word == 0u) {
                    total += 32
                    continue
                }
                return total + word.countLeadingZeroBits()
            }
            return total
        }

        private suspend fun solvePow(nonce: String, difficulty: Int, timeoutSec: Double = 20.0): String? {
            if (difficulty <= 0) return "0"
            val start = System.currentTimeMillis()
            val prefix = "$nonce:"
            var s = 0L
            while (true) {
                repeat(1024) {
                    val digest = powHash((prefix + s).toByteArray(Charsets.US_ASCII))
                    if (leadingZeroBits(digest) >= difficulty) return s.toString()
                    s++
                }
                if ((System.currentTimeMillis() - start) > timeoutSec * 1000) return null
                yield()
            }
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val match = URL_REGEX.find(url) ?: return
            val matchedHost = match.groupValues[1]
            val mediaId = match.groupValues[2]
            val host = if (matchedHost in REDIRECT_DOMAINS) "streamlyplayero.online" else matchedHost

            resolve(host, mediaId, callback)
        } catch (e: Exception) {
            Log.e(TAG, "getUrl failed for $url: ${e.message}", e)
        }
    }

    private suspend fun resolve(host: String, mediaId: String, callback: (ExtractorLink) -> Unit) {
        val webUrl = "https://$host/e/$mediaId"
        var ref = "https://$host/"
        val headers = mutableMapOf(
            "User-Agent" to UA,
            "Referer" to ref,
            "Origin" to ref.trimEnd('/')
        )

        var embed = ""
        var details = fetchJson("${ref}api/videos/$mediaId/details", headers)
        if (details == null) {
            embed = "embed/"
            details = fetchJson("${ref}api/videos/$mediaId/${embed}details", headers)
                ?: run {
                    Log.e(TAG, "Video link not found for $webUrl")
                    return
                }
        }

        val embedUrl = details.optString("embed_frame_url").orEmpty()
        if (embedUrl.isNotEmpty()) {
            ref = originOf(embedUrl)
            headers["X-Embed-Parent"] = webUrl
            headers["Referer"] = ref
            headers["Origin"] = ref.trimEnd('/')
        }

        val settings = fetchJson("${ref}api/videos/$mediaId/${embed}settings", headers) ?: return

        val data = if (settings.optBoolean("captcha_required", false)) {
            solveCaptchaAndGetPlayback(ref, mediaId, embed, headers)
        } else {
            postJson(
                "${ref}api/videos/$mediaId/${embed}playback",
                headers,
                buildFingerprint(16, 0.83, 0.94),
                40
            )
        } ?: run {
            Log.e(TAG, "Playback request failed for $webUrl")
            return
        }

        emitSources(data, ref, headers, callback)
    }

    private suspend fun solveCaptchaAndGetPlayback(
        ref: String,
        mediaId: String,
        embed: String,
        headers: MutableMap<String, String>
    ): JSONObject? {
        val challengeRes = app.post("${ref}api/videos/access/challenge", headers = headers, data = emptyMap(), timeout = 20)
        if (challengeRes.code !in 200..299) return null
        val challenge = JSONObject(challengeRes.text)

        val attest = postJson("${ref}api/videos/access/attest", headers, buildAttestPayload(challenge), 40) ?: return null

        val fingerprint = JSONObject().apply {
            put("token", attest.optString("token"))
            put("viewer_id", attest.optString("viewer_id"))
            put("device_id", attest.optString("device_id"))
            put("confidence", attest.optDouble("confidence"))
        }

        val captcha = postJson(
            "${ref}api/videos/$mediaId/${embed}captcha",
            headers,
            JSONObject().put("fingerprint", fingerprint),
            40
        ) ?: return null

        val solution = solvePow(captcha.optString("pow_nonce"), captcha.optInt("pow_difficulty", 0))
            ?: run {
                Log.e(TAG, "Unable to solve captcha PoW")
                return null
            }

        val verifyBody = JSONObject().apply {
            put("pow_token", captcha.optString("pow_token"))
            put("solution", solution)
            put("fingerprint", fingerprint)
        }
        val verify = postJson("${ref}api/videos/$mediaId/${embed}captcha/verify", headers, verifyBody, 40) ?: return null
        headers["X-Captcha-Token"] = verify.optString("token")

        return postJson(
            "${ref}api/videos/$mediaId/${embed}playback",
            headers,
            JSONObject().put("fingerprint", fingerprint),
            40
        )
    }

    private fun buildAttestPayload(challenge: JSONObject): JSONObject {
        val nonce = challenge.optString("nonce")
        val challengeId = challenge.optString("challenge_id")

        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGenerator.generateKeyPair()

        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(nonce.toByteArray(Charsets.US_ASCII))
        val signature = b64UrlEncode(derToRawEcdsaSignature(signer.sign()))

        val publicKey = keyPair.public as ECPublicKey
        val publicKeyJwk = JSONObject().apply {
            put("crv", "P-256")
            put("ext", true)
            put("key_ops", JSONArray(listOf("verify")))
            put("kty", "EC")
            put("x", b64UrlEncode(bigIntTo32Bytes(publicKey.w.affineX)))
            put("y", b64UrlEncode(bigIntTo32Bytes(publicKey.w.affineY)))
        }

        val r = Random.nextDouble()
        val client = JSONObject().apply {
            put("user_agent", UA)
            put("architecture", "arm")
            put("bitness", "32")
            put("platform", "Android")
            put("platform_version", "10.0.0")
            put("model", "TX6s")
            put("ua_full_version", "137.0.7337.0")
            put("brand_full_versions", JSONArray().put(JSONObject().apply {
                put("brand", "Chromium")
                put("version", "137.0.7337.0")
            }))
            put("pixel_ratio", 1)
            put("screen_width", 1280)
            put("screen_height", 720)
            put("color_depth", 24)
            put("languages", JSONArray(listOf("en-US")))
            put("timezone", "America/New_York")
            put("hardware_concurrency", 4)
            put("device_memory", 2)
            put("touch_points", 1)
            put("webgl_vendor", "Google Inc. (ARM)")
            put("webgl_renderer", "ANGLE (ARM, Mali-G31 MP2, OpenGL ES 3.2)")
            put("canvas_hash", fh(r))
            put("audio_hash", fh(r + 1))
            put("webgl_params_hash", fh(r + 2))
            put("fonts_hash", fh(r + 3))
            put("codecs_hash", fh(r + 4))
            put("media_devices", "ai1ao1vi4")
            put("pointer_type", "coarse")
            put("extra", JSONObject().apply {
                put("vendor", "Google Inc.")
                put("appVersion", UA.removePrefix("Mozilla/"))
            })
        }

        return JSONObject().apply {
            put("viewer_id", "")
            put("device_id", "")
            put("challenge_id", challengeId)
            put("nonce", nonce)
            put("signature", signature)
            put("public_key", publicKeyJwk)
            put("client", client)
            put("storage", JSONObject())
            put("attributes", JSONObject().put("entropy", "high"))
        }
    }

    private fun buildFingerprint(byteLen: Int, minConfidence: Double, maxConfidence: Double): JSONObject {
        val viewerId = randomHex(byteLen)
        val deviceId = randomHex(byteLen)
        val ctime = System.currentTimeMillis() / 1000
        val confidence = Math.round(Random.nextDouble(minConfidence, maxConfidence) * 100.0) / 100.0

        val tokenData = JSONObject().apply {
            put("viewer_id", viewerId)
            put("device_id", deviceId)
            put("confidence", confidence)
            put("iat", ctime)
            put("exp", ctime + 600)
        }
        val tokenBData = b64UrlEncode(tokenData.toString().toByteArray(Charsets.UTF_8))
        val tokenSig = b64UrlEncode(sha256(tokenBData.toByteArray(Charsets.UTF_8)))
        val token = "$tokenBData.$tokenSig"

        val fingerprint = JSONObject().apply {
            put("viewer_id", viewerId)
            put("device_id", deviceId)
            put("confidence", confidence)
            put("token", token)
        }
        return JSONObject().put("fingerprint", fingerprint)
    }

    private fun deriveKey(keyParts: JSONArray, version: Int?): ByteArray {
        val parts = if (version != null && version != 0) {
            listOf(keyParts.getString(version - 1), keyParts.getString(keyParts.length() - version))
        } else {
            (0 until keyParts.length()).map { keyParts.getString(it) }
        }
        return parts.map { b64UrlDecode(it) }.reduce { acc, bytes -> acc + bytes }
    }

    private fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, payload: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(payload)
    }

    private suspend fun emitSources(
        data: JSONObject,
        ref: String,
        headers: Map<String, String>,
        callback: (ExtractorLink) -> Unit
    ) {
        val sources = data.optJSONArray("sources")
        if (sources != null && sources.length() > 0) {
            emitFromArray(sources, ref, headers, callback)
            return
        }

        val playback = data.optJSONObject("playback") ?: run {
            Log.e(TAG, "No sources/playback in response")
            return
        }

        try {
            val iv = b64UrlDecode(playback.getString("iv"))
            val keyParts = playback.getJSONArray("key_parts")
            val version = if (playback.isNull("version")) null else playback.optInt("version")
            val key = deriveKey(keyParts, version)
            val payloadBytes = b64UrlDecode(playback.getString("payload"))

            val plaintext = aesGcmDecrypt(key, iv, payloadBytes)
            val decrypted = JSONObject(String(plaintext, Charsets.ISO_8859_1))
            val decryptedSources = decrypted.optJSONArray("sources") ?: return

            val cleanHeaders = headers.toMutableMap().apply {
                remove("X-Embed-Parent")
                remove("X-Captcha-Token")
            }
            emitFromArray(decryptedSources, ref, cleanHeaders, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt playback payload: ${e.message}", e)
        }
    }

    private suspend fun emitFromArray(
        sources: JSONArray,
        ref: String,
        headers: Map<String, String>,
        callback: (ExtractorLink) -> Unit
    ) {
        for (i in 0 until sources.length()) {
            val source = sources.optJSONObject(i) ?: continue
            val label = source.optString("label").orEmpty()
            var sourceUrl = source.optString("url").orEmpty()
            if (sourceUrl.isEmpty()) continue

            if (sourceUrl.startsWith("/")) {
                sourceUrl = resolveRelative(ref, sourceUrl)
            }
            val finalUrl = followRedirect(sourceUrl, headers)
            val linkType = if (finalUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            val linkName = if (label.isNotBlank()) "$name $label" else name

            callback.invoke(
                newExtractorLink(name, linkName, finalUrl, type = linkType) {
                    this.referer = headers["Referer"] ?: ref
                    this.quality = getQualityFromName(label)
                    this.headers = headers
                }
            )
        }
    }

    private suspend fun fetchJson(url: String, headers: Map<String, String>): JSONObject? {
        return try {
            val res = app.get(url, headers = headers)
            if (res.code !in 200..299) null else JSONObject(res.text)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        body: JSONObject,
        timeoutSec: Long
    ): JSONObject? {
        return try {
            val requestBody = body.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val res = app.post(url, requestBody = requestBody, headers = headers, timeout = timeoutSec)
            if (res.code !in 200..299) null else JSONObject(res.text)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun followRedirect(url: String, headers: Map<String, String>): String {
        return try {
            val res = app.get(url, headers = headers, allowRedirects = false)
            res.headers["location"] ?: res.headers["Location"] ?: res.url
        } catch (e: Exception) {
            url
        }
    }

    private fun resolveRelative(base: String, relative: String): String = try {
        URI(base).resolve(relative).toString()
    } catch (e: Exception) {
        relative
    }

    private fun originOf(url: String): String = try {
        URI(url).resolve("/").toString()
    } catch (e: Exception) {
        url
    }
}
