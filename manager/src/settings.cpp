#include "settings.h"

namespace manager {

namespace {
std::string field(const std::vector<std::string>& row, size_t i) { return i < row.size() ? row[i] : ""; }
}  // namespace

std::shared_ptr<slint::VectorModel<ConfigEntry>> configModel(const Rows& rows) {
    auto model = std::make_shared<slint::VectorModel<ConfigEntry>>();
    for (const auto& r : rows) {
        ConfigEntry e{};
        e.key = slint::SharedString(field(r, 0));
        e.kind = slint::SharedString(field(r, 1));
        e.default_value = slint::SharedString(field(r, 2));
        e.value = slint::SharedString(field(r, 3));
        auto choices = std::make_shared<slint::VectorModel<slint::SharedString>>();
        for (size_t start = 0, end; !field(r, 4).empty() && start <= field(r, 4).size(); start = end + 1) {
            end = field(r, 4).find(',', start);
            if (end == std::string::npos) end = field(r, 4).size();
            choices->push_back(slint::SharedString(field(r, 4).substr(start, end - start)));
        }
        e.choices = choices;
        e.title = slint::SharedString(field(r, 5));
        e.help = slint::SharedString(field(r, 6));
        e.own = field(r, 8) == "1";
        model->push_back(e);
    }
    return model;
}

std::vector<std::string> configArgs(const std::string& package, const std::string& key, const std::string& value) {
    std::vector<std::string> args = {"config", value.empty() ? "unset" : "set", key};
    if (!value.empty()) args.push_back(value);
    if (!package.empty()) { args.push_back("--app"); args.push_back(package); }
    return args;
}

}  // namespace manager
