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
    std::atomic<bool> yield{false};   // reading ahead gives way to a question, keeping what it has read
    int last_read = 0;                // how many tokens the last call read (the rest it already held)
    int n_ctx = 0;
    std::string error;
};

// Once, before anything else. [backend_dir]: where the CPU backends are (the app's native library folder), or
// empty when they're linked in.
void init(const std::string & backend_dir);

// How many bytes of the model at [path] copying its weights into a faster order would copy: the quantised tables that
// are multiplied through. -1 if the file can't be read.
int64_t copied_size(const std::string & path);

// Loads the model at [path]; null (and [error] says why) if it can't be. [copy_weights]: its weights are copied into an
// order that's faster to work through, which holds them in memory a second time (only where there's room for that).
Engine * load(const std::string & path, int n_ctx, int n_threads, bool copy_weights, std::string & error);
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

// The text that stands for the question still to come, as the content of the last message given to [prefill].
extern const char * const ASK_MARK;

// Reads ahead: everything in [messages] before the last one, whose content is ASK_MARK, as [generate] will read it
// with a question in that message's place, so then only the question is left to read. Gives way between batches
// when [Engine::yield] is set (what it has read stays read), and stops like [generate] when [Engine::stop] is.
Result prefill(Engine * e, const std::vector<Message> & messages);

}  // namespace kl
