package ani.dantotsu.parsers.novel
import android.content.Context
import app.cash.quickjs.QuickJs
import ani.dantotsu.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Headers.Companion.toHeaders
import java.util.concurrent.TimeUnit

object LnReaderJsEngine {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun call(
        pluginJs: String,
        pluginId: String,
        method: String,
        argsJson: String = "[]",
    ): String = withContext(Dispatchers.IO) {
        val qjs = QuickJs.create()
        try {
            qjs.set("__dantotsuFetch", FetchBridge::class.java, object : FetchBridge {
                override fun fetch(url: String, method: String, headersJson: String, body: String?): String {
                    return executeFetch(url, method, headersJson, body)
                }
            })

            qjs.evaluate(POLYFILL_JS)
            qjs.evaluate(STRING_HELPERS_JS)
            qjs.evaluate(FETCH_BRIDGE_JS)
            qjs.evaluate(HTMLPARSER_JS)
            qjs.evaluate(CHEERIO_SHIM_JS)
            qjs.evaluate(MODULE_BOOTSTRAP_JS)
            qjs.evaluate(REQUIRE_SHIM_JS)
            qjs.evaluate("""
                (function() {
                    $pluginJs
                    globalThis['__plugin_$pluginId'] = exports.default ?? exports;
                })();
            """.trimIndent())

            val callJs = """
                (function() {
                    var target = globalThis['__plugin_$pluginId'];
                    if (!target) throw new Error("Plugin '$pluginId' not loaded");
                    var fn = target["$method"];
                    if (typeof fn !== "function") throw new Error("Method '$method' not found on plugin");
                    var args = JSON.parse('${argsJson.replace("'", "\\'")}');
                    var result = fn.apply(target, args);
                    if (result && typeof result.then === "function") {
                        throw new Error("__ASYNC_NOT_SUPPORTED__");
                    }
                    return JSON.stringify(result);
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
            parsedHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

            when (method.uppercase()) {
                "POST" -> {
                    val mediaType = okhttp3.MediaType.parse(
                        parsedHeaders["content-type"] ?: "application/json; charset=utf-8"
                    )
                    reqBuilder.post(okhttp3.RequestBody.create(mediaType, body ?: ""))
                }
                "HEAD" -> reqBuilder.head()
                else   -> reqBuilder.get()
            }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            val responseBody = response.body()?.string() ?: ""
            val responseHeaders = response.headers().toMultimap()
                .entries.associate { it.key to it.value.firstOrNull().orEmpty() }

            Json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                kotlinx.serialization.json.buildJsonObject {
                    put("statusCode", kotlinx.serialization.json.JsonPrimitive(response.code()))
                    put("reasonPhrase", kotlinx.serialization.json.JsonPrimitive(response.message()))
                    put("body", kotlinx.serialization.json.JsonPrimitive(responseBody))
                    put("isRedirect", kotlinx.serialization.json.JsonPrimitive(false))
                    put("headers", Json.parseToJsonElement(
                        json.encodeToString(
                            kotlinx.serialization.json.MapSerializer(
                                kotlinx.serialization.json.serializer(),
                                kotlinx.serialization.json.serializer()
                            ),
                            responseHeaders
                        )
                    ))
                }
            )
        } catch (e: Exception) {
            Logger.log("LnReaderJsEngine fetch error: ${e.message}")
            """{"statusCode":0,"reasonPhrase":"${e.message}","body":"","isRedirect":false,"headers":{}}"""
        }
    }

    interface FetchBridge {
        fun fetch(url: String, method: String, headersJson: String, body: String?): String
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
const dayjs = function(v) { return { format: function() { return String(v || new Date()); }, valueOf: function() { return +new Date(v); } }; };
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

const defaultCover = 'https://raw.githubusercontent.com/LNReader/lnreader-plugins/refs/heads/master/public/static/coverNotAvailable.webp';

const require = (pkg) => {
    switch (pkg) {
        case "cheerio":           return { load: load };
        case "htmlparser2":       return { Parser: Parser };
        case "dayjs":             return dayjs;
        case "urlencode":         return { encode: encodeURIComponent, decode: decodeURIComponent };
        case "@libs/fetch":       return { fetchApi: fetchApi };
        case "@libs/novelStatus": return { NovelStatus: NovelStatus };
        case "@libs/isAbsoluteUrl": return { isUrlAbsolute: isUrlAbsolute };
        case "@libs/filterInputs": return { FilterTypes, isPickerValue, isCheckboxValue, isSwitchValue, isTextValue, isXCheckboxValue };
        case "@libs/defaultCover": return { defaultCover: defaultCover };
        case "@libs/storage":     return { storage: { get: () => null, set: () => {}, delete: () => {} } };
        default:                  return {};
    }
};
""".trimIndent()

    private val POLYFILL_JS = """
var console = { log: function(){}, warn: function(){}, error: function(){} };

// URLSearchParams minimal polyfill
function URLSearchParams(init) {
    this._params = {};
    if (typeof init === "string") {
        init.replace(/^\?/,"").split("&").forEach(function(p) {
            var kv = p.split("=");
            if (kv[0]) this._params[decodeURIComponent(kv[0])] = decodeURIComponent(kv[1] || "");
        }.bind(this));
    }
}
URLSearchParams.prototype.append = function(k,v){ this._params[k]=v; };
URLSearchParams.prototype.get = function(k){ return this._params[k]||null; };
URLSearchParams.prototype.set = function(k,v){ this._params[k]=v; };
URLSearchParams.prototype.toString = function(){
    return Object.keys(this._params).map(k=>encodeURIComponent(k)+"="+encodeURIComponent(this._params[k])).join("&");
};

// FormData minimal polyfill
function FormData() { this._data = []; }
FormData.prototype.append = function(k,v){ this._data.push([k,v]); };
FormData.prototype.toJSON = function(){ return { _data: this._data }; };

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
    var headers = (init && init.headers) ? JSON.stringify(init.headers) : "{}";
    var body = (init && init.body) ? (typeof init.body === "string" ? init.body : JSON.stringify(init.body)) : null;
    var resultStr = __dantotsuFetch.fetch(url, method, headers, body);
    var result = JSON.parse(resultStr);
    return {
        status: result.statusCode,
        statusText: result.reasonPhrase,
        ok: result.statusCode >= 200 && result.statusCode < 300,
        headers: result.headers || {},
        text: function() { return Promise.resolve(result.body); },
        json: function() { return Promise.resolve(JSON.parse(result.body)); },
        body: result.body
    };
};
""".trimIndent()

    private val HTMLPARSER_JS = """
function Parser(opts) {
    this.opts = opts || {};
    this._buf = "";
}
Parser.prototype.write = function(html) {
    this._buf += html;
};
Parser.prototype.end = function() {
    var buf = this._buf, i = 0, len = buf.length;
    while (i < len) {
        if (buf[i] === '<') {
            var closing = buf[i+1] === '/';
            if (closing) i++;
            var j = i+1;
            while (j < len && !/[\s>\/]/.test(buf[j])) j++;
            var tag = buf.slice(i+1, j).toLowerCase();
            i = j;
            var attrs = {};
            while (i < len && buf[i] !== '>') {
                while (i < len && /\s/.test(buf[i])) i++;
                if (buf[i] === '>' || buf[i] === '/') break;
                var ks = i;
                while (i < len && !/[\s=>\/ ]/.test(buf[i])) i++;
                var key = buf.slice(ks, i);
                while (i < len && /\s/.test(buf[i])) i++;
                var val = null;
                if (buf[i] === '=') {
                    i++;
                    while (i < len && /\s/.test(buf[i])) i++;
                    if (buf[i] === '"' || buf[i] === "'") {
                        var q = buf[i++], vs = i;
                        while (i < len && buf[i] !== q) i++;
                        val = buf.slice(vs, i++);
                    } else {
                        var vs = i;
                        while (i < len && !/[\s>]/.test(buf[i])) i++;
                        val = buf.slice(vs, i);
                    }
                }
                if (key) attrs[key] = val;
            }
            if (buf[i] === '>') i++;
            if (closing) { if (this.opts.onclosetag) this.opts.onclosetag(tag); }
            else { if (this.opts.onopentagname) this.opts.onopentagname(tag); if (this.opts.onopentag) this.opts.onopentag(tag, attrs); }
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

    private val CHEERIO_SHIM_JS = """
function load(html) {
    // Build a simple node tree from html string
    var nodes = [];
    var stack = [{ tag: "root", children: nodes, attrs: {} }];
    var voids = ["area","base","br","col","embed","hr","img","input","link","meta","param","source","track","wbr"];

    var p = new Parser({
        onopentag: function(tag, attrs) {
            var node = { tag: tag, attrs: attrs||{}, children: [], text: "" };
            stack[stack.length-1].children.push(node);
            if (voids.indexOf(tag) === -1) stack.push(node);
        },
        onclosetag: function(tag) {
            if (stack.length > 1 && stack[stack.length-1].tag === tag) stack.pop();
        },
        ontext: function(t) {
            if (stack.length > 0) stack[stack.length-1].text = (stack[stack.length-1].text||"") + t;
        }
    });
    p.write(html); p.end();

    function q(selector, root) {
        var results = [];
        function match(node) {
            if (!node || !node.tag) return;
            var sel = selector.trim();
            var byId = sel.match(/^#([\w-]+)$/);
            var byCls = sel.match(/^\.([\w-]+)$/);
            var byTag = sel.match(/^([\w]+)$/);
            var byAttr = sel.match(/^\[([^\]]+)\]$/);
            if (byId && node.attrs && node.attrs.id === byId[1]) results.push(node);
            else if (byCls && node.attrs && (node.attrs.class||"").split(/\s+/).indexOf(byCls[1]) !== -1) results.push(node);
            else if (byTag && node.tag === byTag[1].toLowerCase()) results.push(node);
            else if (byAttr && node.attrs && node.attrs[byAttr[1]] !== undefined) results.push(node);
            (node.children||[]).forEach(match);
        }
        (root ? [root] : nodes).forEach(match);
        return results;
    }

    function wrap(nodeList) {
        return {
            _nodes: nodeList,
            text: function() { return nodeList.map(function(n){ return n.text||""; }).join(""); },
            html: function() { return nodeList.map(function(n){ return n.children.map(function(c){ return c.text||""; }).join(""); }).join(""); },
            attr: function(a) { return nodeList[0] && nodeList[0].attrs ? nodeList[0].attrs[a] : null; },
            first: function() { return wrap(nodeList.slice(0,1)); },
            last: function() { return wrap(nodeList.slice(-1)); },
            eq: function(i) { return wrap(nodeList.slice(i, i+1)); },
            each: function(fn) { nodeList.forEach(function(n,i){ fn(i, n); }); return this; },
            find: function(sel) { var r=[]; nodeList.forEach(function(n){ q(sel,n).forEach(function(x){ r.push(x); }); }); return wrap(r); },
            length: nodeList.length,
            get: function(i) { return nodeList[i]; }
        };
    }

    var $ = function(sel) { return wrap(q(sel)); };
    $.html = function() { return html; };
    $.root = function() { return wrap([{ tag:"root", children: nodes, attrs:{}, text:"" }]); };
    return $;
}
""".trimIndent()
}
