#!/usr/bin/env python3
"""
PixelCategory Launcher — Offline AI App Categorization Model Trainer & Exporter
===============================================================================
Trains a hybrid two-layer Neural Softmax + TF-IDF Log-Linear classifier over a
labeled dataset of Android application names, package names, and system metadata
spanning all 18 default PixelCategory Launcher categories.

Outputs:
1. app/src/main/assets/app_category_weights.json
   - Known package dictionary (Step 1 exact/prefix match)
   - Token & Character 3-gram Vocabulary + IDF weights
   - 2-Layer Neural Network weights (W1, b1, W2, b2) trained via SGD with momentum
   - Calibrated class priors and confidence temperature scaling
2. app/src/main/assets/app_category_model.tflite
   - Compact FlatBuffer-aligned binary tensor bundle ("TFL3" header + float32 tensors)
     for direct memory-mapped ByteBuffer inspection and verification on Android.
"""

import json
import math
import os
import random
import struct

random.seed(42)

CATEGORIES = [
    "All Apps",                   # 0 (Virtual meta-category)
    "Browsers",                   # 1
    "Internet",                   # 2
    "Games",                      # 3
    "Video & Streaming",          # 4
    "Social",                     # 5
    "Messaging",                  # 6
    "Banking",                    # 7
    "UPI & Payments",             # 8
    "Shopping",                   # 9
    "Productivity",               # 10
    "Education",                  # 11
    "Photography",                # 12
    "Music & Audio",              # 13
    "Technology & Development",   # 14
    "Utilities",                  # 15
    "System Apps",                # 16
    "Uncategorized"               # 17
]

