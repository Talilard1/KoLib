#include "kl_llm.h"

#include <algorithm>
#include <exception>

#include "chat.h"
#include "common.h"
#include "llama.h"
#include "sampling.h"

namespace kl {

namespace {

constexpr int BATCH = 256;
// Room kept after the prompt for at least a short answer.
constexpr int LEAST_ANSWER = 48;

void quiet_log(enum ggml_log_level level, const char * text, void *) {
#ifdef KL_LOG_STDERR
    if (level >= GGML_LOG_LEVEL_WARN) fputs(text, stderr);
#else
    (void) level;
    (void) text;
#endif
}

// How many bytes at the start of [s] are whole UTF-8 characters.
size_t whole_utf8(const std::string & s) {
    size_t i = 0;
    const size_t n = s.size();
    while (i < n) {
        const unsigned char c = (unsigned char) s[i];
        size_t len = c < 0x80 ? 1 : (c >> 5) == 0x6 ? 2 : (c >> 4) == 0xE ? 3 : (c >> 3) == 0x1E ? 4 : 1;
        if (i + len > n) break;
        i += len;
    }
    return i;
}

bool aborted(void * data) { return ((Engine *) data)->stop.load(); }

}  // namespace

void init(const std::string & backend_dir) {
    llama_log_set(quiet_log, nullptr);
    if (!backend_dir.empty()) ggml_backend_load_all_from_path(backend_dir.c_str());
    llama_backend_init();
}

Engine * load(const std::string & path, int n_ctx, int n_threads, std::string & error) {
    auto * e = new Engine();
    try {
        llama_model_params mp = llama_model_default_params();
        mp.n_gpu_layers = 0;
        mp.load_mode = LLAMA_LOAD_MODE_MMAP;
        mp.lazy_mode = LLAMA_LAZY_MODE_ON;
        e->model = llama_model_load_from_file(path.c_str(), mp);
        if (!e->model) {
            error = "model";
            delete e;
            return nullptr;
        }
        llama_context_params cp = llama_context_default_params();
        const int trained = llama_model_n_ctx_train(e->model);
        cp.n_ctx = trained > 0 ? std::min(n_ctx, trained) : n_ctx;
        cp.n_batch = BATCH;
        cp.n_ubatch = BATCH;
        cp.n_threads = n_threads;
        cp.n_threads_batch = n_threads;
        cp.no_perf = true;
        cp.abort_callback = aborted;
        cp.abort_callback_data = e;
        e->ctx = llama_init_from_model(e->model, cp);
        if (!e->ctx) {
            error = "memory";
            llama_model_free(e->model);
            delete e;
            return nullptr;
        }
        e->n_ctx = (int) llama_n_ctx(e->ctx);
        e->templates = common_chat_templates_init(e->model, "").release();
        return e;
    } catch (const std::exception & ex) {
        error = ex.what();
        if (e->ctx) llama_free(e->ctx);
        if (e->model) llama_model_free(e->model);
        delete e;
        return nullptr;
    }
}

void unload(Engine * e) {
    if (!e) return;
    if (e->templates) common_chat_templates_free(e->templates);
    if (e->ctx) llama_free(e->ctx);
    if (e->model) llama_model_free(e->model);
    delete e;
}

int count_tokens(Engine * e, const std::string & text) {
    if (!e || !e->model) return 0;
    return (int) common_tokenize(llama_model_get_vocab(e->model), text, false, false).size();
}

std::string describe(Engine * e) {
    if (!e || !e->model) return "";
    char buf[256];
    buf[0] = 0;
    llama_model_desc(e->model, buf, sizeof(buf));
    return std::string(buf) + "\n" + std::to_string(e->n_ctx);
}

Result generate(Engine * e, const std::vector<Message> & messages, int max_tokens, float temperature,
                const std::function<bool(float)> & on_progress,
                const std::function<bool(const std::string &)> & on_text) {
    if (!e || !e->ctx) return FAILED;
    e->stop.store(false);
    e->error.clear();
    common_sampler * sampler = nullptr;
    try {
        const llama_vocab * vocab = llama_model_get_vocab(e->model);

        // The conversation as this model wants it written.
        common_chat_templates_inputs in;
        for (const auto & m : messages) {
            common_chat_msg msg;
            msg.role = m.role;
            msg.content = m.content;
            in.messages.push_back(msg);
        }
        in.add_generation_prompt = true;
        in.use_jinja = true;
        in.enable_thinking = false;
        common_chat_params chat = common_chat_templates_apply(e->templates, in);

        // What the reader wrote or the book says is never read as the model's own marks.
        std::vector<llama_token> tokens;
        bool first = true;
        for (const auto & part : chat.prompt.parts) {
            if (part.text.empty()) continue;
            auto piece = common_tokenize(vocab, part.text, first, !part.is_input);
            tokens.insert(tokens.end(), piece.begin(), piece.end());
            first = false;
        }
        if (tokens.empty()) {
            e->error = "empty";
            return FAILED;
        }
        if ((int) tokens.size() + LEAST_ANSWER > e->n_ctx) return TOO_LONG;

        // What's the same as last time stays; the rest is read.
        size_t same = 0;
        while (same < e->kept.size() && same < tokens.size() && e->kept[same] == tokens[same]) same++;
        if (same == tokens.size()) same--;
        llama_memory_t mem = llama_get_memory(e->ctx);
        if (same < e->kept.size()) {
            if (same == 0 || !llama_memory_seq_rm(mem, 0, (llama_pos) same, -1)) {
                llama_memory_clear(mem, true);
                same = 0;
            }
        }
        e->kept.resize(same);

        const size_t to_read = tokens.size() - same;
        for (size_t i = same; i < tokens.size(); i += BATCH) {
            const int n = (int) std::min((size_t) BATCH, tokens.size() - i);
            if (llama_decode(e->ctx, llama_batch_get_one(tokens.data() + i, n)) != 0) {
                // Stopped part-way, or failed: what the context holds now isn't known for sure.
                llama_memory_clear(mem, true);
                e->kept.clear();
                if (e->stop.load()) return CANCELLED;
                e->error = "decode";
                return FAILED;
            }
            e->kept.insert(e->kept.end(), tokens.begin() + i, tokens.begin() + i + n);
            if (on_progress && !on_progress((float) (i + n - same) / (float) to_read)) return CANCELLED;
            if (e->stop.load()) return CANCELLED;
        }

        common_params_sampling sp;
        sp.temp = temperature;
        sp.top_k = 40;
        sp.top_p = 0.9f;
        sp.min_p = 0.05f;
        sp.penalty_repeat = 1.05f;
        sp.penalty_last_n = 128;
        sampler = common_sampler_init(e->model, sp);
        if (!sampler) {
            e->error = "sampler";
            return FAILED;
        }

        std::string pending;   // bytes of a character not whole yet
        std::string tail;      // the end of what's been said, to spot a stop string
        Result result = LENGTH;
        for (int n = 0; n < max_tokens; n++) {
            if (e->stop.load()) { result = CANCELLED; break; }
            llama_token t = common_sampler_sample(sampler, e->ctx, -1);
            common_sampler_accept(sampler, t, true);
            if (llama_vocab_is_eog(vocab, t)) { result = DONE; break; }
            pending += common_token_to_piece(vocab, t, false);
            const size_t whole = whole_utf8(pending);
            if (whole > 0) {
                const std::string out = pending.substr(0, whole);
                pending.erase(0, whole);
                tail += out;
                bool stopped = false;
                for (const auto & s : chat.additional_stops) {
                    if (!s.empty() && tail.size() >= s.size() && tail.compare(tail.size() - s.size(), s.size(), s) == 0) stopped = true;
                }
                if (stopped) { result = DONE; break; }
                if (tail.size() > 256) tail.erase(0, tail.size() - 128);
                if (on_text && !on_text(out)) { result = CANCELLED; break; }
            }
            if ((int) e->kept.size() + 1 >= e->n_ctx) { result = LENGTH; break; }
            if (llama_decode(e->ctx, llama_batch_get_one(&t, 1)) != 0) {
                llama_memory_clear(mem, true);
                e->kept.clear();
                result = e->stop.load() ? CANCELLED : FAILED;
                if (result == FAILED) e->error = "decode";
                break;
            }
            e->kept.push_back(t);
        }
        common_sampler_free(sampler);
        return result;
    } catch (const std::exception & ex) {
        if (sampler) common_sampler_free(sampler);
        e->error = ex.what();
        e->kept.clear();
        if (e->ctx) llama_memory_clear(llama_get_memory(e->ctx), true);
        return FAILED;
    }
}

}  // namespace kl
