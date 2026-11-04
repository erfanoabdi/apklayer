// A JSON reader that builds nothing: values are views into the text, found by key or index (releases.json, F-Droid's 70 MB index-v1.json).
// A malformed or missing value is an empty view.
#pragma once

#include <string>
#include <string_view>
#include <utility>
#include <vector>

namespace manager::json {

// the value of key in the object obj
std::string_view member(std::string_view obj, std::string_view key);
// every key and value of an object, every element of an array
std::vector<std::pair<std::string, std::string_view>> members(std::string_view obj);
std::vector<std::string_view> elements(std::string_view arr);
// a string value unescaped ("" when v is no string), a number's value (0 when v is no number)
std::string str(std::string_view v);
long long num(std::string_view v);

}  // namespace manager::json