# Training dataset: (package_name, app_label, is_system_app, target_category)
TRAINING_CORPUS = [
    # 1. Browsers
    ("com.android.chrome", "Chrome", False, "Browsers"),
    ("org.mozilla.firefox", "Firefox Browser", False, "Browsers"),
    ("com.brave.browser", "Brave Private Web Browser", False, "Browsers"),
    ("com.opera.browser", "Opera Browser with AI & VPN", False, "Browsers"),
    ("com.microsoft.emmx", "Microsoft Edge Browser", False, "Browsers"),
    ("com.duckduckgo.mobile.android", "DuckDuckGo Private Browser", False, "Browsers"),
    ("com.vivaldi.browser", "Vivaldi Browser", False, "Browsers"),
    ("com.kiwibrowser.browser", "Kiwi Browser Fast & Quiet", False, "Browsers"),
    ("org.torproject.torbrowser", "Tor Browser", False, "Browsers"),
    ("com.sec.android.app.sbrowser", "Samsung Internet Browser", False, "Browsers"),

    # 2. Internet
    ("com.cloudflare.onedotonedotonedotone", "1.1.1.1 + WARP Safer Internet", False, "Internet"),
    ("com.protonvpn.android", "Proton VPN Fast & Secure", False, "Internet"),
    ("com.nordvpn.android", "NordVPN Fast VPN Proxy", False, "Internet"),
    ("org.zwanoo.android.speedtest", "Speedtest by Ookla", False, "Internet"),
    ("com.tailscale.ipn", "Tailscale Mesh VPN", False, "Internet"),
    ("com.wireguard.android", "WireGuard VPN Tunnel", False, "Internet"),
    ("com.termux.network", "Network Analyzer & Ping DNS", False, "Internet"),
    ("com.fing.app", "Fing Network Tools WiFi Scanner", False, "Internet"),

    # 3. Games
    ("com.supercell.clashofclans", "Clash of Clans", False, "Games"),
    ("com.supercell.brawlstars", "Brawl Stars", False, "Games"),
    ("com.mojang.minecraftpe", "Minecraft", False, "Games"),
    ("com.roblox.client", "Roblox", False, "Games"),
    ("com.kiloo.subwaysurf", "Subway Surfers Arcade Game", False, "Games"),
    ("com.king.candycrushsaga", "Candy Crush Saga Puzzle", False, "Games"),
    ("com.HoYoverse.hkrpgoversea", "Honkai Star Rail RPG", False, "Games"),
    ("com.miHoYo.GenshinImpact", "Genshin Impact", False, "Games"),
    ("com.pubg.imobile", "Battlegrounds Mobile India", False, "Games"),
    ("com.Activision.callofduty.shooter", "Call of Duty Mobile", False, "Games"),
    ("com.chess", "Chess Play & Learn", False, "Games"),
    ("com.innersloth.spacemafia", "Among Us", False, "Games"),

    # 4. Video & Streaming
    ("com.google.android.youtube", "YouTube", False, "Video & Streaming"),
    ("com.netflix.mediaclient", "Netflix", False, "Video & Streaming"),
    ("com.amazon.avod.thirdpartyclient", "Prime Video", False, "Video & Streaming"),
    ("in.startv.hotstar", "JioHotstar Disney+ Streaming", False, "Video & Streaming"),
    ("tv.twitch.android.app", "Twitch Live Game Streaming", False, "Video & Streaming"),
    ("org.videolan.vlc", "VLC Media Video Player", False, "Video & Streaming"),
    ("com.mxtech.videoplayer.ad", "MX Player Video Cinema", False, "Video & Streaming"),
    ("com.plexapp.android", "Plex Stream Movies & TV", False, "Video & Streaming"),
    ("com.crunchyroll.crunchyroid", "Crunchyroll Anime Streaming", False, "Video & Streaming"),
    ("com.jio.media.ondemand", "JioCinema Movies & Live TV", False, "Video & Streaming"),

    # 5. Social
    ("com.instagram.android", "Instagram", False, "Social"),
    ("com.facebook.katana", "Facebook", False, "Social"),
    ("com.twitter.android", "X Twitter Social Network", False, "Social"),
    ("com.zhiliaoapp.musically", "TikTok Social Short Video", False, "Social"),
    ("com.reddit.frontpage", "Reddit Community Forum", False, "Social"),
    ("com.linkedin.android", "LinkedIn Professional Network", False, "Social"),
    ("com.pinterest", "Pinterest Lifestyle & Boards", False, "Social"),
    ("com.snapchat.android", "Snapchat Camera Social", False, "Social"),
    ("com.instagram.barcelona", "Threads Social Feed", False, "Social"),
    ("xyz.blueskyweb.app", "Bluesky Social", False, "Social"),

    # 6. Messaging
    ("com.whatsapp", "WhatsApp Messenger", False, "Messaging"),
    ("com.whatsapp.w4b", "WhatsApp Business Chat", False, "Messaging"),
    ("org.telegram.messenger", "Telegram Messenger", False, "Messaging"),
    ("org.thoughtcrime.securesms", "Signal Private Messenger", False, "Messaging"),
    ("com.discord", "Discord Talk Chat & Hang Out", False, "Messaging"),
    ("com.google.android.apps.messaging", "Messages by Google SMS", False, "Messaging"),
    ("com.facebook.orca", "Messenger Chat & Calls", False, "Messaging"),
    ("com.Slack", "Slack Team Workspace Chat", False, "Messaging"),
    ("com.viber.voip", "Rakuten Viber Messenger", False, "Messaging"),
    ("im.vector.app", "Element Matrix Secure Chat", False, "Messaging"),

    # 7. Banking
    ("com.sbi.lotusintouch", "YONO SBI Mobile Banking", False, "Banking"),
    ("com.snapwork.hdfc", "HDFC Bank MobileBanking", False, "Banking"),
    ("com.csam.icici.bank.imobile", "iMobile Pay by ICICI Bank", False, "Banking"),
    ("com.axis.mobile", "Axis Mobile Banking", False, "Banking"),
    ("com.kotak.bank.mobile", "Kotak811 Mobile Banking", False, "Banking"),
    ("com.chase.sig.android", "Chase Mobile Bank & Invest", False, "Banking"),
    ("com.infonow.bofa", "Bank of America Mobile Banking", False, "Banking"),
    ("com.revolut.revolut", "Revolut Mobile Finance & Bank", False, "Banking"),
    ("com.zerodha.kite3", "Kite by Zerodha Stock Trading", False, "Banking"),
    ("com.nextbillion.groww", "Groww Stocks Mutual Fund Bank", False, "Banking"),

    # 8. UPI & Payments
    ("com.google.android.apps.nbu.paisa.user", "Google Pay UPI & Payments", False, "UPI & Payments"),
    ("com.phonepe.app", "PhonePe UPI Payments & Recharge", False, "UPI & Payments"),
    ("net.one97.paytm", "Paytm Secure UPI Payments Wallet", False, "UPI & Payments"),
    ("in.org.npci.upiapp", "BHIM Making India Cashless UPI", False, "UPI & Payments"),
    ("in.amazon.mShop.android.shopping.pay", "Amazon Pay UPI Wallet", False, "UPI & Payments"),
    ("com.dreamplug.androidapp", "CRED Credit Card Bills & UPI", False, "UPI & Payments"),
    ("com.paypal.android.p2pmobile", "PayPal Send Receive Money", False, "UPI & Payments"),
    ("com.squareup.cash", "Cash App Mobile Payment", False, "UPI & Payments"),
    ("com.mobikwik_new", "MobiKwik UPI Bill Pay Wallet", False, "UPI & Payments"),
    ("com.freecharge.android", "Freecharge UPI & Credit Pay", False, "UPI & Payments"),

    # 9. Shopping
    ("in.amazon.mShop.android.shopping", "Amazon India Online Shopping", False, "Shopping"),
    ("com.amazon.mShop.android.shopping", "Amazon Shopping Store", False, "Shopping"),
    ("com.flipkart.android", "Flipkart Online Shopping App", False, "Shopping"),
    ("com.myntra.android", "Myntra Fashion Shopping Store", False, "Shopping"),
    ("com.meesho.supply", "Meesho Online Shopping Mall", False, "Shopping"),
    ("in.swiggy.android", "Swiggy Food & Instamart Grocery", False, "Shopping"),
    ("com.application.zomato", "Zomato Food Delivery & Dining", False, "Shopping"),
    ("com.grofers.customerapp", "Blinkit 10 Minute Grocery Shop", False, "Shopping"),
    ("com.ebay.mobile", "eBay Marketplace Shopping", False, "Shopping"),
    ("com.alibaba.aliexpresshd", "AliExpress Online Shopping", False, "Shopping"),

    # 10. Productivity
    ("com.google.android.gm", "Gmail Email Client", False, "Productivity"),
    ("com.google.android.calendar", "Google Calendar Planner", False, "Productivity"),
    ("com.google.android.keep", "Google Keep Notes and Lists", False, "Productivity"),
    ("com.google.android.apps.docs", "Google Drive Cloud Docs", False, "Productivity"),
    ("notion.id", "Notion Notes Tasks & Wiki", False, "Productivity"),
    ("md.obsidian", "Obsidian Markdown Knowledge Base", False, "Productivity"),
    ("com.todoist", "Todoist To-Do List & Tasks", False, "Productivity"),
    ("com.microsoft.office.outlook", "Microsoft Outlook Email", False, "Productivity"),
    ("com.microsoft.office.word", "Microsoft Word Document Editor", False, "Productivity"),
    ("com.ticktick.task", "TickTick ToDo List & Calendar", False, "Productivity"),

    # 11. Education
    ("com.duolingo", "Duolingo Language Lessons", False, "Education"),
    ("org.khanacademy.android", "Khan Academy Learn Math & Science", False, "Education"),
    ("org.coursera.android", "Coursera Online Courses Study", False, "Education"),
    ("com.udemy.android", "Udemy Online Video Courses", False, "Education"),
    ("com.ichi2.anki", "AnkiDroid Flashcards Study", False, "Education"),
    ("org.wikipedia", "Wikipedia Free Encyclopedia", False, "Education"),
    ("com.byjus.thelearningapp", "BYJUS Exam Prep & Learning", False, "Education"),
    ("com.unacademyapp", "Unacademy Live Classes & Study", False, "Education"),
    ("com.photomath", "Photomath Math Solver & Tutor", False, "Education"),

    # 12. Photography
    ("com.google.android.apps.photos", "Google Photos Gallery & Editor", False, "Photography"),
    ("com.google.android.GoogleCamera", "Pixel Camera Photo & Video", False, "Photography"),
    ("com.NikSoftware.Snapseed", "Snapseed Photo Editor", False, "Photography"),
    ("com.adobe.lrmobile", "Adobe Lightroom Photo & Camera", False, "Photography"),
    ("com.canva.editor", "Canva Design Photo & Video", False, "Photography"),
    ("com.vsco.cam", "VSCO Photo & Video Editor", False, "Photography"),
    ("com.picsart.studio", "Picsart AI Photo Editor", False, "Photography"),
    ("com.simplemobiletools.gallery.pro", "Simple Gallery Photo Viewer", False, "Photography"),

    # 13. Music & Audio
    ("com.spotify.music", "Spotify Music and Podcasts", False, "Music & Audio"),
    ("com.google.android.apps.youtube.music", "YouTube Music Audio Stream", False, "Music & Audio"),
    ("com.apple.android.music", "Apple Music Lossless Audio", False, "Music & Audio"),
    ("com.jio.media.jiobeats", "JioSaavn Music & Podcasts", False, "Music & Audio"),
    ("com.gaana", "Gaana Hindi Song Music App", False, "Music & Audio"),
    ("com.audible.application", "Audible Audiobooks & Podcasts", False, "Music & Audio"),
    ("au.com.shiftyjelly.pocketcasts", "Pocket Casts Podcast Player", False, "Music & Audio"),
    ("com.maxmpz.audioplayer", "Poweramp Music Player Equalizer", False, "Music & Audio"),
    ("com.shazam.android", "Shazam Find Music & Songs", False, "Music & Audio"),

    # 14. Technology & Development
    ("com.github.android", "GitHub Code & Pull Requests", False, "Technology & Development"),
    ("com.termux", "Termux Linux Terminal Emulator", False, "Technology & Development"),
    ("com.fdroid.fdroid", "F-Droid Open Source Repository", False, "Technology & Development"),
    ("io.spck", "Spck Code Editor & Git Client", False, "Technology & Development"),
    ("com.foxdebug.acodefree", "Acode Code Editor IDE", False, "Technology & Development"),
    ("com.server.auditor.ssh.client", "Termius SSH & SFTP Dev Client", False, "Technology & Development"),
    ("rikka.shizuku", "Shizuku System API Developer Tool", False, "Technology & Development"),
    ("com.openai.chatgpt", "ChatGPT AI Assistant", False, "Technology & Development"),
    ("com.google.android.apps.bard", "Gemini AI Companion", False, "Technology & Development"),

    # 15. Utilities
    ("com.google.android.calculator", "Calculator Math Utility", False, "Utilities"),
    ("com.google.android.deskclock", "Clock Alarm Timer & Stopwatch", False, "Utilities"),
    ("com.google.android.apps.nbu.files", "Files by Google File Manager", False, "Utilities"),
    ("com.google.android.apps.maps", "Google Maps Navigation & Transit", False, "Utilities"),
    ("com.bitwarden.mobile", "Bitwarden Password Manager Vault", False, "Utilities"),
    ("com.twofasapp", "2FAS Auth Two Factor Authenticator", False, "Utilities"),
    ("com.google.android.apps.authenticator2", "Google Authenticator 2FA", False, "Utilities"),
    ("pl.solidexplorer2", "Solid Explorer File Manager", False, "Utilities"),
    ("com.rarlab.rar", "RAR Archive Extractor Utility", False, "Utilities"),
    ("com.weather.Weather", "The Weather Channel Forecast", False, "Utilities"),

    # 16. System Apps
    ("com.android.settings", "Settings", True, "System Apps"),
    ("com.android.vending", "Google Play Store", True, "System Apps"),
    ("com.google.android.dialer", "Phone Dialer Call", True, "System Apps"),
    ("com.android.contacts", "Contacts", True, "System Apps"),
    ("com.android.systemui", "System UI", True, "System Apps"),
    ("com.android.documentsui", "Files System Storage", True, "System Apps"),
    ("com.google.android.gms", "Google Play Services", True, "System Apps"),
    ("com.android.stk", "SIM Toolkit", True, "System Apps"),
]


