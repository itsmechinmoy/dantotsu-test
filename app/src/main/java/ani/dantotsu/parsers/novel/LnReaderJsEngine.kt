package ani.dantotsu.parsers.novel

import android.content.Context
import app.cash.quickjs.QuickJs
import ani.dantotsu.App
import ani.dantotsu.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object LnReaderJsEngine {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36"

    suspend fun call(
        pluginJs: String,
        pluginId: String,
        method: String,
        argsJson: String = "[]",
    ): String = withContext(Dispatchers.IO) {
        val qjs = QuickJs.create()
        try {
            // Jsoup elements cache
            val jsoupElements = mutableMapOf<Int, org.jsoup.nodes.Element>()
            var elementCounter = 0

            qjs.set("__dantotsuJsoup", JsoupBridge::class.java, object : JsoupBridge {
                override fun parse(html: String): Int {
                    val doc = org.jsoup.Jsoup.parse(html)
                    val id = ++elementCounter
                    jsoupElements[id] = doc
                    return id
                }
                override fun select(nodeIdsJson: String, selector: String): String {
                    val ids = runCatching { Json.decodeFromString<List<Int>>(nodeIdsJson) }.getOrDefault(emptyList())
                    val resultIds = mutableListOf<Int>()
                    for (id in ids) {
                        val node = jsoupElements[id] ?: continue
                        try {
                            val elements = node.select(selector)
                            for (el in elements) {
                                val newId = ++elementCounter
                                jsoupElements[newId] = el
                                resultIds.add(newId)
                            }
                        } catch (e: Exception) {
                            Logger.log("Jsoup select error for '$selector': ${e.message}")
                        }
                    }
                    return Json.encodeToString(resultIds)
                }
                override fun text(nodeIdsJson: String): String {
                    val ids = runCatching { Json.decodeFromString<List<Int>>(nodeIdsJson) }.getOrDefault(emptyList())
                    return ids.mapNotNull { jsoupElements[it]?.text() }.joinToString("")
                }
                override fun attr(nodeIdsJson: String, attr: String): String {
                    val ids = runCatching { Json.decodeFromString<List<Int>>(nodeIdsJson) }.getOrDefault(emptyList())
                    return jsoupElements[ids.firstOrNull()]?.attr(attr) ?: ""
                }
                override fun html(nodeIdsJson: String): String {
                    val ids = runCatching { Json.decodeFromString<List<Int>>(nodeIdsJson) }.getOrDefault(emptyList())
                    return ids.mapNotNull { jsoupElements[it]?.html() }.joinToString("\n")
                }
                override fun outerHtml(nodeIdsJson: String): String {
                    val ids = runCatching { Json.decodeFromString<List<Int>>(nodeIdsJson) }.getOrDefault(emptyList())
                    return ids.mapNotNull { jsoupElements[it]?.outerHtml() }.joinToString("\n")
                }
                override fun remove(nodeIdsJson: String) {
                    val ids = runCatching { Json.decodeFromString<List<Int>>(nodeIdsJson) }.getOrDefault(emptyList())
                    ids.forEach { jsoupElements[it]?.remove() }
                }
                override fun removeAttr(nodeIdsJson: String, attr: String) {
                    val ids = runCatching { Json.decodeFromString<List<Int>>(nodeIdsJson) }.getOrDefault(emptyList())
                    ids.forEach { jsoupElements[it]?.removeAttr(attr) }
                }
                override fun setAttr(nodeIdsJson: String, attr: String, value: String) {
                    val ids = runCatching { Json.decodeFromString<List<Int>>(nodeIdsJson) }.getOrDefault(emptyList())
                    ids.forEach { jsoupElements[it]?.attr(attr, value) }
                }
                override fun next(nodeIdsJson: String): String {
                    val ids = runCatching { Json.decodeFromString<List<Int>>(nodeIdsJson) }.getOrDefault(emptyList())
                    val resultIds = mutableListOf<Int>()
                    for (id in ids) {
                        jsoupElements[id]?.nextElementSibling()?.let {
                            val newId = ++elementCounter
                            jsoupElements[newId] = it
                            resultIds.add(newId)
                        }
                    }
                    return Json.encodeToString(resultIds)
                }
                override fun prev(nodeIdsJson: String): String {
                    val ids = runCatching { Json.decodeFromString<List<Int>>(nodeIdsJson) }.getOrDefault(emptyList())
                    val resultIds = mutableListOf<Int>()
                    for (id in ids) {
                        jsoupElements[id]?.previousElementSibling()?.let {
                            val newId = ++elementCounter
                            jsoupElements[newId] = it
                            resultIds.add(newId)
                        }
                    }
                    return Json.encodeToString(resultIds)
                }
                override fun parent(nodeIdsJson: String): String {
                    val ids = runCatching { Json.decodeFromString<List<Int>>(nodeIdsJson) }.getOrDefault(emptyList())
                    val resultIds = mutableListOf<Int>()
                    for (id in ids) {
                        jsoupElements[id]?.parent()?.let {
                            val newId = ++elementCounter
                            jsoupElements[newId] = it
                            resultIds.add(newId)
                        }
                    }
                    return Json.encodeToString(resultIds)
                }
                override fun children(nodeIdsJson: String, selector: String): String {
                    val ids = runCatching { Json.decodeFromString<List<Int>>(nodeIdsJson) }.getOrDefault(emptyList())
                    val resultIds = mutableListOf<Int>()
                    for (id in ids) {
                        val node = jsoupElements[id] ?: continue
                        val ch = if (selector.isNotBlank()) node.children().select(selector) else node.children()
                        for (el in ch) {
                            val newId = ++elementCounter
                            jsoupElements[newId] = el
                            resultIds.add(newId)
                        }
                    }
                    return Json.encodeToString(resultIds)
                }
                override fun siblings(nodeIdsJson: String, selector: String): String {
                    val ids = runCatching { Json.decodeFromString<List<Int>>(nodeIdsJson) }.getOrDefault(emptyList())
                    val resultIds = mutableListOf<Int>()
                    for (id in ids) {
                        val node = jsoupElements[id] ?: continue
                        val sibs = if (selector.isNotBlank()) node.siblingElements().select(selector) else node.siblingElements()
                        for (el in sibs) {
                            val newId = ++elementCounter
                            jsoupElements[newId] = el
                            resultIds.add(newId)
                        }
                    }
                    return Json.encodeToString(resultIds)
                }
                override fun hasClass(nodeIdsJson: String, className: String): Boolean {
                    val ids = runCatching { Json.decodeFromString<List<Int>>(nodeIdsJson) }.getOrDefault(emptyList())
                    return ids.any { jsoupElements[it]?.hasClass(className) == true }
                }
                override fun attrs(nodeIdsJson: String): String {
                    val ids = runCatching { Json.decodeFromString<List<Int>>(nodeIdsJson) }.getOrDefault(emptyList())
                    val element = jsoupElements[ids.firstOrNull()] ?: return "{}"
                    val map = element.attributes().associate { it.key to it.value }
                    return Json.encodeToString(map)
                }
                override fun tagName(nodeIdsJson: String): String {
                    val ids = runCatching { Json.decodeFromString<List<Int>>(nodeIdsJson) }.getOrDefault(emptyList())
                    return jsoupElements[ids.firstOrNull()]?.tagName() ?: ""
                }
            })

            qjs.set("__dantotsuFetch", FetchBridge::class.java, object : FetchBridge {
                override fun fetch(url: String, method: String, headersJson: String, body: String?): String {
                    return executeFetch(url, method, headersJson, body)
                }
            })

            qjs.set("__dantotsuLog", LogBridge::class.java, object : LogBridge {
                override fun log(msg: String) {
                    Logger.log("LnReaderJS[$pluginId]: $msg")
                }
            })

            qjs.set("__dantotsuStorage", StorageBridge::class.java, object : StorageBridge {
                private val prefs = App.currentContext()?.getSharedPreferences("lnreader_plugin_storage", Context.MODE_PRIVATE)

                override fun get(key: String): String? {
                    return prefs?.getString(key, null)
                }

                override fun set(key: String, value: String) {
                    prefs?.edit()?.putString(key, value)?.apply()
                }

                override fun delete(key: String) {
                    prefs?.edit()?.remove(key)?.apply()
                }

                override fun getAllKeys(prefix: String): String {
                    val keys = prefs?.all?.keys?.filter { it.startsWith(prefix) } ?: emptyList()
                    return Json.encodeToString(keys)
                }
            })

            qjs.evaluate(POLYFILL_JS)
            qjs.evaluate(SYNC_PROMISE_POLYFILL_JS)
            qjs.evaluate(STRING_HELPERS_JS)
            qjs.evaluate(HTMLPARSER_JS)
            qjs.evaluate(CHEERIO_JSOUP_BRIDGE_JS)
            qjs.evaluate(FETCH_BRIDGE_JS)
            qjs.evaluate(MODULE_BOOTSTRAP_JS)
            qjs.evaluate(REQUIRE_SHIM_JS)

            // Load plugin
            qjs.evaluate("""
                (function() {
                    $pluginJs
                    globalThis['__plugin_$pluginId'] = exports.default ?? exports;
                })();
            """.trimIndent())

            val callJs = """
                (function() {
                    try {
                        var target = globalThis['__plugin_$pluginId'];
                        if (!target) throw new Error("Plugin '$pluginId' not loaded");
                        var fn = target["$method"];
                        if (typeof fn !== "function") throw new Error("Method '$method' not found on plugin");
                        var args = JSON.parse('${argsJson.replace("'", "\\'")}');
                        var result = fn.apply(target, args);

                        if (result && typeof result.then === "function") {
                            var isSettled = false;
                            var settledVal = undefined;
                            var settledErr = undefined;

                            result.then(function(v) {
                                settledVal = v;
                                isSettled = true;
                            }, function(e) {
                                settledErr = e;
                                isSettled = true;
                            });

                            if (!isSettled && result.state) {
                                if (result.state === 'fulfilled') {
                                    settledVal = result.value;
                                    isSettled = true;
                                } else if (result.state === 'rejected') {
                                    settledErr = result.reason;
                                    isSettled = true;
                                }
                            }

                            if (isSettled) {
                                if (settledErr !== undefined) {
                                    throw (settledErr instanceof Error ? settledErr : new Error(String(settledErr)));
                                }
                                return JSON.stringify(settledVal !== undefined ? settledVal : null);
                            }

                            if (result.state === 'fulfilled') {
                                return JSON.stringify(result.value !== undefined ? result.value : null);
                            } else if (result.state === 'rejected') {
                                throw result.reason instanceof Error ? result.reason : new Error(String(result.reason));
                            }
                            return JSON.stringify(null);
                        }
                        return JSON.stringify(result !== undefined ? result : null);
                    } catch(e) {
                        throw e;
                    }
                })();
            """.trimIndent()
            val raw = qjs.evaluate(callJs)
            raw?.toString() ?: "null"

        } catch (e: Exception) {
            Logger.log("LnReaderJsEngine.call error [$method]: ${e.message}")
            throw e
        } finally {
            qjs.close()
        }
    }

    private fun executeFetch(
        url: String,
        method: String,
        headersJson: String,
        body: String?,
    ): String {
        return try {
            val parsedHeaders = try {
                val obj = Json.parseToJsonElement(headersJson).jsonObject
                obj.entries.associate { it.key to it.value.jsonPrimitive.content }
            } catch (_: Exception) { emptyMap() }

            val reqBuilder = Request.Builder().url(url)

            val defaultHeaders = mapOf(
                "User-Agent" to DEFAULT_USER_AGENT,
                "Connection" to "keep-alive",
                "Accept" to "*/*",
                "Accept-Language" to "*",
                "Cache-Control" to "max-age=0",
            )
            defaultHeaders.forEach { (k, v) ->
                if (!parsedHeaders.keys.any { it.equals(k, ignoreCase = true) }) {
                    reqBuilder.addHeader(k, v)
                }
            }
            parsedHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

            when (method.uppercase()) {
                "POST" -> {
                    val ct = parsedHeaders.entries
                        .firstOrNull { it.key.equals("content-type", ignoreCase = true) }?.value
                        ?: "application/x-www-form-urlencoded"
                    val mediaType = ct.toMediaTypeOrNull()
                    reqBuilder.post((body ?: "").toRequestBody(mediaType))
                }
                "PUT" -> {
                    val ct = parsedHeaders.entries
                        .firstOrNull { it.key.equals("content-type", ignoreCase = true) }?.value
                        ?: "application/json"
                    reqBuilder.put((body ?: "").toRequestBody(ct.toMediaTypeOrNull()))
                }
                "HEAD" -> reqBuilder.head()
                else   -> reqBuilder.get()
            }

            val builtReq = reqBuilder.build()
            val response = httpClient.newCall(builtReq).execute()
            val responseBody = response.body.string()
            val finalUrl = response.request.url.toString()
            val responseHeaders = response.headers.toMultimap()
                .entries.associate { it.key to it.value.firstOrNull().orEmpty() }

            Json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                buildJsonObject {
                    put("statusCode", response.code)
                    put("reasonPhrase", response.message)
                    put("body", responseBody)
                    put("url", finalUrl)
                    put("isRedirect", false)
                    put("headers", buildJsonObject {
                        responseHeaders.forEach { (k, v) -> put(k, v) }
                    })
                }
            )
        } catch (e: Exception) {
            Logger.log("LnReaderJsEngine fetch error ($url): ${e.message}")
            Json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                buildJsonObject {
                    put("statusCode", 0)
                    put("reasonPhrase", e.message ?: "Unknown error")
                    put("body", "")
                    put("url", url)
                    put("isRedirect", false)
                    put("headers", buildJsonObject {})
                }
            )
        }
    }

    interface FetchBridge {
        fun fetch(url: String, method: String, headersJson: String, body: String?): String
    }

    interface LogBridge {
        fun log(msg: String)
    }

    interface StorageBridge {
        fun get(key: String): String?
        fun set(key: String, value: String)
        fun delete(key: String)
        fun getAllKeys(prefix: String): String
    }

    interface JsoupBridge {
        fun parse(html: String): Int
        fun select(nodeIdsJson: String, selector: String): String
        fun text(nodeIdsJson: String): String
        fun attr(nodeIdsJson: String, attr: String): String
        fun html(nodeIdsJson: String): String
        fun outerHtml(nodeIdsJson: String): String
        fun remove(nodeIdsJson: String)
        fun removeAttr(nodeIdsJson: String, attr: String)
        fun setAttr(nodeIdsJson: String, attr: String, value: String)
        fun next(nodeIdsJson: String): String
        fun prev(nodeIdsJson: String): String
        fun parent(nodeIdsJson: String): String
        fun children(nodeIdsJson: String, selector: String): String
        fun siblings(nodeIdsJson: String, selector: String): String
        fun hasClass(nodeIdsJson: String, className: String): Boolean
        fun attrs(nodeIdsJson: String): String
        fun tagName(nodeIdsJson: String): String
    }

    private val MODULE_BOOTSTRAP_JS = """
var module = {};
var exports = (function() { return this; })();
Object.defineProperties(module, {
    namespace: { set: function(a) { exports = a; } },
    exports: {
        set: function(a) { for (var b in a) { if (a.hasOwnProperty(b)) exports[b] = a[b]; } },
        get: function() { return exports; }
    }
});
""".trimIndent()

    private val REQUIRE_SHIM_JS = """
const dayjs = function(v) { return { format: function(f) { return String(v || new Date()); }, valueOf: function() { return +new Date(v); } }; };
dayjs.extend = function(){};

const NovelStatus = {
    "Unknown":"Unknown","Ongoing":"Ongoing","Completed":"Completed",
    "Licensed":"Licensed","PublishingFinished":"Publishing Finished",
    "Cancelled":"Cancelled","OnHiatus":"On Hiatus"
};

const FilterTypes = {
    "TextInput":"Text","Picker":"Picker","CheckboxGroup":"Checkbox",
    "Switch":"Switch","ExcludableCheckboxGroup":"XCheckbox"
};

const isPickerValue      = q => q.type === FilterTypes.Picker && typeof q.value === "string";
const isCheckboxValue    = q => q.type === FilterTypes.CheckboxGroup && Array.isArray(q.value);
const isSwitchValue      = q => q.type === FilterTypes.Switch && typeof q.value === "boolean";
const isTextValue        = q => q.type === FilterTypes.TextInput && typeof q.value === "string";
const isXCheckboxValue   = q => q.type === FilterTypes.ExcludableCheckboxGroup && typeof q.value === "object" && !Array.isArray(q.value);

const isUrlAbsolute = url => {
    if (!url) return false;
    if (url.indexOf("//") === 0) return true;
    if (url.indexOf("://") === -1) return false;
    if (url.indexOf(".") === -1) return false;
    if (url.indexOf("/") === -1) return false;
    if (url.indexOf(":") > url.indexOf("/")) return false;
    if (url.indexOf("://") < url.indexOf(".")) return true;
    return false;
};

const defaultCover = '';

const utf8ToBytes = function(str) {
    if (!str) return new Uint8Array(0);
    var bytes = [];
    for (var i = 0; i < str.length; i++) {
        var charCode = str.charCodeAt(i);
        if (charCode < 0x80) bytes.push(charCode);
        else if (charCode < 0x800) {
            bytes.push(0xc0 | (charCode >> 6), 0x80 | (charCode & 0x3f));
        } else if (charCode < 0xd800 || charCode >= 0xe000) {
            bytes.push(0xe0 | (charCode >> 12), 0x80 | ((charCode >> 6) & 0x3f), 0x80 | (charCode & 0x3f));
        } else {
            i++;
            charCode = 0x10000 + (((charCode & 0x3ff) << 10) | (str.charCodeAt(i) & 0x3ff));
            bytes.push(0xf0 | (charCode >> 18), 0x80 | ((charCode >> 12) & 0x3f), 0x80 | ((charCode >> 6) & 0x3f), 0x80 | (charCode & 0x3f));
        }
    }
    return new Uint8Array(bytes);
};

const bytesToUtf8 = function(bytes) {
    if (!bytes) return '';
    var encoded = [];
    for (var i = 0; i < bytes.length; i++) {
        encoded.push('%' + ('0' + bytes[i].toString(16)).slice(-2));
    }
    try {
        return decodeURIComponent(encoded.join(''));
    } catch(e) {
        var str = '';
        for (var j = 0; j < bytes.length; j++) str += String.fromCharCode(bytes[j]);
        return str;
    }
};

function PluginStorage(pluginId) {
    this.pluginId = pluginId || '';
}
PluginStorage.prototype.get = function(key) {
    var raw = __dantotsuStorage.get(this.pluginId + "_DB_" + key);
    if (!raw) return undefined;
    try {
        var item = JSON.parse(raw);
        if (item.expires && Date.now() > item.expires) {
            this.delete(key);
            return undefined;
        }
        return item.value !== undefined ? item.value : item;
    } catch (e) {
        return raw;
    }
};
PluginStorage.prototype.set = function(key, value, expires) {
    var exp = (expires instanceof Date) ? expires.getTime() : expires;
    var item = {
        created: new Date().toISOString(),
        value: value,
        expires: exp
    };
    __dantotsuStorage.set(this.pluginId + "_DB_" + key, JSON.stringify(item));
};
PluginStorage.prototype.delete = function(key) {
    __dantotsuStorage.delete(this.pluginId + "_DB_" + key);
};
PluginStorage.prototype.clearAll = function() {
    var keysStr = __dantotsuStorage.getAllKeys(this.pluginId + "_DB_");
    try {
        var keys = JSON.parse(keysStr);
        for (var i = 0; i < keys.length; i++) {
            __dantotsuStorage.delete(keys[i]);
        }
    } catch(e) {}
};
PluginStorage.prototype.getAllKeys = function() {
    var keysStr = __dantotsuStorage.getAllKeys(this.pluginId + "_DB_");
    try {
        var keys = JSON.parse(keysStr);
        var prefix = this.pluginId + "_DB_";
        return keys.map(function(k) { return k.replace(prefix, ''); });
    } catch(e) {
        return [];
    }
};

const gcm = function(key, nonce) {
    return {
        encrypt: function(plaintext) { return plaintext; },
        decrypt: function(ciphertext) { return ciphertext; }
    };
};

const require = (pkg) => {
    switch (pkg) {
        case "cheerio":           return { load: load };
        case "htmlparser2":       return { Parser: Parser };
        case "dayjs":             return dayjs;
        case "urlencode":         return { encode: encodeURIComponent, decode: decodeURIComponent };
        case "@libs/fetch":       return { fetchApi: fetchApi, fetchText: fetchText, fetchProto: fetchApi };
        case "@libs/novelStatus": return { NovelStatus: NovelStatus };
        case "@libs/isAbsoluteUrl": return { isUrlAbsolute: isUrlAbsolute };
        case "@libs/filterInputs": return { FilterTypes, isPickerValue, isCheckboxValue, isSwitchValue, isTextValue, isXCheckboxValue };
        case "@libs/defaultCover": return { defaultCover: defaultCover };
        case "@libs/aes":         return { gcm: gcm };
        case "@libs/utils":       return { utf8ToBytes: utf8ToBytes, bytesToUtf8: bytesToUtf8 };
        case "@libs/storage":     return {
            storage: new PluginStorage(''),
            localStorage: new PluginStorage('local'),
            sessionStorage: new PluginStorage('session')
        };
        case "lodash-es/reverse": return function(arr) { return arr ? arr.slice().reverse() : []; };
        case "lodash-es/uniqBy":  return function(arr, key) {
            if (!arr) return [];
            var seen = new Set();
            return arr.filter(function(item) {
                var k = typeof key === 'function' ? key(item) : item[key];
                if (seen.has(k)) return false;
                seen.add(k);
                return true;
            });
        };
        default:                  return {};
    }
};
""".trimIndent()

    private val POLYFILL_JS = """
var console = {
    log: function() { try { __dantotsuLog.log(Array.prototype.slice.call(arguments).map(function(a) { return typeof a === 'object' ? JSON.stringify(a) : String(a); }).join(' ')); } catch(e){} },
    warn: function() { try { __dantotsuLog.log('WARN: ' + Array.prototype.slice.call(arguments).map(function(a) { return typeof a === 'object' ? JSON.stringify(a) : String(a); }).join(' ')); } catch(e){} },
    error: function() { try { __dantotsuLog.log('ERROR: ' + Array.prototype.slice.call(arguments).map(function(a) { return typeof a === 'object' ? JSON.stringify(a) : String(a); }).join(' ')); } catch(e){} }
};

var setTimeout = function(fn, ms) { try { if (typeof fn === 'function') fn(); } catch(e){} return 0; };
var clearTimeout = function() {};
var setInterval = function(fn, ms) { return 0; };
var clearInterval = function() {};

var btoa = function(str) {
    var chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
    var encoded = '';
    var c1, c2, c3, e1, e2, e3, e4;
    var i = 0;
    while (i < str.length) {
        c1 = str.charCodeAt(i++);
        c2 = str.charCodeAt(i++);
        c3 = str.charCodeAt(i++);
        e1 = c1 >> 2;
        e2 = ((c1 & 3) << 4) | (c2 >> 4);
        e3 = ((c2 & 15) << 2) | (c3 >> 6);
        e4 = c3 & 63;
        if (isNaN(c2)) e3 = e4 = 64;
        else if (isNaN(c3)) e4 = 64;
        encoded += chars.charAt(e1) + chars.charAt(e2) + chars.charAt(e3) + chars.charAt(e4);
    }
    return encoded;
};

var atob = function(input) {
    var chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
    var str = String(input).replace(/=+$/, '');
    var output = '';
    if (str.length % 4 === 1) return '';
    for (var bc = 0, bs, buffer, idx = 0; buffer = str.charAt(idx++); ~buffer && (bs = bc % 4 ? bs * 64 + buffer : buffer, bc++ % 4) ? output += String.fromCharCode(255 & bs >> (-2 * bc & 6)) : 0) {
        buffer = chars.indexOf(buffer);
    }
    return output;
};

if (typeof Object.assign !== 'function') {
    Object.assign = function(target) {
        if (target == null) throw new TypeError('Cannot convert undefined or null to object');
        var to = Object(target);
        for (var i = 1; i < arguments.length; i++) {
            var src = arguments[i];
            if (src != null) {
                for (var key in src) {
                    if (Object.prototype.hasOwnProperty.call(src, key)) {
                        to[key] = src[key];
                    }
                }
            }
        }
        return to;
    };
}

// URLSearchParams polyfill
function URLSearchParams(init) {
    this._params = {};
    if (typeof init === "string") {
        init.replace(/^\?/,"").split("&").forEach(function(p) {
            var kv = p.split("=");
            if (kv[0]) this._params[decodeURIComponent(kv[0])] = decodeURIComponent(kv[1] || "");
        }.bind(this));
    } else if (init && typeof init === "object") {
        for (var key in init) {
            if (init.hasOwnProperty(key) && init[key] !== undefined && init[key] !== null) {
                this._params[key] = String(init[key]);
            }
        }
    }
}
URLSearchParams.prototype.append = function(k,v){ this._params[k]=String(v); };
URLSearchParams.prototype.get = function(k){ return this._params.hasOwnProperty(k) ? this._params[k] : null; };
URLSearchParams.prototype.set = function(k,v){ this._params[k]=String(v); };
URLSearchParams.prototype.delete = function(k){ delete this._params[k]; };
URLSearchParams.prototype.has = function(k){ return this._params.hasOwnProperty(k); };
URLSearchParams.prototype.toString = function(){
    return Object.keys(this._params).map(function(k) {
        return encodeURIComponent(k)+"="+encodeURIComponent(this._params[k]);
    }.bind(this)).join("&");
};

// FormData polyfill with URL-encoded serialization
function FormData() { this._data = []; }
FormData.prototype.append = function(k,v){ this._data.push([k,v]); };
FormData.prototype.toString = function(){ 
    return this._data.map(function(kv) { return encodeURIComponent(kv[0]) + "=" + encodeURIComponent(kv[1]); }).join("&"); 
};

// URL minimal polyfill
function URL(href, base) {
    if (base && !href.match(/^https?:\/\//)) href = base.replace(/\/+$/,"") + "/" + href.replace(/^\//,"");
    var m = href.match(/^(https?:)\/\/([^/?#]*)(.*?)(\?[^#]*)?(#.*)?$/);
    if (!m) { this.href=href; this.pathname=""; this.search=""; this.hash=""; this.host=""; this.protocol=""; return; }
    this.protocol = m[1]; this.host = m[2]; this.pathname = m[3]||"/";
    this.search = m[4]||""; this.hash = m[5]||"";
    this.href = href;
    this.searchParams = new URLSearchParams(this.search);
}
""".trimIndent()

    private val STRING_HELPERS_JS = """
String.prototype.substringAfter = function(p){ var i=this.indexOf(p); return i===-1?this:this.substring(i+p.length); };
String.prototype.substringAfterLast = function(p){ return this.split(p).pop(); };
String.prototype.substringBefore = function(p){ var i=this.indexOf(p); return i===-1?this:this.substring(0,i); };
String.prototype.substringBeforeLast = function(p){ var i=this.lastIndexOf(p); return i===-1?this:this.substring(0,i); };
String.prototype.substringBetween = function(l,r){
    var i=this.indexOf(l); if(i===-1) return "";
    var li=i+l.length; var ri=this.indexOf(r,li); if(ri===-1) return "";
    return this.substring(li,ri);
};
""".trimIndent()

    private val FETCH_BRIDGE_JS = """
var fetchApi = function(url, init) {
    var method = (init && init.method) ? init.method.toUpperCase() : "GET";
    var headers = (init && init.headers) ? JSON.parse(JSON.stringify(init.headers)) : {};
    var body = null;
    
    if (init && init.body) {
        if (init.body instanceof FormData) {
            body = init.body.toString();
            var hasContentType = Object.keys(headers).some(function(k) { return k.toLowerCase() === 'content-type'; });
            if (!hasContentType) headers['content-type'] = 'application/x-www-form-urlencoded';
        } else if (typeof init.body === "string") {
            body = init.body;
        } else {
            body = JSON.stringify(init.body);
        }
    }
    
    if (init && init.referrer) {
        headers['Referer'] = init.referrer;
    }
    
    var headersJson = JSON.stringify(headers);
    var resultStr = __dantotsuFetch.fetch(url, method, headersJson, body);
    var result = JSON.parse(resultStr);
    
    var responseHeaders = result.headers || {};
    responseHeaders.get = function(key) {
        if (!key) return null;
        var lk = key.toLowerCase();
        for (var k in result.headers) {
            if (k.toLowerCase() === lk) return result.headers[k];
        }
        return null;
    };
    
    return Promise.resolve({
        status: result.statusCode,
        statusText: result.reasonPhrase,
        ok: result.statusCode >= 200 && result.statusCode < 300,
        url: result.url || url,
        headers: responseHeaders,
        text: function() { return Promise.resolve(result.body); },
        json: function() {
            try {
                return Promise.resolve(JSON.parse(result.body));
            } catch(e) {
                return Promise.resolve({});
            }
        },
        body: result.body
    });
};

var fetchText = function(url, init) {
    return fetchApi(url, init).then(function(res) { return res.text(); });
};
""".trimIndent()

    private val HTMLPARSER_JS = """
var VOID_ELEMENTS = {area:1,base:1,br:1,col:1,embed:1,hr:1,img:1,input:1,link:1,meta:1,param:1,source:1,track:1,wbr:1};
var RAW_TAGS = {script:1,style:1};

function Parser(opts) {
    this.opts = opts || {};
    this._buf = "";
}
Parser.prototype.write = function(html) {
    this._buf += html;
};
Parser.prototype.isVoidElement = function(tag) {
    return !!VOID_ELEMENTS[tag];
};
Parser.prototype.end = function() {
    var buf = this._buf, i = 0, len = buf.length;
    while (i < len) {
        if (buf[i] === '<') {
            if (buf[i+1] === '!' && buf[i+2] === '-' && buf[i+3] === '-') {
                var ce = buf.indexOf('-->', i + 4);
                i = ce === -1 ? len : ce + 3;
                continue;
            }
            if (buf[i+1] === '!' || buf[i+1] === '?') {
                var de = buf.indexOf('>', i + 2);
                i = de === -1 ? len : de + 1;
                continue;
            }
            var closing = buf[i+1] === '/';
            if (closing) i++;
            var j = i+1;
            while (j < len && !/[\s>\/]/.test(buf[j])) j++;
            var tag = buf.slice(i+1, j).toLowerCase();
            i = j;
            var attrs = {};
            var selfClosing = false;
            while (i < len && buf[i] !== '>') {
                while (i < len && /\s/.test(buf[i])) i++;
                if (buf[i] === '/') { selfClosing = true; i++; continue; }
                if (buf[i] === '>') break;
                var ks = i;
                while (i < len && !/[\s=>\/]/.test(buf[i])) i++;
                var key = buf.slice(ks, i).toLowerCase();
                while (i < len && /\s/.test(buf[i])) i++;
                var val = "";
                if (buf[i] === '=') {
                    i++;
                    while (i < len && /\s/.test(buf[i])) i++;
                    if (buf[i] === '"' || buf[i] === "'") {
                        var q = buf[i++], vs = i;
                        while (i < len && buf[i] !== q) i++;
                        val = buf.slice(vs, i);
                        if (i < len) i++;
                    } else {
                        var vs2 = i;
                        while (i < len && !/[\s>\/]/.test(buf[i])) i++;
                        val = buf.slice(vs2, i);
                    }
                }
                if (key) attrs[key] = val;
            }
            if (buf[i] === '>') i++;
            if (closing) {
                if (this.opts.onclosetag) this.opts.onclosetag(tag);
            } else {
                if (this.opts.onopentagname) this.opts.onopentagname(tag);
                if (this.opts.onopentag) this.opts.onopentag(tag, attrs);
                if (selfClosing || VOID_ELEMENTS[tag]) {
                    if (this.opts.onclosetag) this.opts.onclosetag(tag);
                } else if (RAW_TAGS[tag]) {
                    var closeTag = '</' + tag;
                    var ri = buf.toLowerCase().indexOf(closeTag, i);
                    if (ri !== -1) {
                        var rawText = buf.slice(i, ri);
                        if (rawText && this.opts.ontext) this.opts.ontext(rawText);
                        i = ri;
                    }
                }
            }
        } else {
            var ts = i;
            while (i < len && buf[i] !== '<') i++;
            var text = buf.slice(ts, i);
            if (text && this.opts.ontext) this.opts.ontext(text);
        }
    }
    if (this.opts.onend) this.opts.onend();
};
""".trimIndent()

    private val CHEERIO_JSOUP_BRIDGE_JS = """
function load(html) {
    if (typeof html !== 'string') html = String(html || "");
    var rootId = __dantotsuJsoup.parse(html);

    function wrap(nodeIds) {
        var obj = {
            _nodes: nodeIds,
            length: nodeIds.length,
            nodeType: 1,
            text: function() { return __dantotsuJsoup.text(JSON.stringify(this._nodes)); },
            attr: function(a, v) { 
                if (this._nodes.length === 0) return undefined;
                if (v !== undefined) {
                    __dantotsuJsoup.setAttr(JSON.stringify(this._nodes), a, String(v));
                    return this;
                }
                return __dantotsuJsoup.attr(JSON.stringify(this._nodes), a) || undefined; 
            },
            removeAttr: function(a) {
                __dantotsuJsoup.removeAttr(JSON.stringify(this._nodes), a);
                return this;
            },
            val: function() {
                return this.attr('value') || this.text();
            },
            data: function(k) {
                return this.attr('data-' + k);
            },
            contents: function() {
                return this.children();
            },
            addBack: function() {
                return this;
            },
            filter: function(fn) {
                if (typeof fn === 'function') {
                    var res = [];
                    for(var i=0; i<this._nodes.length; i++) {
                        var el = wrap([this._nodes[i]]);
                        if (fn.call(el, i, el)) res.push(this._nodes[i]);
                    }
                    return wrap(res);
                }
                return this;
            },
            replaceWith: function(html) {
                return this;
            },
            is: function(sel) {
                if (typeof sel === 'string' && sel) {
                    if (sel.startsWith('.')) return this.hasClass(sel.slice(1));
                    return this.tagName.toLowerCase() === sel.toLowerCase();
                }
                return true;
            },
            not: function(sel) {
                return this;
            },
            clone: function() {
                return wrap(this._nodes.slice());
            },
            empty: function() {
                return this;
            },
            siblings: function(sel) {
                var resStr = __dantotsuJsoup.siblings(JSON.stringify(this._nodes), sel || "");
                return wrap(JSON.parse(resStr));
            },
            html: function() { return __dantotsuJsoup.html(JSON.stringify(this._nodes)); },
            outerHtml: function() { return __dantotsuJsoup.outerHtml(JSON.stringify(this._nodes)); },
            remove: function() { __dantotsuJsoup.remove(JSON.stringify(this._nodes)); return this; },
            find: function(sel) {
                var resStr = __dantotsuJsoup.select(JSON.stringify(this._nodes), sel);
                return wrap(JSON.parse(resStr));
            },
            children: function(sel) {
                var resStr = __dantotsuJsoup.children(JSON.stringify(this._nodes), sel || "");
                return wrap(JSON.parse(resStr));
            },
            parent: function() {
                var resStr = __dantotsuJsoup.parent(JSON.stringify(this._nodes));
                return wrap(JSON.parse(resStr));
            },
            hasClass: function(className) {
                return __dantotsuJsoup.hasClass(JSON.stringify(this._nodes), className);
            },
            each: function(fn) {
                for (var i = 0; i < this._nodes.length; i++) {
                    var el = wrap([this._nodes[i]]);
                    if (fn.call(el, i, el) === false) break;
                }
                return this;
            },
            first: function() { return wrap(this._nodes.length > 0 ? [this._nodes[0]] : []); },
            last: function() { return wrap(this._nodes.length > 0 ? [this._nodes[this._nodes.length - 1]] : []); },
            eq: function(i) { return wrap(i >= 0 && i < this._nodes.length ? [this._nodes[i]] : []); },
            get: function(i) {
                 if (i === undefined) return this._nodes.map(function(id) { return wrap([id]); });
                 return wrap([this._nodes[i]]);
            },
            toArray: function() {
                 return this._nodes.map(function(id) { return wrap([id]); });
            },
            map: function(fn) {
                 var res = [];
                 for(var i=0; i<this._nodes.length; i++) {
                     var el = wrap([this._nodes[i]]);
                     var v = fn.call(el, i, el);
                     if (v !== null && v !== undefined) res.push(v);
                 }
                 var wrapObj = wrap([]);
                 wrapObj.get = function() { return res; };
                 wrapObj.join = function(sep) { return res.join(sep); };
                 return wrapObj;
            },
            next: function() {
                 var resStr = __dantotsuJsoup.next(JSON.stringify(this._nodes));
                 return wrap(JSON.parse(resStr));
            },
            prev: function() {
                 var resStr = __dantotsuJsoup.prev(JSON.stringify(this._nodes));
                 return wrap(JSON.parse(resStr));
            },
            trim: function() { return this.text().trim(); },
            toString: function() { return this.text(); }
        };
        Object.defineProperty(obj, 'attribs', {
            get: function() {
                 var map = {};
                 if (nodeIds.length > 0) {
                     try {
                         var str = __dantotsuJsoup.attrs(JSON.stringify(nodeIds));
                         map = JSON.parse(str) || {};
                     } catch(e){}
                 }
                 if (!map.class) map.class = '';
                 return map;
            },
            set: function(v) {},
            enumerable: true,
            configurable: true
        });
        Object.defineProperty(obj, 'tagName', {
            get: function() {
                 if (nodeIds.length === 0) return "";
                 return __dantotsuJsoup.tagName(JSON.stringify(nodeIds));
            },
            enumerable: true
        });
        Object.defineProperty(obj, 'type', { value: 'tag', enumerable: true });
        for(var i=0; i<nodeIds.length; i++) {
            obj[i] = wrap([nodeIds[i]]);
        }
        return obj;
    }

    var $ = function(sel, context) {
        if (typeof sel === 'object' && sel !== null && sel._nodes) return sel;
        if (typeof sel !== 'string') return wrap([]);
        if (context) {
             if (typeof context === 'object' && context._nodes) {
                  var resStr = __dantotsuJsoup.select(JSON.stringify(context._nodes), sel);
                  return wrap(JSON.parse(resStr));
             }
        }
        var resStr = __dantotsuJsoup.select(JSON.stringify([rootId]), sel);
        return wrap(JSON.parse(resStr));
    };

    $.html = function() { return __dantotsuJsoup.outerHtml(JSON.stringify([rootId])); };
    $.root = function() { return wrap([rootId]); };
    return $;
}
""".trimIndent()

    private val SYNC_PROMISE_POLYFILL_JS = """
function Promise(executor) {
    this.state = 'pending';
    this.value = undefined;
    this.reason = undefined;
    this.onFulfilled = [];
    this.onRejected = [];
    var self = this;
    
    function resolve(value) {
        if (value instanceof Promise) {
            value.then(resolve, reject);
            return;
        }
        if (self.state === 'pending') {
            self.state = 'fulfilled';
            self.value = value;
            self.onFulfilled.forEach(function(fn) { fn(value); });
        }
    }
    
    function reject(reason) {
        if (self.state === 'pending') {
            self.state = 'rejected';
            self.reason = reason;
            self.onRejected.forEach(function(fn) { fn(reason); });
        }
    }
    
    try {
        executor(resolve, reject);
    } catch (e) {
        reject(e);
    }
}

Promise.prototype.then = function(onFulfilled, onRejected) {
    var self = this;
    return new Promise(function(resolve, reject) {
        function handle(callback, val) {
            try {
                if (typeof callback === 'function') {
                    var result = callback(val);
                    resolve(result);
                } else {
                    if (self.state === 'fulfilled') resolve(val);
                    else reject(val);
                }
            } catch (e) {
                reject(e);
            }
        }
        
        if (self.state === 'fulfilled') {
            handle(onFulfilled, self.value);
        } else if (self.state === 'rejected') {
            handle(onRejected, self.reason);
        } else {
            self.onFulfilled.push(function(val) { handle(onFulfilled, val); });
            self.onRejected.push(function(reason) { handle(onRejected, reason); });
        }
    });
};

Promise.prototype.catch = function(onRejected) {
    return this.then(null, onRejected);
};

Promise.prototype.finally = function(onFinally) {
    return this.then(
        function(value) { return Promise.resolve(onFinally()).then(function() { return value; }); },
        function(reason) { return Promise.resolve(onFinally()).then(function() { throw reason; }); }
    );
};

Promise.resolve = function(value) {
    if (value instanceof Promise) return value;
    return new Promise(function(resolve) { resolve(value); });
};

Promise.reject = function(reason) {
    return new Promise(function(resolve, reject) { reject(reason); });
};

Promise.all = function(promises) {
    return new Promise(function(resolve, reject) {
        if (!promises || promises.length === 0) return resolve([]);
        var results = new Array(promises.length);
        var completed = 0;
        promises.forEach(function(p, i) {
            Promise.resolve(p).then(function(val) {
                results[i] = val;
                completed++;
                if (completed === promises.length) resolve(results);
            }).catch(reject);
        });
    });
};

Promise.allSettled = function(promises) {
    return Promise.all(promises.map(function(p) {
        return Promise.resolve(p).then(
            function(val) { return { status: "fulfilled", value: val }; },
            function(err) { return { status: "rejected", reason: err }; }
        );
    }));
};
""".trimIndent()

}
