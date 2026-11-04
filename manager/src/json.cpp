#include "json.h"

#include <cstdlib>

namespace manager::json {

namespace {
constexpr size_t npos = std::string_view::npos;

size_t space(std::string_view s, size_t i) {
    while (i < s.size() && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++;
    return i;
}

// the end of the value starting at i (no leading space), npos when it is malformed
size_t skip(std::string_view s, size_t i) {
    if (i >= s.size()) return npos;
    if (s[i] == '"') {
        for (i++; i < s.size(); i++) {
            if (s[i] == '\\') i++;
            else if (s[i] == '"') return i + 1;
        }
        return npos;
    }
    if (s[i] == '{' || s[i] == '[') {
        char close = s[i] == '{' ? '}' : ']';
        i = space(s, i + 1);
        if (i < s.size() && s[i] == close) return i + 1;
        for (;;) {
            if (close == '}') {  // "key" : value
                if (i >= s.size() || s[i] != '"' || (i = skip(s, i)) == npos) return npos;
                i = space(s, i);
                if (i >= s.size() || s[i] != ':') return npos;
                i = space(s, i + 1);
            }
            if ((i = skip(s, i)) == npos) return npos;
            i = space(s, i);
            if (i < s.size() && s[i] == close) return i + 1;
            if (i >= s.size() || s[i] != ',') return npos;
            i = space(s, i + 1);
        }
    }
    size_t start = i;  // number, true, false, null
    while (i < s.size() && s[i] != ',' && s[i] != '}' && s[i] != ']' && s[i] != ' ' && s[i] != '\n' && s[i] != '\r' && s[i] != '\t') i++;
    return i > start ? i : npos;
}

// calls f(key or "", value) for each member or element; false when the container is malformed
template <class F> bool each(std::string_view s, char open, F f) {
    size_t i = space(s, 0);
    if (i >= s.size() || s[i] != open || skip(s, i) == npos) return false;
    char close = open == '{' ? '}' : ']';
    i = space(s, i + 1);
    while (i < s.size() && s[i] != close) {
        std::string key;
        if (open == '{') {
            size_t end = skip(s, i);
            key = str(s.substr(i, end - i));
            i = space(s, space(s, end) + 1);
        }
        size_t end = skip(s, i);
        if (f(key, s.substr(i, end - i))) return true;
        i = space(s, space(s, end) + 1);  // past the ','
    }
    return true;
}

void utf8(std::string& out, unsigned c) {
    if (c < 0x80) out += static_cast<char>(c);
    else if (c < 0x800) { out += static_cast<char>(0xc0 | c >> 6); out += static_cast<char>(0x80 | (c & 0x3f)); }
    else if (c < 0x10000) { out += static_cast<char>(0xe0 | c >> 12); out += static_cast<char>(0x80 | (c >> 6 & 0x3f)); out += static_cast<char>(0x80 | (c & 0x3f)); }
    else { out += static_cast<char>(0xf0 | c >> 18); out += static_cast<char>(0x80 | (c >> 12 & 0x3f)); out += static_cast<char>(0x80 | (c >> 6 & 0x3f)); out += static_cast<char>(0x80 | (c & 0x3f)); }
}
}  // namespace

std::string_view member(std::string_view obj, std::string_view key) {
    std::string_view found;
    each(obj, '{', [&](const std::string& k, std::string_view v) { if (k == key) { found = v; return true; } return false; });
    return found;
}

std::vector<std::pair<std::string, std::string_view>> members(std::string_view obj) {
    std::vector<std::pair<std::string, std::string_view>> out;
    each(obj, '{', [&](const std::string& k, std::string_view v) { out.emplace_back(k, v); return false; });
    return out;
}

std::vector<std::string_view> elements(std::string_view arr) {
    std::vector<std::string_view> out;
    each(arr, '[', [&](const std::string&, std::string_view v) { out.push_back(v); return false; });
    return out;
}

std::string str(std::string_view v) {
    std::string out;
    if (v.size() < 2 || v.front() != '"' || v.back() != '"') return out;
    for (size_t i = 1; i + 1 < v.size(); i++) {
        if (v[i] != '\\') { out += v[i]; continue; }
        char e = v[++i];
        if (e == 'n') out += '\n';
        else if (e == 't') out += '\t';
        else if (e == 'r') out += '\r';
        else if (e == 'b') out += '\b';
        else if (e == 'f') out += '\f';
        else if (e == 'u' && i + 4 < v.size()) {
            unsigned c = std::strtoul(std::string(v.substr(i + 1, 4)).c_str(), nullptr, 16);
            i += 4;
            if (c >= 0xd800 && c < 0xdc00 && i + 6 < v.size() && v[i + 1] == '\\' && v[i + 2] == 'u') {  // a surrogate pair
                unsigned low = std::strtoul(std::string(v.substr(i + 3, 4)).c_str(), nullptr, 16);
                c = 0x10000 + ((c - 0xd800) << 10) + (low - 0xdc00);
                i += 6;
            }
            utf8(out, c);
        } else out += e;  // \" \\ \/
    }
    return out;
}

long long num(std::string_view v) { return v.empty() || v[0] == '"' ? 0 : std::atoll(std::string(v).c_str()); }

}  // namespace manager::json
