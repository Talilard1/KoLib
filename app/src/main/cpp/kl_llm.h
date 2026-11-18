// KoLib's on-device language model engine: a thin layer over llama.cpp (MIT).
// One model at a time, one conversation at a time; text in, text out.
#pragma once

#include <atomic>
#include <functional>
#include <string>
#include <vector>

struct llama_model;
struct llama_context;
struct common_chat_templates;

namespace kl {

struct Message {
    std::string role;     // "system", "user" or "assistant"
    std::string content;
};

// How a reply ended.
enum Result {
    DONE = 0,          // the model finished what it had to say
    LENGTH = 1,        // it reached the most tokens asked for
    CANCELLED = 2,     // stopped from outside
    TOO_LONG = 3,      // what was asked doesn't fit in the model's context
    FAILED = -1,       // see Engine::error
};

struct Engine {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    common_chat_templates * templates = nullptr;
    std::vector<int32_t> kept;        // the tokens the context holds now, so a shared start isn't read twice
    std::atomic<bool> stop{false};
    int n_ctx = 0;
    std::string error;
};

// Once, before anything else. [backend_dir]: where the CPU backends are (the app's native library folder), or
// empty when they're linked in.
void init(const std::string & backend_dir);

// Loads the model at [path]; null (and [error] says why) if it can't be.
Engine * load(const std::string & path, int n_ctx, int n_threads, std::string & error);
void unload(Engine * e);

// How many tokens [text] is for this model.
int count_tokens(Engine * e, const std::string & text);

// Something about the model: its name and how much it can hold.
std::string describe(Engine * e);

// Answers [messages]. [on_progress] hears how much of the prompt has been read (0..1); [on_text] gets the reply
// a piece at a time (whole UTF-8 characters). Either returns false to stop.
Result generate(Engine * e, const std::vector<Message> & messages, int max_tokens, float temperature,
                const std::function<bool(float)> & on_progress,
                const std::function<bool(const std::string &)> & on_text);

}  // namespace kl
