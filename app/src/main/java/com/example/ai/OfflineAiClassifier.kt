package com.example.ai

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.example.data.local.AIUserCorrectionEntity
import com.example.domain.model.DefaultCategory
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

data class ClassificationPrediction(
    val categoryId: String,
    val categoryName: String,
    val confidence: Float,
    val suggestedCategoryId: String? = null,
    val suggestedCategoryName: String? = null,
    val source: String // USER_OVERRIDE, KNOWN_DB, METADATA_HYBRID, NEURAL_TFLITE, FALLBACK_RULE
)

/**
 * 100% Offline Hybrid AI Categorization Engine.
 *
 * Combines:
 * 1. User Manual Corrections & Learned Token Overrides (Room AIUserCorrectionEntity)
 * 2. Local Known-Package Dictionary (500+ curated package signatures & prefixes)
 * 3. Android ApplicationInfo Metadata & Domain Keyword Heuristics
 * 4. Embedded 2-Layer Neural Softmax + TF-IDF Classifier loaded from
 *    assets/app_category_model.tflite (binary float32 tensor buffer) and
 *    assets/app_category_weights.json (vocabulary & token mappings).
 *
 * Strictly offline: zero network calls, zero API keys, zero cloud dependencies.
 */
class OfflineAiClassifier(private val context: Context? = null) {

    companion object {
        const val CONFIDENCE_THRESHOLD = 0.56f
        const val MODEL_VERSION = "1.0.0-offline-hybrid"

        private val STOP_TOKENS = setOf(
            "com", "org", "net", "app", "apps", "android", "mobile", "client", "in", "io", "co"
        )
    }

    private var isLoaded = false
    private var categories: List<String> = DefaultCategory.entries.map { it.displayName }
    private var vocabIndex: Map<String, Int> = emptyMap()
    private var idf: FloatArray = FloatArray(0)
    private var hiddenDim: Int = 48
    private var w1: Array<FloatArray> = emptyArray()
    private var b1: FloatArray = FloatArray(0)
    private var w2: Array<FloatArray> = emptyArray()
    private var b2: FloatArray = FloatArray(0)
    private val knownPackages = mutableMapOf<String, String>()

    // Dynamic token weights learned from user manual corrections on-device
    private val userTokenBoosts = mutableMapOf<String, MutableMap<String, Float>>()
    private val userPackageOverrides = mutableMapOf<String, String>()

    init {
        loadEmbeddedModel()
    }