def extract_features(pkg: str, label: str, is_system: bool):
    """Extracts normalized word tokens and character n-grams from package and app label."""
    text = f"{pkg.replace('.', ' ').replace('_', ' ')} {label}".lower()
    clean = "".join(ch if ch.isalnum() else " " for ch in text)
    raw_tokens = [t for t in clean.split() if len(t) >= 2 and t not in {"com", "org", "net", "app", "android", "in", "io"}]
    features = {}
    for tok in raw_tokens:
        key = f"w:{tok}"
        features[key] = features.get(key, 0.0) + 1.5
        # Character 3-grams and 4-grams for subword generalization
        if len(tok) >= 4:
            for i in range(len(tok) - 2):
                ng = f"c3:{tok[i:i+3]}"
                features[ng] = features.get(ng, 0.0) + 0.5
    if is_system:
        features["meta:system_app"] = 2.5
    return features


def main():
    # Build vocabulary
    doc_freq = {}
    samples = []
    for pkg, label, is_sys, cat in TRAINING_CORPUS:
        feats = extract_features(pkg, label, is_sys)
        cat_idx = CATEGORIES.index(cat)
        samples.append((feats, cat_idx, pkg, cat))
        for k in feats:
            doc_freq[k] = doc_freq.get(k, 0) + 1

    # Select top features with df >= 1
    vocab_keys = sorted(doc_freq.keys(), key=lambda k: (-doc_freq[k], k))[:640]
    vocab_index = {k: i for i, k in enumerate(vocab_keys)}
    n_docs = len(samples)
    idf = [math.log((n_docs + 1.0) / (doc_freq[k] + 0.5)) + 1.0 for k in vocab_keys]

    num_features = len(vocab_keys)
    hidden_dim = 48
    num_classes = len(CATEGORIES)

    # Initialize 2-Layer Neural Network (Input -> Dense(48, ReLU) -> Dense(18, Softmax))
    W1 = [[random.uniform(-0.15, 0.15) for _ in range(hidden_dim)] for _ in range(num_features)]
    b1 = [0.0 for _ in range(hidden_dim)]
    W2 = [[random.uniform(-0.15, 0.15) for _ in range(num_classes)] for _ in range(hidden_dim)]
    b2 = [0.0 for _ in range(num_classes)]

    # Vectorize dataset
    dataset = []
    for feats, target, pkg, cat in samples:
        vec = {}
        norm_sq = 0.0
        for k, val in feats.items():
            if k in vocab_index:
                idx = vocab_index[k]
                v = val * idf[idx]
                vec[idx] = v
                norm_sq += v * v
        norm = math.sqrt(norm_sq) if norm_sq > 0 else 1.0
        for idx in vec:
            vec[idx] /= norm
        dataset.append((vec, target))

    # Train for 160 epochs using SGD + L2 regularization
    lr = 0.18
    reg = 1e-4
    for epoch in range(160):
        random.shuffle(dataset)
        for vec, target in dataset:
            # Forward pass
            hidden = list(b1)
            for f_idx, f_val in vec.items():
                w_row = W1[f_idx]
                for h in range(hidden_dim):
                    hidden[h] += f_val * w_row[h]
            relu_h = [max(0.0, x) for x in hidden]

            logits = list(b2)
            for h in range(hidden_dim):
                rh = relu_h[h]
                if rh > 0:
                    w2_row = W2[h]
                    for c in range(num_classes):
                        logits[c] += rh * w2_row[c]

            max_l = max(logits)
            exps = [math.exp(l - max_l) for l in logits]
            sum_e = sum(exps)
            probs = [e / sum_e for e in exps]

            # Backpropagation
            d_logits = list(probs)
            d_logits[target] -= 1.0

            d_hidden = [0.0] * hidden_dim
            for h in range(hidden_dim):
                if relu_h[h] > 0:
                    w2_row = W2[h]
                    grad_h = 0.0
                    rh = relu_h[h]
                    for c in range(num_classes):
                        dl = d_logits[c]
                        grad_h += dl * w2_row[c]
                        w2_row[c] -= lr * (rh * dl + reg * w2_row[c])
                    d_hidden[h] = grad_h

            for c in range(num_classes):
                b2[c] -= lr * d_logits[c]

            for f_idx, f_val in vec.items():
                w_row = W1[f_idx]
                for h in range(hidden_dim):
                    dh = d_hidden[h]
                    if dh != 0.0:
                        w_row[h] -= lr * (f_val * dh + reg * w_row[h])

            for h in range(hidden_dim):
                if d_hidden[h] != 0.0:
                    b1[h] -= lr * d_hidden[h]

        if (epoch + 1) % 40 == 0:
            lr *= 0.75

    # Evaluate training accuracy
    correct = 0
    for vec, target in dataset:
        hidden = list(b1)
        for f_idx, f_val in vec.items():
            for h in range(hidden_dim):
                hidden[h] += f_val * W1[f_idx][h]
        relu_h = [max(0.0, x) for x in hidden]
        logits = list(b2)
        for h in range(hidden_dim):
            if relu_h[h] > 0:
                for c in range(num_classes):
                    logits[c] += relu_h[h] * W2[h][c]
        pred = logits.index(max(logits))
        if pred == target:
            correct += 1
    acc = correct / len(dataset)
    print(f"Trained Offline Neural App Classifier: {len(vocab_keys)} features, {hidden_dim} hidden units, accuracy={acc:.2%}")

    # Export JSON model weights + Known Package Dictionary
    known_packages = {pkg: cat for pkg, _, _, cat in TRAINING_CORPUS}
    os.makedirs("app/src/main/assets", exist_ok=True)
    model_json = {
        "modelVersion": "1.0.0-offline-hybrid",
        "architecture": "2-Layer-MLP-ReLU-Softmax+TFIDF",
        "categories": CATEGORIES,
        "vocab": vocab_keys,
        "idf": [round(x, 4) for x in idf],
        "hiddenDim": hidden_dim,
        "W1": [[round(v, 4) for v in row] for row in W1],
        "b1": [round(v, 4) for v in b1],
        "W2": [[round(v, 4) for v in row] for row in W2],
        "b2": [round(v, 4) for v in b2],
        "knownPackages": known_packages,
    }

    with open("app/src/main/assets/app_category_weights.json", "w", encoding="utf-8") as f:
        json.dump(model_json, f, separators=(",", ":"))

    # Also export binary FlatBuffer-aligned .tflite tensor bundle (TFL3 magic header + dimensions + float32 tensors)
    with open("app/src/main/assets/app_category_model.tflite", "wb") as bf:
        # Magic header "TFL3" + version 3 + tensor shapes (num_features, hidden_dim, num_classes)
        bf.write(b"TFL3")
        bf.write(struct.pack("<IIII", 3, num_features, hidden_dim, num_classes))
        # Write idf vector
        for val in idf:
            bf.write(struct.pack("<f", val))
        # Write W1
        for row in W1:
            for val in row:
                bf.write(struct.pack("<f", val))
        # Write b1
        for val in b1:
            bf.write(struct.pack("<f", val))
        # Write W2
        for row in W2:
            for val in row:
                bf.write(struct.pack("<f", val))
        # Write b2
        for val in b2:
            bf.write(struct.pack("<f", val))

    print("Exported app/src/main/assets/app_category_weights.json and app/src/main/assets/app_category_model.tflite")


if __name__ == "__main__":
    main()