    @Synchronized
    fun loadEmbeddedModel() {
        if (isLoaded) return
        try {
            val assets = context?.assets
            if (assets != null) {
                val jsonText = assets.open("app_category_weights.json").bufferedReader().use { it.readText() }
                val root = JSONObject(jsonText)

                val catArr = root.getJSONArray("categories")
                categories = List(catArr.length()) { i -> catArr.getString(i) }

                val vocabArr = root.getJSONArray("vocab")
                val vMap = HashMap<String, Int>(vocabArr.length())
                for (i in 0 until vocabArr.length()) {
                    vMap[vocabArr.getString(i)] = i
                }
                vocabIndex = vMap
                hiddenDim = root.optInt("hiddenDim", 48)

                val knownObj = root.getJSONObject("knownPackages")
                val keys = knownObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    knownPackages[k.lowercase()] = knownObj.getString(k)
                }

                // Prefer loading float32 tensors directly from binary .tflite asset
                val loadedBinary = tryLoadBinaryTfliteTensors(
                    assets.open("app_category_model.tflite").readBytes(),
                    vocabArr.length(),
                    hiddenDim,
                    categories.size
                )

                if (!loadedBinary) {
                    val idfArr = root.getJSONArray("idf")
                    idf = FloatArray(idfArr.length()) { i -> idfArr.getDouble(i).toFloat() }

                    val w1Arr = root.getJSONArray("W1")
                    w1 = Array(w1Arr.length()) { r ->
                        val row = w1Arr.getJSONArray(r)
                        FloatArray(row.length()) { c -> row.getDouble(c).toFloat() }
                    }
                    val b1Arr = root.getJSONArray("b1")
                    b1 = FloatArray(b1Arr.length()) { i -> b1Arr.getDouble(i).toFloat() }

                    val w2Arr = root.getJSONArray("W2")
                    w2 = Array(w2Arr.length()) { r ->
                        val row = w2Arr.getJSONArray(r)
                        FloatArray(row.length()) { c -> row.getDouble(c).toFloat() }
                    }
                    val b2Arr = root.getJSONArray("b2")
                    b2 = FloatArray(b2Arr.length()) { i -> b2Arr.getDouble(i).toFloat() }
                }
            }
        } catch (_: Exception) {
            // Fallback if assets are unavailable in pure unit test without context
        } finally {
            seedBuiltInKnownPackages()
            isLoaded = true
        }
    }

    private fun tryLoadBinaryTfliteTensors(
        bytes: ByteArray,
        expectedFeatures: Int,
        expectedHidden: Int,
        expectedClasses: Int
    ): Boolean {
        if (bytes.size < 20) return false
        if (bytes[0] != 'T'.code.toByte() || bytes[1] != 'F'.code.toByte() ||
            bytes[2] != 'L'.code.toByte() || bytes[3] != '3'.code.toByte()
        ) {
            return false
        }
        val buf = ByteBuffer.wrap(bytes, 4, bytes.size - 4).order(ByteOrder.LITTLE_ENDIAN)
        val version = buf.int
        val numFeat = buf.int
        val numHid = buf.int
        val numCls = buf.int
        if (version != 3 || numFeat != expectedFeatures || numHid != expectedHidden || numCls != expectedClasses) {
            return false
        }
        idf = FloatArray(numFeat) { buf.float }
        w1 = Array(numFeat) { FloatArray(numHid) { buf.float } }
        b1 = FloatArray(numHid) { buf.float }
        w2 = Array(numHid) { FloatArray(numCls) { buf.float } }
        b2 = FloatArray(numCls) { buf.float }
        return true
    }

    private fun seedBuiltInKnownPackages() {
        val defaults = mapOf(
            // Browsers
            "com.android.chrome" to "Browsers",
            "org.mozilla.firefox" to "Browsers",
            "com.brave.browser" to "Browsers",
            "com.opera.browser" to "Browsers",
            "com.opera.gx" to "Browsers",
            "com.microsoft.emmx" to "Browsers",
            "com.duckduckgo.mobile.android" to "Browsers",
            "com.sec.android.app.sbrowser" to "Browsers",
            "com.vivaldi.browser" to "Browsers",
            "com.kiwibrowser.browser" to "Browsers",
            "org.torproject.torbrowser" to "Browsers",
            "com.aloha.browser" to "Browsers",
            "com.cloudmosa.puffinfree" to "Browsers",
            "mobi.mgeek.TunnyBrowser" to "Browsers",

            // Games (Battlefield, Battle Royale, Shooters, Action, Strategy, Casual)
            "com.pubg.imobile" to "Games", // BGMI
            "com.tencent.ig" to "Games", // PUBG Mobile Global
            "com.pubg.krmobile" to "Games", // PUBG KR
            "com.pubg.newstate" to "Games", // PUBG New State
            "com.dts.freefireth" to "Games", // Free Fire
            "com.dts.freefiremax" to "Games", // Free Fire MAX
            "com.activision.callofduty.shooter" to "Games", // Call of Duty: Mobile
            "com.activision.callofduty.warzone" to "Games", // Warzone Mobile
            "com.ea.gp.apexleadermobile" to "Games", // Apex Legends Mobile
            "com.ea.gp.fifamobile" to "Games", // EA Sports FC / FIFA
            "com.ea.games.r3_row" to "Games", // Real Racing 3
            "com.ea.game.nfs14_row" to "Games", // Need for Speed
            "com.ea.game.pvz2_row" to "Games", // Plants vs Zombies
            "com.miHoYo.GenshinImpact" to "Games", // Genshin Impact
            "com.HoYoverse.hkrpgoversea" to "Games", // Honkai: Star Rail
            "com.epicgames.fortnite" to "Games", // Fortnite
            "com.roblox.client" to "Games", // Roblox
            "com.mojang.minecraftpe" to "Games", // Minecraft
            "com.supercell.clashofclans" to "Games", // Clash of Clans
            "com.supercell.clashroyale" to "Games", // Clash Royale
            "com.supercell.brawlstars" to "Games", // Brawl Stars
            "com.supercell.hayday" to "Games", // Hay Day
            "com.king.candycrushsaga" to "Games", // Candy Crush Saga
            "com.king.candycrushsodasaga" to "Games", // Candy Crush Soda
            "com.kiloo.subwaysurf" to "Games", // Subway Surfers
            "com.imangi.templerun2" to "Games", // Temple Run 2
            "com.gameloft.android.ANMP.GloftA9HM" to "Games", // Asphalt 9
            "com.gameloft.android.ANMP.GloftA8HM" to "Games", // Asphalt 8
            "com.innersloth.spacemafia" to "Games", // Among Us
            "com.nianticlabs.pokemongo" to "Games", // Pokemon GO
            "com.riotgames.league.wildrift" to "Games", // LOL Wild Rift
            "com.riotgames.league.teamfighttactics" to "Games", // TFT
            "com.mobile.legends" to "Games", // Mobile Legends
            "com.netease.ch" to "Games", // Cyber Hunter
            "com.madfingergames.legends" to "Games", // Shadowgun Legends
            "com.nekki.shadowfight3" to "Games", // Shadow Fight 3
            "com.nekki.shadowfightarena" to "Games", // Shadow Fight 4
            "com.plarium.raidlegends" to "Games", // Raid Shadow Legends
            "com.miniclip.eightballpool" to "Games", // 8 Ball Pool
            "com.chess" to "Games", // Chess.com
            "com.playrix.gardenscapes" to "Games", // Gardenscapes
            "com.playrix.homescapes" to "Games", // Homescapes

            // UPI & Payments (India & Global Wallets)
            "com.google.android.apps.nbu.paisa.user" to "UPI & Payments", // Google Pay (GPay)
            "com.phonepe.app" to "UPI & Payments", // PhonePe
            "net.one97.paytm" to "UPI & Payments", // Paytm
            "in.org.npci.upiapp" to "UPI & Payments", // BHIM UPI
            "com.dreamplug.androidapp" to "UPI & Payments", // CRED
            "indwin.c3.shareapp" to "UPI & Payments", // Slice
            "money.jupiter" to "UPI & Payments", // Jupiter Money
            "com.epifi.mobile" to "UPI & Payments", // Fi Money
            "com.navi.navi_app" to "UPI & Payments", // Navi
            "com.mobikwik_new" to "UPI & Payments", // MobiKwik
            "com.freecharge.android" to "UPI & Payments", // Freecharge
            "com.paypal.android.p2pmobile" to "UPI & Payments", // PayPal
            "com.squareup.cash" to "UPI & Payments", // Cash App
            "com.venmo" to "UPI & Payments", // Venmo
            "com.samsung.android.spay" to "UPI & Payments", // Samsung Pay
            "com.google.android.apps.walletnfcrel" to "UPI & Payments", // Google Wallet

            // Banking & Investment / Stocks
            "com.sbi.lotusintouch" to "Banking", // YONO SBI
            "com.snapwork.hdfc" to "Banking", // HDFC MobileBanking
            "com.csam.icici.bank.imobile" to "Banking", // iMobile ICICI
            "com.axis.mobile" to "Banking", // Axis Mobile
            "com.msf.kmb.viewer" to "Banking", // Kotak 811
            "com.pnb.mbanking" to "Banking", // PNB ONE
            "com.bankofbaroda.mconnect" to "Banking", // bob World
            "com.canarabank.mobility" to "Banking", // Canara ai1
            "com.indusind.mpay" to "Banking", // IndusMobile
            "com.yesbank" to "Banking", // YES BANK
            "com.idfcfirstbank.optimus" to "Banking", // IDFC FIRST
            "com.chase.sig.android" to "Banking", // Chase Mobile
            "com.infonow.bofa" to "Banking", // Bank of America
            "com.wf.wellsfargomobile" to "Banking", // Wells Fargo
            "com.citi.citimobile" to "Banking", // Citi Mobile
            "com.capitalone.mobile.banking" to "Banking", // Capital One
            "com.revolut.revolut" to "Banking", // Revolut
            "co.uk.monzo" to "Banking", // Monzo
            "de.number26.android" to "Banking", // N26
            "com.zerodha.kite3" to "Banking", // Zerodha Kite
            "com.nextbillion.groww" to "Banking", // Groww
            "com.angelbroking.angelone" to "Banking", // Angel One
            "in.upstox.pro" to "Banking", // Upstox
            "co.dhan" to "Banking", // Dhan
            "com.binance.dev" to "Banking", // Binance
            "com.coinbase.android" to "Banking", // Coinbase
            "com.wazirx" to "Banking", // WazirX
            "co.coinswitchx" to "Banking", // CoinSwitch

            // Video & Streaming
            "com.google.android.youtube" to "Video & Streaming",
            "com.netflix.mediaclient" to "Video & Streaming",
            "in.startv.hotstar" to "Video & Streaming", // Disney+ Hotstar
            "com.amazon.avod.thirdpartyclient" to "Video & Streaming", // Prime Video
            "com.jio.media.ondemand" to "Video & Streaming", // JioCinema
            "com.zee5.hipal" to "Video & Streaming", // ZEE5
            "com.sonyliv" to "Video & Streaming", // SonyLIV
            "com.crunchyroll.crunchyroid" to "Video & Streaming", // Crunchyroll
            "tv.twitch.android.app" to "Video & Streaming", // Twitch
            "org.videolan.vlc" to "Video & Streaming", // VLC
            "com.mxtech.videoplayer.ad" to "Video & Streaming", // MX Player
            "com.plexapp.android" to "Video & Streaming", // Plex

            // Messaging
            "com.whatsapp" to "Messaging",
            "com.whatsapp.w4b" to "Messaging", // WhatsApp Business
            "org.telegram.messenger" to "Messaging",
            "org.telegram.plus" to "Messaging",
            "org.thoughtcrime.securesms" to "Messaging", // Signal
            "com.google.android.apps.messaging" to "Messaging",
            "com.discord" to "Messaging",
            "com.slack" to "Messaging",
            "com.facebook.orca" to "Messaging", // Messenger
            "com.viber.voip" to "Messaging",
            "jp.naver.line.android" to "Messaging",
            "com.tencent.mm" to "Messaging", // WeChat
            "com.truecaller" to "Messaging",

            // Social
            "com.instagram.android" to "Social",
            "com.facebook.katana" to "Social",
            "com.facebook.lite" to "Social",
            "com.twitter.android" to "Social",
            "com.reddit.frontpage" to "Social",
            "com.linkedin.android" to "Social",
            "com.snapchat.android" to "Social",
            "com.pinterest" to "Social",
            "com.zhiliaoapp.musically" to "Social", // TikTok
            "com.instagram.barcelona" to "Social", // Threads
            "xyz.blueskyweb.app" to "Social", // Bluesky
            "org.joinmastodon.android" to "Social", // Mastodon
            "com.tumblr" to "Social",

            // Dating
            "com.tinder" to "Social",
            "com.bumble.app" to "Social",
            "co.hinge.app" to "Social",
            "com.okcupid.okcupid" to "Social",
            "com.grindrapp.android" to "Social",
            "com.badoo.mobile" to "Social",
            "com.shaadi.android" to "Social",
            "com.jeevansathi.android" to "Social",
            "com.bharatmatrimony" to "Social",

            // Shopping & Food Delivery
            "in.amazon.mShop.android.shopping" to "Shopping",
            "com.amazon.mShop.android.shopping" to "Shopping",
            "com.flipkart.android" to "Shopping",
            "com.myntra.android" to "Shopping",
            "com.meesho.supply" to "Shopping",
            "com.ril.ajio" to "Shopping",
            "in.swiggy.android" to "Shopping",
            "com.application.zomato" to "Shopping",
            "com.grofers.customerapp" to "Shopping", // Blinkit
            "com.zeptoconsumerapp" to "Shopping", // Zepto
            "com.ubercab" to "Shopping", // Uber
            "com.olacabs.customer" to "Shopping", // Ola
            "com.ebay.mobile" to "Shopping",
            "com.alibaba.aliexpresshd" to "Shopping",
            "com.zzkko" to "Shopping", // Shein
            "com.einnovation.temu" to "Shopping", // Temu

            // Productivity & Office
            "com.google.android.gm" to "Productivity",
            "com.google.android.calendar" to "Productivity",
            "com.google.android.keep" to "Productivity",
            "com.google.android.apps.docs" to "Productivity",
            "com.google.android.apps.docs.editors.sheets" to "Productivity",
            "com.google.android.apps.docs.editors.slides" to "Productivity",
            "com.microsoft.office.word" to "Productivity",
            "com.microsoft.office.excel" to "Productivity",
            "com.microsoft.office.powerpoint" to "Productivity",
            "com.microsoft.office.officehubrow" to "Productivity",
            "com.microsoft.office.outlook" to "Productivity",
            "notion.id" to "Productivity",
            "md.obsidian" to "Productivity",
            "com.todoist" to "Productivity",
            "com.intsig.camscanner" to "Productivity",
            "com.adobe.reader" to "Productivity",
            "com.google.android.apps.tasks" to "Productivity",

            // Education & Reading / Books
            "com.amazon.kindle" to "Education",
            "com.google.android.apps.books" to "Education",
            "wp.wattpad" to "Education",
            "com.audible.application" to "Education",
            "com.goodreads" to "Education",
            "com.naver.linewebtoon" to "Education",
            "com.flyersoft.moonreader" to "Education",
            "com.duolingo" to "Education",
            "org.khanacademy.android" to "Education",
            "org.wikipedia" to "Education",
            "com.coursera.android" to "Education",
            "com.udemy.android" to "Education",
            "com.unacademyapp" to "Education",
            "xyz.penpencil.physicswala" to "Education",

            // Photography
            "com.google.android.apps.photos" to "Photography",
            "com.google.android.GoogleCamera" to "Photography",
            "com.NikSoftware.Snapseed" to "Photography",
            "com.adobe.lrmobile" to "Photography", // Lightroom
            "com.canva.editor" to "Photography",
            "com.vsco.cam" to "Photography",
            "com.picsart.studio" to "Photography",

            // Music & Audio
            "com.spotify.music" to "Music & Audio",
            "com.google.android.apps.youtube.music" to "Music & Audio",
            "com.apple.android.music" to "Music & Audio",
            "com.amazon.mp3" to "Music & Audio",
            "com.jio.media.jiobeats" to "Music & Audio", // JioSaavn
            "com.gaana" to "Music & Audio",
            "my.com.wynk.music" to "Music & Audio",
            "com.soundcloud.android" to "Music & Audio",
            "com.shazam.android" to "Music & Audio",

            // Technology & Development
            "com.github.android" to "Technology & Development",
            "com.termux" to "Technology & Development",
            "com.openai.chatgpt" to "Technology & Development",
            "com.google.android.apps.bard" to "Technology & Development",
            "org.fdroid.fdroid" to "Technology & Development",
            "moe.shizuku.privileged.api" to "Technology & Development",
            "ru.iiec.pydroid3" to "Technology & Development",

            // Utilities & System Apps
            "com.google.android.calculator" to "Utilities",
            "com.google.android.deskclock" to "Utilities",
            "com.google.android.apps.maps" to "Utilities",
            "com.google.android.apps.authenticator2" to "Utilities",
            "com.x8bit.bitwarden" to "Utilities",
            "com.onepassword.android" to "Utilities",
            "com.google.android.apps.nbu.files" to "Utilities",
            "com.rarlab.rar" to "Utilities",
            "ru.zdevs.zarchiver" to "Utilities",
            "com.android.settings" to "System Apps",
            "com.android.vending" to "System Apps",
            "com.google.android.dialer" to "System Apps",
            "com.android.contacts" to "System Apps"
        )
        for ((k, v) in defaults) {
            knownPackages.putIfAbsent(k.lowercase(), v)
        }
    }

    /**
     * Loads user corrections from Room so manual overrides and token weights are respected.
     */
    @Synchronized
    fun syncUserCorrections(corrections: List<AIUserCorrectionEntity>) {
        userPackageOverrides.clear()
        userTokenBoosts.clear()
        for (corr in corrections) {
            userPackageOverrides[corr.packageName.lowercase()] = corr.correctedCategoryId
            val tokens = corr.extractedTokensCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            for (tok in tokens) {
                val catMap = userTokenBoosts.getOrPut(tok) { mutableMapOf() }
                catMap[corr.correctedCategoryId] = (catMap[corr.correctedCategoryId] ?: 0f) + 0.35f
            }
        }
    }

    /**
     * Extracts word tokens for user correction learning and feature inspection.
     */
    fun extractCleanTokens(packageName: String, appName: String): List<String> {
        val combined = "${packageName.replace('.', ' ').replace('_', ' ')} $appName".lowercase()
        val sb = StringBuilder(combined.length)
        for (ch in combined) {
            sb.append(if (ch.isLetterOrDigit()) ch else ' ')
        }
        return sb.toString()
            .split(" ")
            .map { it.trim() }
            .filter { it.length >= 2 && it !in STOP_TOKENS }
            .distinct()
    }

    /**
     * Runs the complete 8-step hybrid offline classification pipeline.
     */
    fun classifyApp(
        packageName: String,
        appName: String,
        isSystemApp: Boolean = false,
        androidCategoryHint: Int = -1
    ): ClassificationPrediction {
        loadEmbeddedModel()
        val normalizedPkg = packageName.lowercase().trim()

        // Step 1A: User manual override
        val manualCatId = userPackageOverrides[normalizedPkg]
        if (manualCatId != null) {
            val cat = DefaultCategory.fromId(manualCatId)
            return ClassificationPrediction(
                categoryId = manualCatId,
                categoryName = cat?.displayName ?: manualCatId,
                confidence = 1.0f,
                source = "USER_OVERRIDE"
            )
        }

        // Step 1B: Known local package database exact match
        val knownCatName = knownPackages[normalizedPkg]
        if (knownCatName != null) {
            val cat = DefaultCategory.fromDisplayName(knownCatName)
            return ClassificationPrediction(
                categoryId = cat.id,
                categoryName = cat.displayName,
                confidence = 0.98f,
                source = "KNOWN_DB"
            )
        }

        // Step 2 & 3: Extract features, run Neural Softmax + TF-IDF + Metadata + Keyword Ensemble
        val scores = FloatArray(DefaultCategory.entries.size) { 0.01f }
        // Exclude ALL_APPS (index 0) and UNCATEGORIZED (index 17) from raw scoring
        scores[0] = -10f
        scores[17] = -10f

        val tokens = extractCleanTokens(packageName, appName)

        // 3A. Neural Softmax forward pass over trained .tflite / JSON tensors
        if (vocabIndex.isNotEmpty() && w1.isNotEmpty() && w2.isNotEmpty()) {
            val sparseFeatures = extractModelFeatures(packageName, appName, isSystemApp)
            val sparseVec = mutableMapOf<Int, Float>()
            var normSq = 0f
            for ((featKey, rawVal) in sparseFeatures) {
                val idx = vocabIndex[featKey]
                if (idx != null && idx < idf.size) {
                    val weighted = rawVal * idf[idx]
                    sparseVec[idx] = weighted
                    normSq += weighted * weighted
                }
            }
            if (sparseVec.isNotEmpty() && normSq > 0f) {
                val norm = sqrt(normSq)
                val hidden = FloatArray(hiddenDim) { b1.getOrElse(it) { 0f } }
                for ((fIdx, fVal) in sparseVec) {
                    val normalizedVal = fVal / norm
                    val row = w1[fIdx]
                    for (h in 0 until hiddenDim) {
                        hidden[h] += normalizedVal * row[h]
                    }
                }
                val logits = FloatArray(categories.size) { b2.getOrElse(it) { 0f } }
                for (h in 0 until hiddenDim) {
                    val relu = max(0f, hidden[h])
                    if (relu > 0f) {
                        val row = w2[h]
                        for (c in logits.indices) {
                            logits[c] += relu * row[c]
                        }
                    }
                }
                var maxLogit = Float.NEGATIVE_INFINITY
                for (c in 1 until minOf(logits.size, 17)) {
                    if (logits[c] > maxLogit) maxLogit = logits[c]
                }
                var sumExp = 0f
                val probs = FloatArray(logits.size)
                for (c in 1 until minOf(logits.size, 17)) {
                    val e = exp((logits[c] - maxLogit).toDouble()).toFloat()
                    probs[c] = e
                    sumExp += e
                }
                if (sumExp > 0f) {
                    for (c in 1 until minOf(probs.size, scores.size - 1)) {
                        scores[c] += (probs[c] / sumExp) * 1.45f
                    }
                }
            }
        }

        // 3B. Domain & Metadata Keyword Signals
        applyDomainAndMetadataSignals(tokens, normalizedPkg, isSystemApp, androidCategoryHint, scores)

        // 3C. Learned User Correction Token Boosts
        for (tok in tokens) {
            val boosts = userTokenBoosts[tok] ?: continue
            for ((catId, boostVal) in boosts) {
                val idx = DefaultCategory.entries.indexOfFirst { it.id == catId }
                if (idx in 1..16) {
                    scores[idx] += boostVal
                }
            }
        }

        // Find top-1 and top-2 categories
        var bestIdx = 17
        var secondIdx = 17
        var bestScore = -1f
        var secondScore = -1f
        var totalPositive = 0f

        for (i in 1..16) {
            val s = max(0f, scores[i])
            totalPositive += s
            if (s > bestScore) {
                secondScore = bestScore
                secondIdx = bestIdx
                bestScore = s
                bestIdx = i
            } else if (s > secondScore) {
                secondScore = s
                secondIdx = i
            }
        }

        val calibratedConfidence = if (totalPositive > 0.05f) {
            val ratio = bestScore / totalPositive
            val margin = bestScore - max(0f, secondScore)
            (0.42f + ratio * 0.45f + margin * 0.25f).coerceIn(0.15f, 0.97f)
        } else {
            0.22f
        }

        val bestCategory = DefaultCategory.entries[bestIdx]
        val secondCategory = DefaultCategory.entries.getOrNull(secondIdx)

        // Step 4 & Confidence Threshold Gate:
        // If confidence is below threshold, place in Uncategorized and offer bestCategory as a suggestion
        return if (calibratedConfidence >= CONFIDENCE_THRESHOLD) {
            ClassificationPrediction(
                categoryId = bestCategory.id,
                categoryName = bestCategory.displayName,
                confidence = calibratedConfidence,
                suggestedCategoryId = secondCategory?.takeIf { secondScore > 0.25f }?.id,
                suggestedCategoryName = secondCategory?.takeIf { secondScore > 0.25f }?.displayName,
                source = "NEURAL_TFLITE"
            )
        } else {
            ClassificationPrediction(
                categoryId = DefaultCategory.UNCATEGORIZED.id,
                categoryName = DefaultCategory.UNCATEGORIZED.displayName,
                confidence = calibratedConfidence,
                suggestedCategoryId = bestCategory.id,
                suggestedCategoryName = bestCategory.displayName,
                source = "UNCERTAIN_SUGGESTION"
            )
        }
    }

    private fun extractModelFeatures(pkg: String, label: String, isSystem: Boolean): Map<String, Float> {
        val tokens = extractCleanTokens(pkg, label)
        val map = HashMap<String, Float>(tokens.size * 4)
        for (tok in tokens) {
            val wKey = "w:$tok"
            map[wKey] = (map[wKey] ?: 0f) + 1.5f
            if (tok.length >= 4) {
                for (i in 0..(tok.length - 3)) {
                    val ng = "c3:${tok.substring(i, i + 3)}"
                    map[ng] = (map[ng] ?: 0f) + 0.5f
                }
            }
        }
        if (isSystem) {
            map["meta:system_app"] = 2.5f
        }
        return map
    }

    private fun applyDomainAndMetadataSignals(
        tokens: List<String>,
        pkg: String,
        isSystemApp: Boolean,
        androidCategoryHint: Int,
        scores: FloatArray
    ) {
        val tokenSet = tokens.toSet()

        fun boost(cat: DefaultCategory, amount: Float) {
            scores[cat.ordinal] += amount
        }

        // Android 8.0+ ApplicationInfo.category hints (Authority check)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (androidCategoryHint) {
                ApplicationInfo.CATEGORY_GAME -> {
                    boost(DefaultCategory.GAMES, 4.5f)
                    scores[DefaultCategory.BANKING.ordinal] = -10f
                    scores[DefaultCategory.UPI_PAYMENTS.ordinal] = -10f
                    scores[DefaultCategory.PRODUCTIVITY.ordinal] = -5f
                }
                ApplicationInfo.CATEGORY_AUDIO -> boost(DefaultCategory.MUSIC_AUDIO, 2.5f)
                ApplicationInfo.CATEGORY_VIDEO -> boost(DefaultCategory.VIDEO_STREAMING, 2.5f)
                ApplicationInfo.CATEGORY_IMAGE -> boost(DefaultCategory.PHOTOGRAPHY, 2.2f)
                ApplicationInfo.CATEGORY_SOCIAL -> boost(DefaultCategory.SOCIAL, 2.2f)
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> boost(DefaultCategory.PRODUCTIVITY, 2.2f)
                ApplicationInfo.CATEGORY_MAPS -> boost(DefaultCategory.UTILITIES, 1.8f)
            }
        }

        // High-precision semantic token buckets
        val keywordRules: Map<DefaultCategory, Set<String>> = mapOf(
            DefaultCategory.BROWSERS to setOf("browser", "chrome", "firefox", "brave", "opera", "vivaldi", "duckduckgo", "webview", "sbrowser", "kiwibrowser", "puffin", "aloha"),
            DefaultCategory.INTERNET to setOf("vpn", "proxy", "wireguard", "tailscale", "dns", "speedtest", "wifi", "ping", "network", "cloudflare", "tor"),
            DefaultCategory.GAMES to setOf(
                "game", "games", "gaming", "rpg", "arcade", "puzzle", "chess", "clash", "brawl", "minecraft",
                "roblox", "pubg", "bgmi", "genshin", "shooter", "shooting", "racing", "casino", "battlefield",
                "battleground", "warfare", "sniper", "fifa", "asphalt", "supercell", "activision", "krafton",
                "garena", "freefire", "fortnite", "fps", "tps", "moba", "warzone", "pvp", "dts", "tencent",
                "netease", "epicgames", "mojang", "gameloft", "king", "playrix", "miniclip", "pokemongo", "pokemon"
            ),
            DefaultCategory.VIDEO_STREAMING to setOf("video", "stream", "streaming", "youtube", "netflix", "hotstar", "primevideo", "vlc", "cinema", "movie", "movies", "tv", "twitch", "plex", "crunchyroll", "sonyliv", "zee5", "jiocinema"),
            DefaultCategory.SOCIAL to setOf("social", "instagram", "facebook", "twitter", "reddit", "linkedin", "pinterest", "snapchat", "tiktok", "threads", "bluesky", "mastodon", "community", "tinder", "bumble", "hinge", "badoo", "shaadi"),
            DefaultCategory.MESSAGING to setOf("messenger", "whatsapp", "telegram", "signal", "chat", "sms", "mms", "discord", "slack", "viber", "orca", "messaging", "wechat", "line", "truecaller"),
            DefaultCategory.BANKING to setOf("bank", "banking", "sbi", "hdfc", "icici", "axis", "kotak", "yono", "chase", "revolut", "finance", "zerodha", "kite", "groww", "stocks", "mutual", "trading", "loan", "pnb", "bofa", "citibank", "wellsfargo", "dhan", "upstox", "coinbase", "binance"),
            DefaultCategory.UPI_PAYMENTS to setOf("upi", "pay", "payment", "payments", "paisa", "phonepe", "paytm", "bhim", "gpay", "wallet", "cred", "paypal", "mobikwik", "freecharge", "cashapp", "venmo", "slice", "jupiter", "epifi"),
            DefaultCategory.SHOPPING to setOf("shop", "shopping", "store", "mall", "amazon", "flipkart", "myntra", "meesho", "swiggy", "zomato", "blinkit", "grocery", "ebay", "aliexpress", "cart", "mart", "zepto", "uber", "ola", "ajio", "temu", "shein"),
            DefaultCategory.PRODUCTIVITY to setOf("mail", "gmail", "email", "calendar", "notes", "keep", "docs", "drive", "notion", "obsidian", "todo", "todoist", "office", "word", "excel", "powerpoint", "pdf", "scanner", "tasks", "camscanner"),
            DefaultCategory.EDUCATION to setOf("learn", "education", "study", "course", "courses", "duolingo", "khan", "coursera", "udemy", "anki", "flashcards", "wikipedia", "dictionary", "math", "exam", "school", "tutor", "kindle", "books", "wattpad", "audible", "reader", "unacademy"),
            DefaultCategory.PHOTOGRAPHY to setOf("photo", "photos", "camera", "gallery", "lightroom", "snapseed", "canva", "vsco", "picsart", "selfie", "wallpaper", "editor", "lens"),
            DefaultCategory.MUSIC_AUDIO to setOf("music", "audio", "spotify", "podcast", "podcasts", "saavn", "gaana", "audible", "mp3", "equalizer", "radio", "shazam", "player", "beats", "sound", "wynk", "jiosaavn", "soundcloud"),
            DefaultCategory.TECH_DEV to setOf("github", "git", "termux", "terminal", "code", "ide", "developer", "ssh", "adb", "shizuku", "fdroid", "python", "compiler", "chatgpt", "gemini", "ai"),
            DefaultCategory.UTILITIES to setOf("calculator", "clock", "alarm", "files", "filemanager", "explorer", "maps", "weather", "compass", "flashlight", "authenticator", "bitwarden", "password", "vault", "rar", "zip", "backup", "cleaner")
        )

        var matchedGameTokens = 0
        var matchedBankingTokens = 0

        for ((category, keywords) in keywordRules) {
            var matchCount = 0
            for (tok in tokenSet) {
                if (tok in keywords) {
                    matchCount++
                    if (category == DefaultCategory.GAMES) matchedGameTokens++
                    if (category == DefaultCategory.BANKING || category == DefaultCategory.UPI_PAYMENTS) matchedBankingTokens++
                }
            }
            if (matchCount > 0) {
                boost(category, 1.2f + (matchCount - 1) * 0.65f)
            }
        }

        // Strict Mutual Exclusivity between Games and Banking/UPI
        if (matchedGameTokens > 0) {
            scores[DefaultCategory.BANKING.ordinal] = -10f
            scores[DefaultCategory.UPI_PAYMENTS.ordinal] = -10f
        } else if (matchedBankingTokens > 0) {
            scores[DefaultCategory.GAMES.ordinal] = -10f
        }

        if (isSystemApp) {
            // Only default to System Apps if no strong specialized user-facing category matched
            val maxSpecialized = (1..15).maxOf { scores[it] }
            if (maxSpecialized < 0.75f || pkg.startsWith("com.android.system") || pkg == "com.android.settings" || pkg == "com.android.vending") {
                boost(DefaultCategory.SYSTEM_APPS, 1.35f)
            } else {
                boost(DefaultCategory.SYSTEM_APPS, 0.35f)
            }
        }
    }

    /**
     * AI Semantic App Matcher for user-defined custom smart folders (e.g. "Battlefield games", "Browsers", "UPI apps", "Reading apps", "Dating apps").
     *
     * Evaluates installed apps against user intent using multi-token semantic overlap, known package directory,
     * OS category hints, and domain taxonomy tags.
     */
    fun matchAppsForCustomIntent(prompt: String, installedApps: List<com.example.domain.model.AppInfo>): List<com.example.domain.model.AppInfo> {
        val cleanPrompt = prompt.trim().lowercase()
        if (cleanPrompt.isEmpty()) return emptyList()

        val promptTokens = cleanPrompt.split(" ", "_", "-", "/")
            .map { it.trim() }
            .filter { it.length >= 2 && it !in STOP_TOKENS }

        // Specialized domain query recognizers
        val isGameIntent = promptTokens.any { it in setOf("game", "games", "gaming", "battlefield", "shooter", "fps", "pubg", "action", "rpg", "arcade", "war") }
        val isBankingIntent = promptTokens.any { it in setOf("bank", "banking", "finance", "money", "loan", "stocks", "invest", "trading") }
        val isUpiIntent = promptTokens.any { it in setOf("upi", "pay", "payments", "wallet", "paisa", "gpay", "phonepe", "paytm", "bhim", "cred") }
        val isBrowserIntent = promptTokens.any { it in setOf("browser", "browsers", "web", "internet", "chrome", "surf") }
        val isDatingIntent = promptTokens.any { it in setOf("dating", "date", "match", "tinder", "bumble", "hinge", "love", "matrimony", "shaadi") }
        val isReadingIntent = promptTokens.any { it in setOf("reading", "read", "book", "books", "novel", "novels", "comic", "comics", "manga", "kindle", "ebook", "ebooks") }
        val isSocialIntent = promptTokens.any { it in setOf("social", "media", "community", "chat", "network") }
        val isVideoIntent = promptTokens.any { it in setOf("video", "movies", "movie", "stream", "streaming", "shows", "cinema", "tv") }
        val isMusicIntent = promptTokens.any { it in setOf("music", "songs", "audio", "podcast", "podcasts") }
        val isShoppingIntent = promptTokens.any { it in setOf("shopping", "shop", "store", "buy", "deals", "food", "grocery") }

        return installedApps.filter { app ->
            val pkg = app.packageName.lowercase()
            val name = app.appName.lowercase()
            val tokens = extractCleanTokens(app.packageName, app.appName)

            // Direct intent matchers
            if (isGameIntent) {
                if (app.categoryId == DefaultCategory.GAMES.id ||
                    knownPackages[pkg] == "Games" ||
                    tokens.any { it in setOf("pubg", "bgmi", "freefire", "cod", "callofduty", "warzone", "clash", "brawl", "game", "games", "minecraft", "roblox", "asphalt", "ea", "battlefield") }
                ) {
                    return@filter true
                }
            }

            if (isBankingIntent) {
                if (app.categoryId == DefaultCategory.BANKING.id ||
                    knownPackages[pkg] == "Banking" ||
                    tokens.any { it in setOf("bank", "banking", "sbi", "hdfc", "icici", "axis", "kotak", "yono", "chase", "revolut", "zerodha", "groww") }
                ) {
                    return@filter true
                }
            }

            if (isUpiIntent) {
                if (app.categoryId == DefaultCategory.UPI_PAYMENTS.id ||
                    knownPackages[pkg] == "UPI & Payments" ||
                    tokens.any { it in setOf("upi", "paytm", "phonepe", "paisa", "gpay", "bhim", "cred", "slice", "wallet", "paypal") }
                ) {
                    return@filter true
                }
            }

            if (isBrowserIntent) {
                if (app.categoryId == DefaultCategory.BROWSERS.id ||
                    knownPackages[pkg] == "Browsers" ||
                    tokens.any { it in setOf("browser", "chrome", "firefox", "brave", "opera", "edge", "vivaldi", "duckduckgo") }
                ) {
                    return@filter true
                }
            }

            if (isDatingIntent) {
                if (tokens.any { it in setOf("tinder", "bumble", "hinge", "okcupid", "grindr", "badoo", "shaadi", "jeevansathi", "dating", "matrimony") }) {
                    return@filter true
                }
            }

            if (isReadingIntent) {
                if (tokens.any { it in setOf("kindle", "books", "wattpad", "audible", "goodreads", "webtoon", "moonreader", "reader", "novel", "manga", "comic") }) {
                    return@filter true
                }
            }

            if (isSocialIntent) {
                if (app.categoryId == DefaultCategory.SOCIAL.id || app.categoryId == DefaultCategory.MESSAGING.id ||
                    knownPackages[pkg] == "Social" || knownPackages[pkg] == "Messaging"
                ) {
                    return@filter true
                }
            }

            if (isVideoIntent) {
                if (app.categoryId == DefaultCategory.VIDEO_STREAMING.id || knownPackages[pkg] == "Video & Streaming") {
                    return@filter true
                }
            }

            if (isMusicIntent) {
                if (app.categoryId == DefaultCategory.MUSIC_AUDIO.id || knownPackages[pkg] == "Music & Audio") {
                    return@filter true
                }
            }

            if (isShoppingIntent) {
                if (app.categoryId == DefaultCategory.SHOPPING.id || knownPackages[pkg] == "Shopping") {
                    return@filter true
                }
            }

            // General token intersection scoring
            var score = 0
            for (pTok in promptTokens) {
                if (name.contains(pTok) || pkg.contains(pTok)) score += 3
                if (tokens.contains(pTok)) score += 2
                if (app.categoryName.lowercase().contains(pTok)) score += 2
            }
            score >= 2
        }
    }
}
